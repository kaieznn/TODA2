#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <WiFiClientSecure.h>
#include <time.h>
#include <Wire.h>
#include <SPI.h>
#include <Adafruit_PN532.h>

// WiFi credentials
#define WIFI_SSID "George"
#define WIFI_PASSWORD "Aixroch!092601"

// Firebase credentials
#define API_KEY "AIzaSyA9ADVig4CiO2Y3ELl3unzXajdzxCgRxHI"
#define DATABASE_URL "https://toda-contribution-system-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define USER_EMAIL "test@example.com"
#define USER_PASSWORD "test123456"

// PN532 SPI configuration
#define PN532_SCK  18
#define PN532_MOSI 23
#define PN532_SS   5
#define PN532_MISO 19

// Serial communication
#define COIN_RX 16
#define COIN_TX 17

// Button pins
#define REGULAR_BUTTON_PIN 21
#define SPECIAL_BUTTON_PIN 22

// Create instances
Adafruit_PN532 nfc(PN532_SCK, PN532_MISO, PN532_MOSI, PN532_SS);

// Firebase objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// System state
bool coinSlotEnabled = false;
bool nfcEnabled = true;
unsigned long lastNFCCheck = 0;
const unsigned long NFC_CHECK_INTERVAL = 100;
unsigned long totalSavings = 0;

// Add RFID debounce variables
String lastScannedUID = "";
unsigned long lastScanTime = 0;
const unsigned long SCAN_DEBOUNCE_TIME = 3000; // 3 seconds between same card scans
bool isProcessingRFID = false;

// Add Firebase timeout variables
unsigned long firebaseTimeout = 10000; // 10 seconds timeout

// Button state variables
bool regularButtonPressed = false;
bool specialButtonPressed = false;
unsigned long regularButtonLastPress = 0;
unsigned long specialButtonLastPress = 0;
const unsigned long BUTTON_DEBOUNCE_TIME = 500; // 500ms debounce

// Driver information structure
struct Driver {
  String rfidUID;
  String todaNumber;
  String driverName;
  bool isRegistered;
};

// Global variable to store current driver info
Driver currentDriver;

// Queue management
String firstDriverQueueKey = "";

// Function forward declarations
void checkButtons();
void handleRegularPassenger();
void handleSpecialPassenger();
bool getFirstDriverInQueue();
void removeDriverFromQueue();
void checkQueueStatus();
void checkForRFIDCard();
void checkDriverRegistration(String rfidUID);
void enableCoinSlot(Driver driver);
void processCoinContribution();
void processContributionAndQueue();
void recordContribution();
void addDriverToQueue();
void showError(String message);
String getCurrentDate();
String getCurrentTime();

void setup() {
  Serial.begin(115200);
  Serial2.begin(9600, SERIAL_8N1, COIN_RX, COIN_TX);
  
  // Initialize button pins
  pinMode(REGULAR_BUTTON_PIN, INPUT_PULLUP);
  pinMode(SPECIAL_BUTTON_PIN, INPUT_PULLUP);
  
  Serial.println("ESP32 ready: TODA Contribution & Queueing System");
  Serial.println("Serial2 initialized: RX=16, TX=17");
  Serial.println("Buttons initialized: Regular=Pin21, Special=Pin22");
  
  // Initialize WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Connected with IP: ");
  Serial.println(WiFi.localIP());
  
  // Configure time (needed for Firebase)
  configTime(8 * 3600, 0, "pool.ntp.org", "time.nist.gov");  // GMT+8 Philippines
  
  // Wait for time to be set
  Serial.print("Waiting for NTP time sync");
  time_t now = time(nullptr);
  while (now < 8 * 3600 * 2) {
    delay(500);
    Serial.print(".");
    now = time(nullptr);
  }
  Serial.println();
  Serial.println("Time synchronized");
  
  // Initialize Firebase
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  
  // Use email/password authentication
  Serial.println("Initializing Firebase with email authentication...");
  
  // Set user credentials
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;
  
  // Assign the callback function for the long running token generation task
  config.token_status_callback = tokenStatusCallback;
  
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  
  // Set timeout
  fbdo.setResponseSize(4096);
  
  Serial.println("Firebase configuration complete");
  
  // Wait for Firebase authentication
  Serial.print("Waiting for Firebase authentication");
  unsigned long startTime = millis();
  while (!Firebase.ready() && (millis() - startTime < 30000)) {
    delay(1000);
    Serial.print(".");
  }
  
  if (Firebase.ready()) {
    Serial.println();
    Serial.println("✓ Firebase authenticated and ready!");
    
    // Test database connection
    if (Firebase.RTDB.get(&fbdo, "/drivers")) {
      Serial.println("✓ Database connection successful!");
    } else {
      Serial.println("Database test result: " + fbdo.errorReason());
    }
  } else {
    Serial.println();
    Serial.println("✗ Firebase authentication failed");
  }
  
  // Initialize PN532
  nfc.begin();
  uint32_t versiondata = nfc.getFirmwareVersion();
  if (!versiondata) {
    Serial.println("PN532 not found");
  } else {
    Serial.print("Found PN532 with firmware version: ");
    Serial.println((versiondata >> 16) & 0xFF, HEX);
    nfc.SAMConfig();
    Serial.println("PN532 ready, waiting for driver RFID...");
  }
  
  // Check if there are drivers in queue on startup and set currentDriver
  Serial.println("Checking for drivers in queue on startup...");
  if (getFirstDriverInQueue()) {
    Serial.println("✓ Found driver in queue: " + currentDriver.driverName + " (TODA #" + currentDriver.todaNumber + ")");
    Serial.println("✓ Buttons are now ready to assign trips!");
  } else {
    Serial.println("No drivers available in queue. Scan RFID to add driver or wait for drivers to join queue.");
  }
  
  Serial.println("System ready: Please scan driver RFID card to start contribution process");
}

