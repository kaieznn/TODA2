#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <WiFiClientSecure.h>
#include <time.h>

// WiFi credentials
#define WIFI_SSID "George"
#define WIFI_PASSWORD "Aixroch!092601"

// Firebase credentials
#define API_KEY "AIzaSyA9ADVig4CiO2Y3ELl3unzXajdzxCgRxHI"
#define DATABASE_URL "https://toda-contribution-system-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define USER_EMAIL "test@example.com"
#define USER_PASSWORD "test123456"

// Button pins (38-pin ESP32 compatible)
#define REGULAR_BUTTON_PIN 32
#define SPECIAL_BUTTON_PIN 33

// Firebase objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

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
String getCurrentDate();
String getCurrentTime();
bool findAndCompleteBooking();
void completeBookingWithDriver(String bookingId, String bookingPath);
bool hasAcceptedBookings();

void setup() {
  Serial.begin(115200);
  
  // Initialize button pins
  pinMode(REGULAR_BUTTON_PIN, INPUT_PULLUP);
  pinMode(SPECIAL_BUTTON_PIN, INPUT_PULLUP);
  
  Serial.println("ESP32 Button Controller: TODA Passenger Selection System");
  Serial.println("Buttons initialized: Regular=Pin32, Special=Pin33");
  
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
    if (Firebase.RTDB.get(&fbdo, "/queue")) {
      Serial.println("✓ Database connection successful!");
    } else {
      Serial.println("Database test result: " + fbdo.errorReason());
    }
  } else {
    Serial.println();
    Serial.println("✗ Firebase authentication failed");
  }
  
  // Check if there are drivers in queue on startup and set currentDriver
  Serial.println("Checking for drivers in queue on startup...");
  if (getFirstDriverInQueue()) {
    Serial.println("✓ Found driver in queue: " + currentDriver.driverName + " (TODA #" + currentDriver.todaNumber + ")");
    Serial.println("✓ Buttons are now ready to assign trips!");
  } else {
    Serial.println("No drivers in queue. Waiting for drivers to join queue from main ESP32...");
  }
  
  Serial.println("Button Controller ready: Press buttons to assign passengers to drivers");
}

void loop() {
  // Check button presses
  checkButtons();
  
  // Debug system status every 30 seconds
  static unsigned long lastSystemStatus = 0;
  if (millis() - lastSystemStatus > 30000) {
    Serial.println("Button Controller Status - WiFi: " + String(WiFi.status() == WL_CONNECTED ? "Connected" : "Disconnected") + 
                  ", Firebase: " + String(Firebase.ready() ? "Ready" : "Not Ready"));
    
    // Also check queue status periodically
    checkQueueStatus();
    lastSystemStatus = millis();
  }
  
  // Check for manual commands via Serial Monitor (for debugging)
  if (Serial.available()) {
    String command = Serial.readString();
    command.trim();
    command.toLowerCase();
    
    if (command == "regular") {
      Serial.println("Manual REGULAR passenger selection test...");
      handleRegularPassenger();
    } else if (command == "special") {
      Serial.println("Manual SPECIAL passenger selection test...");
      handleSpecialPassenger();
    } else if (command == "queue") {
      Serial.println("Checking driver queue status...");
      checkQueueStatus();
    } else if (command == "status") {
      Serial.println("\n=== BUTTON CONTROLLER STATUS ===");
      Serial.println("WiFi Status: " + String(WiFi.status() == WL_CONNECTED ? "Connected" : "Disconnected"));
      Serial.println("Firebase Ready: " + String(Firebase.ready() ? "true" : "false"));
      Serial.println("Current Driver: " + (currentDriver.driverName.length() > 0 ? currentDriver.driverName : "None"));
      Serial.println("================================");
    } else if (command == "refresh") {
      Serial.println("Refreshing driver queue...");
      if (getFirstDriverInQueue()) {
        Serial.println("✓ Updated current driver: " + currentDriver.driverName);
      } else {
        Serial.println("No drivers in queue");
      }
    }
  }
}