void loop() {
  // Check for coin data from Arduino
  if (Serial2.available()) {
    uint8_t receivedByte = Serial2.read();
    Serial.println("Received byte from Arduino: " + String(receivedByte));
    
    if (receivedByte == 5 && coinSlotEnabled) {
      Serial.println("Valid coin detected and coin slot is enabled!");
      processCoinContribution();
    } else if (receivedByte == 5 && !coinSlotEnabled) {
      Serial.println("Coin detected but coin slot is disabled - ignoring");
    } else {
      Serial.println("Unexpected byte received: " + String(receivedByte));
    }
  }
  
  // Check button presses
  checkButtons();
  
  // Check for NFC cards (only when enabled)
  if (nfcEnabled && (millis() - lastNFCCheck >= NFC_CHECK_INTERVAL)) {
    lastNFCCheck = millis();
    checkForRFIDCard();
  }
  
  // Debug system status every 10 seconds
  static unsigned long lastSystemStatus = 0;
  if (millis() - lastSystemStatus > 10000) {
    Serial.println("System Status - NFC: " + String(nfcEnabled ? "ON" : "OFF") + 
                  ", CoinSlot: " + String(coinSlotEnabled ? "ON" : "OFF") + 
                  ", ProcessingRFID: " + String(isProcessingRFID ? "YES" : "NO"));
    lastSystemStatus = millis();
  }
  
  // Check for manual commands via Serial Monitor (for debugging)
  if (Serial.available()) {
    String command = Serial.readString();
    command.trim();
    command.toLowerCase();
    
    if (command == "enable") {
      Serial.println("Manually enabling coin slot...");
      Driver testDriver;
      testDriver.rfidUID = "MANUAL";
      testDriver.driverName = "Manual Test";
      testDriver.todaNumber = "TEST";
      testDriver.isRegistered = true;
      enableCoinSlot(testDriver);
    } else if (command == "disable") {
      Serial.println("Manually disabling coin slot...");
      coinSlotEnabled = false;
      nfcEnabled = true;
      Serial2.write((uint8_t)201);
      Serial2.flush();
      Serial.println("Coin slot disabled, NFC re-enabled");
    } else if (command == "status") {
      Serial.println("\n=== SYSTEM STATUS ===");
      Serial.println("NFC Enabled: " + String(nfcEnabled ? "true" : "false"));
      Serial.println("Coin Slot Enabled: " + String(coinSlotEnabled ? "true" : "false"));
      Serial.println("Processing RFID: " + String(isProcessingRFID ? "true" : "false"));
      Serial.println("WiFi Status: " + String(WiFi.status() == WL_CONNECTED ? "Connected" : "Disconnected"));
      Serial.println("Firebase Ready: " + String(Firebase.ready() ? "true" : "false"));
      Serial.println("==================");
    } else if (command == "test200") {
      Serial.println("Sending command 200 to Arduino...");
      Serial2.write((uint8_t)200);
      Serial2.flush();
      Serial.println("Command 200 sent!");
    } else if (command == "test201") {
      Serial.println("Sending command 201 to Arduino...");
      Serial2.write((uint8_t)201);
      Serial2.flush();
      Serial.println("Command 201 sent!");
    } else if (command == "regular") {
      Serial.println("Manual REGULAR passenger selection test...");
      handleRegularPassenger();
    } else if (command == "special") {
      Serial.println("Manual SPECIAL passenger selection test...");
      handleSpecialPassenger();
    } else if (command == "queue") {
      Serial.println("Checking driver queue status...");
      checkQueueStatus();
    } else if (command == "testqueue") {
      Serial.println("Testing queue parsing...");
      testQueueParsing();
    }
  }
}

void checkQueueStatus() {
  Serial.println("\n=== DRIVER QUEUE STATUS ===");
  
  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();
      
      if (jsonStr == "null" || jsonStr.length() == 0) {
        Serial.println("Queue is empty");
      } else {
        Serial.println("Queue data:");
        
        // Parse and display queue entries with readable timestamps
        int driverCount = 0;
        int pos = 0;
        while ((pos = jsonStr.indexOf("\"driverName\"", pos)) != -1) {
          driverCount++;
          
          // Find the driver name
          int nameStart = jsonStr.indexOf("\"", pos + 12) + 1;
          int nameEnd = jsonStr.indexOf("\"", nameStart);
          String driverName = jsonStr.substring(nameStart, nameEnd);
          
          // Find the timestamp (look backwards for the key)
          int keyEnd = jsonStr.lastIndexOf("\":", pos);
          int keyStart = jsonStr.lastIndexOf("\"", keyEnd - 1) + 1;
          String queueKey = jsonStr.substring(keyStart, keyEnd);
          
          // Find the readable timestamp
          int timestampPos = jsonStr.indexOf("\"timestamp\":", pos);
          String readableTime = "";
          if (timestampPos != -1 && timestampPos < jsonStr.indexOf("\"driverName\"", pos + 1)) {
            int timeStart = jsonStr.indexOf("\"", timestampPos + 12) + 1;
            int timeEnd = jsonStr.indexOf("\"", timeStart);
            readableTime = jsonStr.substring(timeStart, timeEnd);
          }
          
          Serial.println("Driver #" + String(driverCount) + ": " + driverName + 
                        " (Queue: " + queueKey + ", Time: " + readableTime + ")");
          
          pos++;
        }
        Serial.println("Total drivers in queue: " + String(driverCount));
      }
    } else {
      Serial.println("Queue data type: " + fbdo.dataType());
    }
  } else {
    // Firebase removes empty nodes, so "path not exist" means no drivers in queue
    if (fbdo.errorReason().indexOf("path not exist") != -1 || 
        fbdo.errorReason().indexOf("not found") != -1 ||
        fbdo.httpCode() == 404) {
      Serial.println("No drivers available in queue");
    } else {
      Serial.println("✗ Failed to get queue: " + fbdo.errorReason());
    }
  }
  
  Serial.println("===========================\n");
}

void testQueueParsing() {
  Serial.println("Testing getFirstDriverInQueue() function...");
  
  if (getFirstDriverInQueue()) {
    Serial.println("SUCCESS: Found driver - " + currentDriver.driverName);
  } else {
    Serial.println("FAILED: Could not find driver in queue");
  }
}

void checkButtons() {
  // INDEPENDENT BUTTON SYSTEM - No dependency on coin slot, NFC, or global state
  // Only interacts with Firebase /queue and /activeTrips tables
  
  // Read button states
  int regularState = digitalRead(REGULAR_BUTTON_PIN);
  int specialState = digitalRead(SPECIAL_BUTTON_PIN);
  
  // Check regular button (active LOW with pullup)
  if (regularState == LOW) {
    if (!regularButtonPressed && (millis() - regularButtonLastPress > BUTTON_DEBOUNCE_TIME)) {
      regularButtonPressed = true;
      regularButtonLastPress = millis();
      Serial.println("REGULAR BUTTON PRESSED!");
      handleRegularPassenger();
    }
  } else {
    regularButtonPressed = false;
  }
  
  // Check special button (active LOW with pullup)
  if (specialState == LOW) {
    if (!specialButtonPressed && (millis() - specialButtonLastPress > BUTTON_DEBOUNCE_TIME)) {
      specialButtonPressed = true;
      specialButtonLastPress = millis();
      Serial.println("SPECIAL BUTTON PRESSED!");
      handleSpecialPassenger();
    }
  } else {
    specialButtonPressed = false;
  }
}