void checkButtons() {
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
    // First, check if there are any accepted bookings waiting for a driver
    if (findAndCompleteBooking()) {
      Serial.println("✓ Completed a pending booking with driver " + currentDriver.driverName);

      // Remove driver from queue
      removeDriverFromQueue();

      // Clear current driver and get next one if available
      currentDriver = Driver(); // Reset
      if (getFirstDriverInQueue()) {
        Serial.println("Next driver ready: " + currentDriver.driverName);
      } else {
        Serial.println("No more drivers in queue");
      }
    } else {
      // No pending bookings, create a regular trip record
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

        // Clear current driver and get next one if available
        currentDriver = Driver(); // Reset
        if (getFirstDriverInQueue()) {
          Serial.println("Next driver ready: " + currentDriver.driverName);
        } else {
          Serial.println("No more drivers in queue");
        }

      } else {
        Serial.println("✗ Failed to create trip record: " + fbdo.errorReason());
      }
    }
  } else {
    Serial.println("✗ No driver in queue to assign trip!");
  }
  
  Serial.println("==========================================\n");
}

void handleSpecialPassenger() {
  Serial.println("\n=== DRIVER SELECTED: SPECIAL PASSENGER ===");
  
  // Check if we have a current driver, if not try to get from queue
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
    // For special button, ONLY check for accepted bookings - don't create regular trips
    if (findAndCompleteBooking()) {
      Serial.println("✓ Completed a pending booking with driver " + currentDriver.driverName);

      // Remove driver from queue
      removeDriverFromQueue();

      // Clear current driver and get next one if available
      currentDriver = Driver(); // Reset
      if (getFirstDriverInQueue()) {
        Serial.println("Next driver ready: " + currentDriver.driverName);
      } else {
        Serial.println("No more drivers in queue");
      }
    } else {
      // No accepted bookings available - do NOT assign driver
      Serial.println("✗ No ACCEPTED bookings found. Driver " + currentDriver.driverName + " remains in queue.");
      Serial.println("Special button can only assign drivers to ACCEPTED bookings.");
    }
  } else {
    Serial.println("✗ No driver in queue to assign trip!");
  }
  
  Serial.println("===========================================\n");
}