void handleRegularPassenger() {
  Serial.println("\n=== DRIVER SELECTED: REGULAR PASSENGER ===");
  
  // Check if we have a current driver, if not try to get from queue
  // This handles ESP32 resets where currentDriver gets cleared
  bool hasDriver = false;
  
  // If currentDriver is empty or invalid, try to get from queue
  if (currentDriver.driverName.length() == 0 || currentDriver.rfidUID.length() == 0) {
    Serial.println("No current driver set, fetching from queue...");
    hasDriver = getFirstDriverInQueue();
  } else {
    Serial.println("Using current driver: " + currentDriver.driverName);
    hasDriver = true;
  }
  
  if (hasDriver) {
    String timestamp = String(time(nullptr));
    String tripPath = "/activeTrips/" + timestamp;
    
    // Create trip record
    FirebaseJson tripJson;
    tripJson.set("driverRFID", currentDriver.rfidUID);
    tripJson.set("driverName", currentDriver.driverName);
    tripJson.set("todaNumber", currentDriver.todaNumber);
    tripJson.set("passengerType", "regular");
    tripJson.set("tripStartTime", timestamp);
    tripJson.set("status", "in_travel");
    tripJson.set("timestamp", getCurrentDate() + " " + getCurrentTime());
    
    if (Firebase.RTDB.setJSON(&fbdo, tripPath, &tripJson)) {
      Serial.println("✓ Driver " + currentDriver.driverName + " is now transporting REGULAR passenger");
      Serial.println("Trip ID: " + timestamp);
      
      // Remove driver from queue
      removeDriverFromQueue();
      
    } else {
      Serial.println("✗ Failed to create trip record: " + fbdo.errorReason());
    }
  } else {
    Serial.println("✗ No driver in queue to assign trip!");
  }
  
  Serial.println("==========================================\n");
}

void handleSpecialPassenger() {
  Serial.println("\n=== DRIVER SELECTED: SPECIAL PASSENGER ===");
  
  // Check if we have a current driver, if not try to get from queue
  // This handles ESP32 resets where currentDriver gets cleared
  bool hasDriver = false;
  
  // If currentDriver is empty or invalid, try to get from queue
  if (currentDriver.driverName.length() == 0 || currentDriver.rfidUID.length() == 0) {
    Serial.println("No current driver set, fetching from queue...");
    hasDriver = getFirstDriverInQueue();
  } else {
    Serial.println("Using current driver: " + currentDriver.driverName);
    hasDriver = true;
  }
  
  if (hasDriver) {
    String timestamp = String(time(nullptr));
    String tripPath = "/activeTrips/" + timestamp;
    
    // Create trip record
    FirebaseJson tripJson;
    tripJson.set("driverRFID", currentDriver.rfidUID);
    tripJson.set("driverName", currentDriver.driverName);
    tripJson.set("todaNumber", currentDriver.todaNumber);
    tripJson.set("passengerType", "special");
    tripJson.set("tripStartTime", timestamp);
    tripJson.set("status", "in_travel");
    tripJson.set("timestamp", getCurrentDate() + " " + getCurrentTime());
    
    if (Firebase.RTDB.setJSON(&fbdo, tripPath, &tripJson)) {
      Serial.println("✓ Driver " + currentDriver.driverName + " is now transporting SPECIAL passenger");
      Serial.println("Trip ID: " + timestamp);
      
      // Remove driver from queue
      removeDriverFromQueue();
      
    } else {
      Serial.println("✗ Failed to create trip record: " + fbdo.errorReason());
    }
  } else {
    Serial.println("✗ No driver in queue to assign trip!");
  }
  
  Serial.println("===========================================\n");
}

bool getFirstDriverInQueue() {
  Serial.println("Getting first driver in queue...");
  
  // Get all drivers in queue
  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();
      
      if (jsonStr == "null" || jsonStr.length() == 0) {
        Serial.println("✗ No drivers in queue");
        return false;
      }
      
      Serial.println("Queue data: " + jsonStr);
      
      // Find the smallest timestamp (oldest entry) using pattern matching
      String smallestKey = "";
      unsigned long smallestTimestamp = 4000000000UL; // Use unsigned long with reasonable max value
      
      // Look for pattern: "1234567890":{
      // This identifies top-level timestamp keys
      int pos = 0;
      while (pos < jsonStr.length()) {
        int quoteStart = jsonStr.indexOf('"', pos);
        if (quoteStart == -1) break;
        
        int quoteEnd = jsonStr.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) break;
        
        // Check if this quote is followed by ":{"  (indicates top-level key)
        if (quoteEnd + 2 < jsonStr.length() && 
            jsonStr.charAt(quoteEnd + 1) == ':' && 
            jsonStr.charAt(quoteEnd + 2) == '{') {
          
          String key = jsonStr.substring(quoteStart + 1, quoteEnd);
          unsigned long timestamp = (unsigned long)key.toInt();
          
          Serial.println("Found top-level key: " + key + " (timestamp: " + String(timestamp) + ")");
          Serial.println("Validation check: " + String(timestamp) + " > 1000000000 = " + String(timestamp > 1000000000));
          Serial.println("Validation check: " + String(timestamp) + " < " + String(smallestTimestamp) + " = " + String(timestamp < smallestTimestamp));
          
          // Only consider keys that are valid timestamps (10-digit unix timestamps)
          if (timestamp > 1000000000UL && timestamp < smallestTimestamp) {
            smallestTimestamp = timestamp;
            smallestKey = key;
            Serial.println("✓ New smallest timestamp: " + key);
          } else {
            Serial.println("✗ Timestamp validation failed for: " + key);
          }
        }
        
        pos = quoteEnd + 1;
      }
      
      if (smallestKey.length() > 0) {
        firstDriverQueueKey = smallestKey;
        Serial.println("Oldest driver queue key: " + firstDriverQueueKey);
        
        // Get the driver details from this queue entry
        String queuePath = "/queue/" + firstDriverQueueKey;
        Serial.println("Fetching driver data from: " + queuePath);
        
        if (Firebase.RTDB.get(&fbdo, queuePath)) {
          if (fbdo.dataType() == "json") {
            String driverJson = fbdo.jsonString();
            Serial.println("Driver JSON: " + driverJson);
            
            // Parse driver info
            int nameStart = driverJson.indexOf("\"driverName\":\"") + 14;
            int nameEnd = driverJson.indexOf("\"", nameStart);
            String driverName = driverJson.substring(nameStart, nameEnd);
            
            int todaStart = driverJson.indexOf("\"todaNumber\":\"") + 14;
            int todaEnd = driverJson.indexOf("\"", todaStart);
            String todaNumber = driverJson.substring(todaStart, todaEnd);
            
            int rfidStart = driverJson.indexOf("\"driverRFID\":\"") + 14;
            int rfidEnd = driverJson.indexOf("\"", rfidStart);
            String rfidUID = driverJson.substring(rfidStart, rfidEnd);
            
            if (driverName.length() > 0 && todaNumber.length() > 0 && rfidUID.length() > 0) {
              // Update currentDriver with queue data
              currentDriver.driverName = driverName;
              currentDriver.todaNumber = todaNumber;
              currentDriver.rfidUID = rfidUID;
              currentDriver.isRegistered = true;
              
              Serial.println("✓ First driver in queue: " + driverName + " (TODA #" + todaNumber + ")");
              return true;
            } else {
              Serial.println("✗ Incomplete driver data parsed");
              Serial.println("Name: '" + driverName + "', TODA: '" + todaNumber + "', RFID: '" + rfidUID + "'");
            }
          } else {
            Serial.println("✗ Driver data is not JSON: " + fbdo.dataType());
          }
        } else {
          Serial.println("✗ Failed to get driver data: " + fbdo.errorReason());
        }
      } else {
        Serial.println("✗ No valid timestamp keys found in queue");
      }
    } else {
      Serial.println("✗ Queue data is not JSON: " + fbdo.dataType());
    }
  } else {
    // Firebase removes empty nodes, so "path not exist" means no drivers in queue
    if (fbdo.errorReason().indexOf("path not exist") != -1 || 
        fbdo.errorReason().indexOf("not found") != -1 ||
        fbdo.httpCode() == 404) {
      Serial.println("✗ No drivers available in queue");
    } else {
      Serial.println("✗ Failed to get queue: " + fbdo.errorReason());
    }
  }
  
  return false;
}