bool getFirstDriverInQueue() {
  // Get all drivers in queue
  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();
      
      if (jsonStr == "null" || jsonStr.length() == 0) {
        return false;
      }
      
      // Find the smallest timestamp (oldest entry) using pattern matching
      String smallestKey = "";
      unsigned long smallestTimestamp = 4000000000UL;
      
      // Look for pattern: "1234567890":{
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
          
          // Only consider keys that are valid timestamps (10-digit unix timestamps)
          if (timestamp > 1000000000UL && timestamp < smallestTimestamp) {
            smallestTimestamp = timestamp;
            smallestKey = key;
          }
        }
        
        pos = quoteEnd + 1;
      }
      
      if (smallestKey.length() > 0) {
        firstDriverQueueKey = smallestKey;
        
        // Get the driver details from this queue entry
        String queuePath = "/queue/" + firstDriverQueueKey;
        
        if (Firebase.RTDB.get(&fbdo, queuePath)) {
          if (fbdo.dataType() == "json") {
            String driverJson = fbdo.jsonString();
            
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
              
              return true;
            }
          }
        }
      }
    }
  } else {
    // Firebase removes empty nodes, so "path not exist" means no drivers in queue
    if (fbdo.errorReason().indexOf("path not exist") != -1 || 
        fbdo.errorReason().indexOf("not found") != -1 ||
        fbdo.httpCode() == 404) {
      // Silent - this is normal when queue is empty
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

void checkQueueStatus() {
  Serial.println("\n=== DRIVER QUEUE STATUS ===");
  
  if (Firebase.RTDB.get(&fbdo, "/queue")) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();
      
      if (jsonStr == "null" || jsonStr.length() == 0) {
        Serial.println("Queue is empty");
      } else {
        Serial.println("Queue data available");
        
        // Count drivers in queue
        int driverCount = 0;
        int pos = 0;
        while ((pos = jsonStr.indexOf("\"driverName\"", pos)) != -1) {
          driverCount++;
          pos++;
        }
        Serial.println("Total drivers in queue: " + String(driverCount));
        
        // Show current driver if available
        if (currentDriver.driverName.length() > 0) {
          Serial.println("Current/Next driver: " + currentDriver.driverName + " (TODA #" + currentDriver.todaNumber + ")");
        }
      }
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

String getCurrentDate() {
  time_t now = time(nullptr);
  struct tm* timeinfo = localtime(&now);
  
  char dateStr[20];
  sprintf(dateStr, "%04d-%02d-%02d", 
          timeinfo->tm_year + 1900, 
          timeinfo->tm_mon + 1, 
          timeinfo->tm_mday);
  
  return String(dateStr);
}

String getCurrentTime() {
  time_t now = time(nullptr);
  struct tm* timeinfo = localtime(&now);
  
  char timeStr[20];
  sprintf(timeStr, "%02d:%02d:%02d", 
          timeinfo->tm_hour, 
          timeinfo->tm_min, 
          timeinfo->tm_sec);
  
  return String(timeStr);
}

bool findAndCompleteBooking() {
  Serial.println("Checking for accepted bookings...");

  // Check for accepted bookings that need driver assignment
  if (Firebase.RTDB.get(&fbdo, "/bookings")) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();
      Serial.println("Bookings JSON received, length: " + String(jsonStr.length()));

      if (jsonStr != "null" && jsonStr.length() > 0) {
        // Look for bookings with status "ACCEPTED" that need a driver
        int pos = 0;
        while ((pos = jsonStr.indexOf("\"status\":\"ACCEPTED\"", pos)) != -1) {
          Serial.println("Found ACCEPTED booking at position: " + String(pos));

          // Find the booking ID by looking for the key pattern
          // Search backwards from the status field to find the booking key
          int searchStart = pos;
          String bookingId = "";

          // Look for the pattern: "-OYh..." which is the booking ID format
          int keySearch = jsonStr.lastIndexOf("\"-", searchStart);
          while (keySearch > 0 && keySearch < searchStart) {
            int keyEnd = jsonStr.indexOf("\"", keySearch + 1);
            if (keyEnd != -1) {
              String potentialKey = jsonStr.substring(keySearch + 1, keyEnd);
              // Check if this looks like a booking ID (starts with -, contains letters/numbers)
              if (potentialKey.startsWith("-O") && potentialKey.length() > 10) {
                bookingId = potentialKey;
                break;
              }
            }
            keySearch = jsonStr.lastIndexOf("\"-", keySearch - 1);
          }

          if (bookingId.length() > 0) {
            Serial.println("Found booking ID: " + bookingId);
            String bookingPath = "/bookings/" + bookingId;

            // Complete the booking with the current driver
            completeBookingWithDriver(bookingId, bookingPath);
            return true;
          } else {
            Serial.println("Could not extract booking ID");
          }

          pos += 20; // Move past the current status field
        }
        Serial.println("No ACCEPTED bookings found");
      } else {
        Serial.println("No bookings data available");
      }
    } else {
      Serial.println("Bookings data is not JSON format");
    }
  } else {
    Serial.println("Failed to get bookings: " + fbdo.errorReason());
  }

  return false;
}

void completeBookingWithDriver(String bookingId, String bookingPath) {
  Serial.println("Completing booking " + bookingId + " with driver " + currentDriver.driverName);

  // First get the existing booking data
  if (Firebase.RTDB.get(&fbdo, bookingPath)) {
    if (fbdo.dataType() == "json") {
      // Parse the existing booking data and update it
      FirebaseJson bookingJson;
      bookingJson.setJsonData(fbdo.jsonString());

      // Update with driver details
      bookingJson.set("assignedDriverId", currentDriver.rfidUID);
      bookingJson.set("driverRFID", currentDriver.rfidUID);
      bookingJson.set("driverName", currentDriver.driverName);
      bookingJson.set("assignedTricycleId", currentDriver.todaNumber);
      bookingJson.set("todaNumber", currentDriver.todaNumber);
      bookingJson.set("status", "IN_PROGRESS");
      bookingJson.set("completionTime", String(time(nullptr)));

      if (Firebase.RTDB.setJSON(&fbdo, bookingPath, &bookingJson)) {
        Serial.println("✓ Booking completed successfully");
      } else {
        Serial.println("✗ Failed to complete booking: " + fbdo.errorReason());
      }
    }
  } else {
    Serial.println("✗ Failed to get existing booking data: " + fbdo.errorReason());
  }
}

// Updated driver lookup - no changes needed as it only reads from queue
// The queue is populated by the main ESP32 which handles driver registration
bool hasAcceptedBookings() {
  // Check if the current driver has any accepted bookings
  String driverPath = "/drivers/" + currentDriver.rfidUID;

  if (Firebase.RTDB.get(&fbdo, driverPath)) {
    if (fbdo.dataType() == "json") {
      String jsonStr = fbdo.jsonString();

      if (jsonStr != "null" && jsonStr.length() > 0) {
        // Check for any bookings with status "accepted"
        if (jsonStr.indexOf("\"status\":\"accepted\"") != -1) {
          return true;
        }
      }
    }
  }

  return false;
}