void removeDriverFromQueue() {
  if (firstDriverQueueKey.length() > 0) {
    Serial.println("Removing driver from queue: " + firstDriverQueueKey);
    
    if (Firebase.RTDB.deleteNode(&fbdo, "/queue/" + firstDriverQueueKey)) {
      Serial.println("✓ Driver removed from queue successfully");
      firstDriverQueueKey = ""; // Clear the key
    } else {
      Serial.println("✗ Failed to remove driver from queue: " + fbdo.errorReason());
    }
  }
}

void checkForRFIDCard() {
  // Don't check for RFID if we're already processing one
  if (isProcessingRFID) {
    return;
  }
  
  uint8_t uid[7];
  uint8_t uidLength;
  
  if (nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength)) {
    // Convert UID to string
    String uidString = "";
    for (uint8_t i = 0; i < uidLength; i++) {
      if (uid[i] < 0x10) uidString += "0";
      uidString += String(uid[i], HEX);
    }
    uidString.toUpperCase();
    
    // Check for debounce - prevent same card from being processed too quickly
    if (uidString == lastScannedUID && (millis() - lastScanTime < SCAN_DEBOUNCE_TIME)) {
      return; // Ignore duplicate scan within debounce time
    }
    
    Serial.print("Driver RFID scanned: ");
    Serial.println(uidString);
    
    // Update debounce variables
    lastScannedUID = uidString;
    lastScanTime = millis();
    isProcessingRFID = true;
    
    // Check driver registration in database
    checkDriverRegistration(uidString);
  }
}

void checkDriverRegistration(String rfidUID) {
  Serial.println("Checking driver registration in database...");
  Serial.println("Searching for RFID: " + rfidUID);

  // Clear any previous data
  fbdo.clear();
  
  // Search all drivers to find one with matching rfidUID field (new structure only)
  if (Firebase.RTDB.get(&fbdo, "/drivers")) {
    if (fbdo.dataType() == "json") {
      String allDriversJson = fbdo.jsonString();
      Serial.println("Got drivers data, searching for matching RFID...");
      Serial.println("Drivers JSON length: " + String(allDriversJson.length()));

      // Look for the RFID UID in the JSON using new structure pattern
      String searchPattern = "\"rfidUID\":\"" + rfidUID + "\"";
      int rfidPos = allDriversJson.indexOf(searchPattern);

      if (rfidPos != -1) {
        Serial.println("Found driver with matching RFID UID at position: " + String(rfidPos));

        // Find the driver key by searching backwards for the opening brace
        int driverStart = allDriversJson.lastIndexOf("{", rfidPos);
        int keyEnd = allDriversJson.lastIndexOf("\"", driverStart - 2);
        int keyStart = allDriversJson.lastIndexOf("\"", keyEnd - 1) + 1;
        String driverKey = allDriversJson.substring(keyStart, keyEnd);

        Serial.println("Driver key: " + driverKey);

        // Extract driver data from the JSON
        int driverEnd = allDriversJson.indexOf("}", rfidPos);
        String driverData = allDriversJson.substring(driverStart, driverEnd + 1);
        Serial.println("Driver data: " + driverData);

        // Parse driver name and TODA number
        int nameStart = driverData.indexOf("\"driverName\":\"") + 14;
        int nameEnd = driverData.indexOf("\"", nameStart);
        String driverName = driverData.substring(nameStart, nameEnd);

        int todaStart = driverData.indexOf("\"todaNumber\":\"") + 14;
        int todaEnd = driverData.indexOf("\"", todaStart);
        String todaNumber = driverData.substring(todaStart, todaEnd);

        Serial.println("Parsed driver name: '" + driverName + "'");
        Serial.println("Parsed TODA number: '" + todaNumber + "'");

        if (driverName.length() > 0 && todaNumber.length() > 0) {
          Driver driver;
          driver.rfidUID = rfidUID;
          driver.driverName = driverName;
          driver.todaNumber = todaNumber; // Use the ACTUAL toda number from database
          driver.isRegistered = true;

          Serial.println("✓ Driver found and verified:");
          Serial.println("  Key: " + driverKey);
          Serial.println("  Name: " + driver.driverName);
          Serial.println("  TODA Number: " + driver.todaNumber);

          // Enable coin slot for contribution
          enableCoinSlot(driver);
        } else {
          Serial.println("✗ Driver data incomplete");
          Serial.println("Missing name or TODA number in driver data");
          showError("Driver registration incomplete. Please contact TODA admin.");
        }
      } else {
        Serial.println("✗ Driver not found in database (RFID: " + rfidUID + ")");
        Serial.println("Available drivers in database:");

        // Show all available RFIDs for debugging
        int pos = 0;
        int count = 0;
        while ((pos = allDriversJson.indexOf("\"rfidUID\":", pos)) != -1) {
          count++;
          int valueStart = allDriversJson.indexOf("\"", pos + 10) + 1;
          int valueEnd = allDriversJson.indexOf("\"", valueStart);
          String foundRFID = allDriversJson.substring(valueStart, valueEnd);
          Serial.println("  RFID #" + String(count) + ": " + foundRFID);
          pos = valueEnd;
        }

        showError("RFID not registered. Please register this driver first.");
      }
    } else {
      Serial.println("✗ Drivers data is not JSON format: " + fbdo.dataType());
      showError("Database error. Please try again.");
    }
  } else {
    Serial.println("✗ Failed to get drivers data: " + fbdo.errorReason());
    Serial.println("HTTP Code: " + String(fbdo.httpCode()));

    // Check if it's a connection issue vs no data
    if (fbdo.httpCode() == -103 || fbdo.httpCode() == 0) {
      showError("Firebase connection error. Check WiFi and try again.");
    } else if (fbdo.httpCode() == 404) {
      Serial.println("No drivers table found in database");
      showError("No drivers registered in system. Please register drivers first.");
    } else {
      showError("Database connection error. Please try again.");
    }
  }
  
  // Reset processing flag after a delay to allow user to see messages
  delay(2000);
  isProcessingRFID = false;
}


void enableCoinSlot(Driver driver) {
  Serial.println("\n=== ENABLING COIN SLOT ===");
  
  // FIRST: Check if driver is already in queue before enabling coin slot
  Serial.println("Checking if driver is already in queue before enabling coin slot...");

  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String queueJson = fbdo.jsonString();

      // Check if this driver's RFID is already in the queue with "waiting" status
      String searchPattern = "\"driverRFID\":\"" + driver.rfidUID + "\"";
      int rfidPos = queueJson.indexOf(searchPattern);

      if (rfidPos != -1) {
        // Found the driver's RFID, now check if they have "waiting" status
        int statusStart = queueJson.indexOf("\"status\":", rfidPos);
        if (statusStart != -1) {
          int statusValueStart = queueJson.indexOf("\"", statusStart + 9) + 1;
          int statusValueEnd = queueJson.indexOf("\"", statusValueStart);
          String status = queueJson.substring(statusValueStart, statusValueEnd);

          if (status == "waiting") {
            Serial.println("✗ Driver " + driver.driverName + " is already in the queue!");
            Serial.println("Coin slot will NOT be enabled. Please wait for your turn.");
            showError("Already in queue! Please wait for your turn.");

            // Reset processing flag and re-enable NFC immediately
            isProcessingRFID = false;
            nfcEnabled = true;
            coinSlotEnabled = false;
            return; // Exit without enabling coin slot
          }
        }
      }
    }
  }

  Serial.println("Driver not in queue, proceeding to enable coin slot...");

  // Disable NFC to prevent interference
  nfcEnabled = false;
  coinSlotEnabled = true;
  isProcessingRFID = false; // Allow processing to continue
  
  Serial.println("NFC disabled: " + String(nfcEnabled ? "false" : "true"));
  Serial.println("Coin slot enabled: " + String(coinSlotEnabled ? "true" : "false"));
  
  // Send command to Arduino to power on coin slot
  Serial.println("Sending command 200 to Arduino via Serial2...");
  Serial2.write((uint8_t)200);
  Serial2.flush(); // Ensure data is sent
  
  Serial.println("✓ Command sent to Arduino");
  Serial.println("RFID verified! PN532 disabled. Coin slot enabled - insert ₱5 contribution.");
  Serial.println("Driver: " + driver.driverName + " (TODA #" + driver.todaNumber + ")");
  
  // Store current driver info for contribution processing
  currentDriver = driver;
  
  Serial.println("=== COIN SLOT READY ===\n");
}

void processCoinContribution() {
  totalSavings += 5;
  Serial.println("₱5 contribution received from " + currentDriver.driverName);
  Serial.println("Total today: ₱" + String(totalSavings));
  
  // Send command to Arduino to power off coin slot
  Serial2.write((uint8_t)201);
  
  // Process contribution and add to queue
  processContributionAndQueue();
  
  // Re-enable NFC for next driver
  coinSlotEnabled = false;
  nfcEnabled = true;
  isProcessingRFID = false; // Make sure RFID processing flag is cleared
  
  Serial.println("Contribution processed! PN532 re-enabled. Next driver can scan RFID.");
  Serial.println("System ready for next driver...");
}

void processContributionAndQueue() {
  Serial.println("Processing contribution and adding to queue...");
  
  // Record contribution in database
  recordContribution();
  
  // Add driver to queue
  addDriverToQueue();
}

void recordContribution() {
  // Create contribution record JSON
  String timestamp = String(time(nullptr));
  String contributionPath = "/contributions/" + timestamp;
  
  FirebaseJson contributionJson;
  contributionJson.set("driverRFID", currentDriver.rfidUID);
  contributionJson.set("driverName", currentDriver.driverName);
  contributionJson.set("todaNumber", currentDriver.todaNumber);
  contributionJson.set("amount", 5);
  contributionJson.set("timestamp", timestamp);
  contributionJson.set("date", getCurrentDate());
  
  if (Firebase.RTDB.setJSON(&fbdo, contributionPath, &contributionJson)) {
    Serial.println("✓ Contribution recorded in database");
  } else {
    Serial.println("✗ Failed to record contribution: " + fbdo.errorReason());
  }
}

void addDriverToQueue() {
  Serial.println("Checking if driver is already in queue...");

  // First, check if driver is already in the queue
  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String queueJson = fbdo.jsonString();

      // Check if this driver's RFID is already in the queue with "waiting" status
      String searchPattern = "\"driverRFID\":\"" + currentDriver.rfidUID + "\"";
      int rfidPos = queueJson.indexOf(searchPattern);

      if (rfidPos != -1) {
        // Found the driver's RFID, now check if they have "waiting" status
        int statusStart = queueJson.indexOf("\"status\":", rfidPos);
        if (statusStart != -1) {
          int statusValueStart = queueJson.indexOf("\"", statusStart + 9) + 1;
          int statusValueEnd = queueJson.indexOf("\"", statusValueStart);
          String status = queueJson.substring(statusValueStart, statusValueEnd);

          if (status == "waiting") {
            Serial.println("✗ Driver " + currentDriver.driverName + " is already in the queue!");
            Serial.println("Please wait for your turn or check with the operator.");
            showError("Already in queue! Please wait for your turn.");
            return; // Exit without adding to queue
          }
        }
      }
    }
  }

  Serial.println("Driver not in queue, adding to queue...");

  // Create queue entry JSON
  String timestamp = String(time(nullptr));
  String queuePath = "/queue/" + timestamp;
  
  FirebaseJson queueJson;
  queueJson.set("driverRFID", currentDriver.rfidUID);
  queueJson.set("driverName", currentDriver.driverName);
  queueJson.set("todaNumber", currentDriver.todaNumber);
  queueJson.set("queueTime", timestamp);
  queueJson.set("timestamp", getCurrentDate() + " " + getCurrentTime()); // Real-time readable timestamp
  queueJson.set("status", "waiting");
  queueJson.set("contributionPaid", true);
  
  if (Firebase.RTDB.setJSON(&fbdo, queuePath, &queueJson)) {
    Serial.println("✓ Driver added to queue successfully!");
    Serial.println("Driver " + currentDriver.driverName + " is now in the tricycle queue");
    Serial.println("Queue Time: " + getCurrentDate() + " " + getCurrentTime());
  } else {
    Serial.println("✗ Failed to add driver to queue: " + fbdo.errorReason());
  }
}

void showError(String message) {
  Serial.println("ERROR: " + message);
  Serial.println("Please try scanning the RFID card again...");
  // Here you could add LED indicators, buzzer, or display messages
}

String getCurrentDate() {
  time_t now = time(nullptr);
  struct tm* timeinfo = localtime(&now);
  
  char dateStr[11];
  sprintf(dateStr, "%04d-%02d-%02d", 
          timeinfo->tm_year + 1900,
          timeinfo->tm_mon + 1, 
          timeinfo->tm_mday);
  
  return String(dateStr);
}

String getCurrentTime() {
  time_t now = time(nullptr);
  struct tm* timeinfo = localtime(&now);
  
  char timeStr[9];
  sprintf(timeStr, "%02d:%02d:%02d", 
          timeinfo->tm_hour,
          timeinfo->tm_min, 
          timeinfo->tm_sec);
  
  return String(timeStr);
}