package com.example.toda.service

import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.example.toda.data.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseRealtimeDatabaseService {

    private val database: DatabaseReference = Firebase.database.reference

    init {
        // Add debugging for Firebase database connection
        println("=== FIREBASE DATABASE SERVICE INIT ===")
        println("Database reference: ${database.ref}")
        println("Database URL: ${Firebase.database.app.options.databaseUrl}")
        println("==========================================")
    }

    // Database references for different collections
    private val usersRef = database.child("users")
    private val userProfilesRef = database.child("userProfiles")
    private val bookingsRef = database.child("bookings")
    private val tricyclesRef = database.child("tricycles")
    private val driverRegistrationsRef = database.child("driverRegistrations")
    private val todaOrganizationsRef = database.child("todaOrganizations")
    private val driverLocationsRef = database.child("driverLocations")
    private val chatMessagesRef = database.child("chatMessages")
    private val chatRoomsRef = database.child("chatRooms")
    private val notificationsRef = database.child("notifications")
    private val emergencyAlertsRef = database.child("emergencyAlerts")

    // Index references for efficient querying
    private val phoneNumberIndexRef = database.child("phoneNumberIndex")
    private val activeBookingsRef = database.child("activeBookings")
    private val availableDriversRef = database.child("availableDrivers")
    private val pendingApplicationsRef = database.child("pendingApplications")
    private val bookingIndexRef = database.child("bookingIndex") // Add booking index reference

    // Hardware Integration Methods (ESP32 Support)
    private val hardwareDriversRef = database.child("drivers")
    private val driverQueueRef = database.child("driverQueue")
    private val contributionsRef = database.child("contributions")
    private val activeTripsRef = database.child("activeTrips")
    private val systemStatusRef = database.child("systemStatus")
    private val ratingsRef = database.child("ratings") // Add ratings reference
    private val rfidChangeHistoryRef = database.child("rfidChangeHistory") // Add RFID change history reference

    // User Management
    suspend fun createUser(user: FirebaseUser): Boolean {
        return try {
            usersRef.child(user.id).setValue(user).await()
            // Add to phone number index for quick lookup
            phoneNumberIndexRef.child(user.phoneNumber).setValue(user.id).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Create user with additional data for detailed registration
    suspend fun createUserWithData(userId: String, userData: Map<String, Any>): Boolean {
        return try {
            // Save the user data to the users node
            usersRef.child(userId).setValue(userData).await()

            // Add to phone number index for quick lookup
            val phoneNumber = userData["phoneNumber"] as? String ?: ""
            if (phoneNumber.isNotBlank()) {
                phoneNumberIndexRef.child(phoneNumber).setValue(userId).await()
            }
            true
        } catch (e: Exception) {
            println("Error creating user with data: ${e.message}")
            false
        }
    }

    suspend fun getUserByPhoneNumber(phoneNumber: String): FirebaseUser? {
        return try {
            val userId = phoneNumberIndexRef.child(phoneNumber).get().await().getValue(String::class.java)
            userId?.let {
                usersRef.child(it).get().await().getValue(FirebaseUser::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserByUserId(userId: String): FirebaseUser? {
        return try {
            usersRef.child(userId).get().await().getValue(FirebaseUser::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserProfile(userId: String, profile: FirebaseUserProfile): Boolean {
        return try {
            userProfilesRef.child(userId).setValue(profile).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getUserProfile(userId: String): Flow<FirebaseUserProfile?> = callbackFlow {
        val listener = userProfilesRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(FirebaseUserProfile::class.java)
                trySend(profile)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        })
        awaitClose { userProfilesRef.child(userId).removeEventListener(listener) }
    }

    // New: stream raw user data from 'users' node to access discount fields stored there
    fun getUserRaw(userId: String): Flow<Map<String, Any>?> = callbackFlow {
        val listener = usersRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.value
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any>
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        })
        awaitClose { usersRef.child(userId).removeEventListener(listener) }
    }

    // Booking Management
    suspend fun createBooking(booking: FirebaseBooking): String? {
        return try {
            println("=== FIREBASE CREATE BOOKING DEBUG ===")
            println("Attempting to create booking: $booking")

            // First, test basic Firebase connectivity
            println("Testing Firebase connectivity...")
            val testRef = database.child("test").push()
            testRef.setValue(mapOf("timestamp" to System.currentTimeMillis())).await()
            println("Firebase connectivity test PASSED")

            val bookingRef = bookingsRef.push()
            val bookingId = bookingRef.key

            println("Generated booking ID: $bookingId")

            if (bookingId == null) {
                println("ERROR: Failed to generate booking ID")
                return null
            }

            val bookingWithId = booking.copy(id = bookingId)
            println("Booking with ID: $bookingWithId")

            println("Attempting to save to Firebase...")
            bookingRef.setValue(bookingWithId).await()
            println("SUCCESS: Booking saved to Firebase with ID: $bookingId")

            // Create bookingIndex entry for ESP32 efficient lookup
            // Similar to rfidUIDIndex, this contains minimal data for quick access
            println("Creating bookingIndex entry for ESP32...")
            val bookingIndexEntry = mapOf(
                "status" to booking.status,
                "timestamp" to booking.timestamp,
                "driverRFID" to (booking.driverRFID ?: "")
            )

            bookingIndexRef.child(bookingId).setValue(bookingIndexEntry).await()
            println("SUCCESS: bookingIndex entry created: $bookingId -> status=${booking.status}, timestamp=${booking.timestamp}")

            // Clean up test data
            testRef.removeValue()

            bookingId
        } catch (e: Exception) {
            println("ERROR: Exception in createBooking: ${e.message}")
            println("Exception type: ${e::class.java.simpleName}")
            e.printStackTrace()
            null
        }
    }

    fun getActiveBookings(): Flow<List<FirebaseBooking>> = callbackFlow {
        val listener = bookingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("=== FIREBASE SERVICE ACTIVE BOOKINGS DEBUG ===")
                println("Total bookings in Firebase: ${snapshot.childrenCount}")

                val bookings = mutableListOf<FirebaseBooking>()
                snapshot.children.forEach { child ->
                    println("Processing booking: ${child.key}")

                    val rawStatus = child.child("status").getValue(String::class.java)
                    println("Raw status from Firebase: '$rawStatus'")

                    try {
                        // Try direct conversion first
                        val booking = child.getValue(FirebaseBooking::class.java)
                        if (booking != null) {
                            println("Direct conversion SUCCESS for ${booking.id}")
                            println("Booking status: ${booking.status}")
                            println("Customer ID: ${booking.customerId}")
                            println("Driver RFID: ${booking.driverRFID}")
                            println("Assigned Driver ID: ${booking.assignedDriverId}")

                            // Include PENDING, ACCEPTED, IN_PROGRESS, and COMPLETED bookings
                            if (booking.status in listOf("PENDING", "ACCEPTED", "IN_PROGRESS", "COMPLETED")) {
                                println("Adding booking to active list: ${booking.id}")
                                bookings.add(booking)
                            } else {
                                println("Booking status '${booking.status}' not in active list")
                            }
                        } else {
                            println("Direct conversion returned NULL for ${child.key}")
                        }
                    } catch (e: Exception) {
                        println("Direct conversion FAILED for ${child.key}: ${e.message}")

                        // If direct conversion fails, try manual conversion
                        try {
                            val manualBooking = convertDataSnapshotToFirebaseBooking(child)
                            if (manualBooking != null) {
                                println("Manual conversion SUCCESS for ${manualBooking.id}")
                                println("Manual booking status: ${manualBooking.status}")

                                if (manualBooking.status in listOf("PENDING", "ACCEPTED", "IN_PROGRESS", "COMPLETED")) {
                                    println("Adding manually converted booking to active list: ${manualBooking.id}")
                                    bookings.add(manualBooking)
                                }
                            } else {
                                println("Manual conversion also returned NULL for ${child.key}")
                            }
                        } catch (e2: Exception) {
                            println("Manual conversion also FAILED for ${child.key}: ${e2.message}")
                            android.util.Log.e("FirebaseService", "Failed to convert booking: ${child.key}", e2)
                        }
                    }
                }

                println("Final active bookings count: ${bookings.size}")
                bookings.forEach { booking ->
                    println("Active booking: ${booking.id} - Status: ${booking.status} - Customer: ${booking.customerId} - DriverRFID: ${booking.driverRFID}")
                }
                println("===============================================")

                trySend(bookings.toList())
            }
            override fun onCancelled(error: DatabaseError) {
                println("Firebase listener cancelled: ${error.message}")
                trySend(emptyList())
            }
        })
        awaitClose { bookingsRef.removeEventListener(listener) }
    }

    private fun convertDataSnapshotToFirebaseBooking(snapshot: DataSnapshot): FirebaseBooking? {
        return try {
            val data = snapshot.value as? Map<String, Any> ?: return null

            FirebaseBooking(
                id = data["id"] as? String ?: snapshot.key ?: "",
                customerId = data["customerId"] as? String ?: "",
                customerName = data["customerName"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                isPhoneVerified = (data["isPhoneVerified"] as? Boolean)
                    ?: (data["phoneVerified"] as? Boolean) ?: false, // Handle both field names for backward compatibility
                pickupLocation = data["pickupLocation"] as? String ?: "",
                destination = data["destination"] as? String ?: "",
                pickupCoordinates = (data["pickupCoordinates"] as? Map<String, Any>)?.mapValues {
                    when (val value = it.value) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } ?: emptyMap(),
                dropoffCoordinates = (data["dropoffCoordinates"] as? Map<String, Any>)?.mapValues {
                    when (val value = it.value) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } ?: emptyMap(),
                estimatedFare = when (val value = data["estimatedFare"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                },
                actualFare = when (val value = data["actualFare"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                },
                status = data["status"] as? String ?: "PENDING",
                timestamp = when (val value = data["timestamp"]) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                },
                assignedDriverId = data["assignedDriverId"] as? String ?: "",
                assignedTricycleId = data["assignedTricycleId"] as? String ?: "",
                verificationCode = data["verificationCode"] as? String ?: "",
                completionTime = when (val value = data["completionTime"]) {
                    is Number -> value.toString()
                    is String -> value
                    else -> "0"
                },
                rating = when (val value = data["rating"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                },
                feedback = data["feedback"] as? String ?: "",
                paymentMethod = data["paymentMethod"] as? String ?: "CASH",
                distance = when (val value = data["distance"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                },
                duration = when (val value = data["duration"]) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull() ?: 0
                    else -> 0
                },
                driverName = data["driverName"] as? String ?: "",
                driverRFID = data["driverRFID"] as? String ?: "",
                todaNumber = data["todaNumber"] as? String ?: "",
                tripType = data["tripType"] as? String ?: "",
                // New: arrival/no-show tracking
                arrivedAtPickup = (data["arrivedAtPickup"] as? Boolean) ?: false,
                arrivedAtPickupTime = when (val value = data["arrivedAtPickupTime"]) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull() ?: 0L
                    else -> 0L
                },
                isNoShow = (data["isNoShow"] as? Boolean) ?: false,
                noShowReportedTime = when (val value = data["noShowReportedTime"]) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull() ?: 0L
                    else -> 0L
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseService", "Error in manual conversion", e)
            null
        }
    }

    suspend fun updateBookingStatus(bookingId: String, status: String, driverId: String? = null): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "bookings/$bookingId/status" to status
            )

            driverId?.let {
                updates["bookings/$bookingId/assignedDriverId"] = it
            }

            // If completing the trip, record completionTime
            if (status == "COMPLETED") {
                updates["bookings/$bookingId/completionTime"] = System.currentTimeMillis()
            }

            // Update bookingIndex status as well
            updates["bookingIndex/$bookingId/status"] = status

            // Update only the specified fields in the existing booking record
            database.updateChildren(updates).await()

            // No longer managing activeBookings index - status in booking is the source of truth

            true
        } catch (e: Exception) {
            false
        }
    }

    // Driver Location Management
    suspend fun updateDriverLocation(location: FirebaseDriverLocation): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "driverLocations/${location.driverId}" to location,
                "tricycles/${location.tricycleId}/currentLocation" to mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude
                ),
                "tricycles/${location.tricycleId}/isOnline" to location.isOnline
            )

            // Update available drivers index
            if (location.isAvailable && location.isOnline) {
                updates["availableDrivers/${location.driverId}"] = mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "tricycleId" to location.tricycleId,
                    "timestamp" to location.timestamp
                )
            }

            // Apply updates first
            database.updateChildren(updates).await()

            // Remove from available drivers if not available (separate operation)
            if (!location.isAvailable || !location.isOnline) {
                availableDriversRef.child(location.driverId).removeValue().await()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAvailableDrivers(): Flow<Map<String, Map<String, Any>>> = callbackFlow {
        val listener = availableDriversRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val drivers = mutableMapOf<String, Map<String, Any>>()
                snapshot.children.forEach { child ->
                    child.key?.let { driverId ->
                        (child.value as? Map<String, Any>)?.let { driverData ->
                            drivers[driverId] = driverData
                        }
                    }
                }
                trySend(drivers)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        })
        awaitClose { availableDriversRef.removeEventListener(listener) }
    }

    // Driver Registration Management
    suspend fun createDriverRegistration(registration: FirebaseDriverRegistration): String? {
        return try {
            val regRef = driverRegistrationsRef.push()
            val regId = regRef.key ?: return null
            val regWithId = registration.copy(id = regId)

            regRef.setValue(regWithId).await()
            // Add to pending applications index
            pendingApplicationsRef.child(regId).setValue(true).await()

            regId
        } catch (e: Exception) {
            null
        }
    }

    // New method to create drivers directly in the drivers table
    suspend fun createDriverInDriversTable(
        driverName: String,
        todaNumber: String,
        phoneNumber: String,
        address: String,
        emergencyContact: String,
        licenseNumber: String,
        licenseExpiry: Long,
        yearsOfExperience: Int,
        tricyclePlateNumber: String
    ): String? {
        return try {
            // Generate a unique ID for the driver (but rfidUID will be empty initially)
            val driverId = "driver_${System.currentTimeMillis()}_${phoneNumber.takeLast(4)}"

            val driver = mapOf(
                "driverId" to driverId,
                "rfidUID" to "", // Empty initially - admin will assign RFID card later
                "driverName" to driverName,
                "todaNumber" to todaNumber,
                "isActive" to true,
                "registrationDate" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                "phoneNumber" to phoneNumber,
                "address" to address,
                "emergencyContact" to emergencyContact,
                "licenseNumber" to licenseNumber,
                "licenseExpiry" to licenseExpiry,
                "yearsOfExperience" to yearsOfExperience,
                "tricyclePlateNumber" to tricyclePlateNumber, // Add tricycle plate number
                "needsRfidAssignment" to true // Flag to indicate RFID needs to be assigned
            )

            hardwareDriversRef.child(driverId).setValue(driver).await()

            // Also create a user entry for the driver
            val driverUser = FirebaseUser(
                id = driverId,
                phoneNumber = phoneNumber,
                name = driverName,
                userType = "DRIVER",
                isVerified = true,
                registrationDate = System.currentTimeMillis(),
                todaId = todaNumber,
                membershipNumber = "MEM-${todaNumber.padStart(4, '0')}",
                membershipStatus = "Active"
            )

            usersRef.child(driverId).setValue(driverUser).await()
            phoneNumberIndexRef.child(phoneNumber).setValue(driverId).await()

            driverId // Return the driver ID
        } catch (e: Exception) {
            println("Error creating driver in drivers table: ${e.message}")
            null
        }
    }

    // New method to get all drivers (for admin management)
    suspend fun getAllDrivers(): List<Map<String, Any>> {
        return try {
            val snapshot = hardwareDriversRef.get().await()
            val drivers = mutableListOf<Map<String, Any>>()

            snapshot.children.forEach { child ->
                val driverData = child.value as? Map<String, Any>
                if (driverData != null) {
                    val driverWithKey = driverData.toMutableMap()
                    driverWithKey["id"] = child.key ?: ""
                    drivers.add(driverWithKey)
                }
            }

            drivers
        } catch (e: Exception) {
            println("Error getting all drivers: ${e.message}")
            emptyList()
        }
    }

    // New method to assign RFID to a driver
    suspend fun assignRfidToDriver(driverId: String, rfidUID: String): Boolean {
        return try {
            // Verify driver exists before assigning RFID
            val driverSnapshot = database.child("drivers").child(driverId).get().await()

            if (!driverSnapshot.exists()) {
                println("Error: Driver not found for ID: $driverId")
                return false
            }

            // Simple and efficient: RFID index only contains the driver ID reference
            val updates = mapOf(
                "drivers/$driverId/rfidUID" to rfidUID,
                "drivers/$driverId/hasRfidAssigned" to true,
                "drivers/$driverId/needsRfidAssignment" to false,
                // RFID UID index contains only the driver ID - hardware can use this to fetch full driver data
                "rfidUIDIndex/$rfidUID" to driverId
            )

            database.updateChildren(updates).await()
            println("RFID assigned successfully. Index created: rfidUIDIndex/$rfidUID -> $driverId")
            true
        } catch (e: Exception) {
            println("Error assigning RFID to driver: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Chat Management
    suspend fun sendMessage(message: FirebaseChatMessage): Boolean {
        return try {
            println("🔥 FirebaseService: Sending message to booking ${message.bookingId}")
            println("   Message: ${message.message}")
            println("   From: ${message.senderName} (${message.senderId})")
            println("   To: ${message.receiverId}")

            val messageRef = chatMessagesRef.child(message.bookingId).push()
            val messageId = messageRef.key ?: return false
            val messageWithId = message.copy(id = messageId)

            messageRef.setValue(messageWithId).await()
            println("✅ Message sent successfully with ID: $messageId")
            true
        } catch (e: Exception) {
            println("❌ Failed to send message: ${e.message}")
            false
        }
    }

    fun getChatMessages(bookingId: String): Flow<List<FirebaseChatMessage>> = callbackFlow {
        println("🔥 FirebaseService: Setting up listener for messages in booking $bookingId")
        val listener = chatMessagesRef.child(bookingId)
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    println("🔥 FirebaseService: Messages data changed for booking $bookingId")
                    println("   Snapshot exists: ${snapshot.exists()}")
                    println("   Children count: ${snapshot.childrenCount}")

                    val messages = snapshot.children.mapNotNull {
                        val message = it.getValue(FirebaseChatMessage::class.java)
                        println("   Found message: ${message?.senderName}: ${message?.message}")
                        message
                    }
                    println("🔥 FirebaseService: Sending ${messages.size} messages to UI")
                    trySend(messages)
                }
                override fun onCancelled(error: DatabaseError) {
                    println("❌ FirebaseService: Messages listener cancelled: ${error.message}")
                    trySend(emptyList())
                }
            })
        awaitClose {
            println("🔥 FirebaseService: Removing messages listener for booking $bookingId")
            chatMessagesRef.child(bookingId).removeEventListener(listener)
        }
    }

    // Enhanced Chat Management with Auto-Room Creation
    suspend fun createOrGetChatRoom(bookingId: String, customerId: String, customerName: String, driverId: String, driverName: String): String? {
        return try {
            println("=== CREATING/GETTING CHAT ROOM ===")
            println("Booking: $bookingId, Customer: $customerName, Driver: $driverName")

            // Check if chat room already exists for this booking
            val existingRoomSnapshot = chatRoomsRef.orderByChild("bookingId").equalTo(bookingId).get().await()

            if (existingRoomSnapshot.exists()) {
                val existingRoom = existingRoomSnapshot.children.firstOrNull()
                println("Chat room already exists: ${existingRoom?.key}")
                return existingRoom?.key
            }

            // Create new chat room
            val chatRoomRef = chatRoomsRef.push()
            val chatRoomId = chatRoomRef.key ?: return null

            val chatRoom = FirebaseChatRoom(
                id = chatRoomId,
                bookingId = bookingId,
                customerId = customerId,
                customerName = customerName,
                driverId = driverId,
                driverName = driverName,
                createdAt = System.currentTimeMillis(),
                lastMessageTime = System.currentTimeMillis(),
                lastMessage = "Chat room created",
                isActive = true
            )

            chatRoomRef.setValue(chatRoom).await()

            // Send initial system message
            val welcomeMessage = FirebaseChatMessage(
                bookingId = bookingId,
                senderId = "system",
                senderName = "TODA System",
                receiverId = "all",
                message = "Chat started between $customerName and $driverName. Have a safe trip!",
                timestamp = System.currentTimeMillis(),
                messageType = "SYSTEM",
                isRead = false
            )
            sendMessage(welcomeMessage)

            println("Successfully created chat room: $chatRoomId")
            chatRoomId
        } catch (e: Exception) {
            println("Error creating chat room: ${e.message}")
            null
        }
    }

    suspend fun updateChatRoomLastMessage(bookingId: String, lastMessage: String): Boolean {
        return try {
            val roomSnapshot = chatRoomsRef.orderByChild("bookingId").equalTo(bookingId).get().await()
            if (roomSnapshot.exists()) {
                val roomKey = roomSnapshot.children.firstOrNull()?.key
                if (roomKey != null) {
                    val updates = mapOf(
                        "chatRooms/$roomKey/lastMessage" to lastMessage,
                        "chatRooms/$roomKey/lastMessageTime" to System.currentTimeMillis()
                    )
                    database.updateChildren(updates).await()
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun getChatRoom(bookingId: String): Flow<FirebaseChatRoom?> = callbackFlow {
        val listener = chatRoomsRef.orderByChild("bookingId").equalTo(bookingId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatRoom = snapshot.children.firstOrNull()?.getValue(FirebaseChatRoom::class.java)
                    trySend(chatRoom)
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            })
        awaitClose { chatRoomsRef.removeEventListener(listener) }
    }

    fun getUserChatRooms(userId: String): Flow<List<FirebaseChatRoom>> = callbackFlow {
        // Get chat rooms where user is either customer or driver
        val customerListener = chatRoomsRef.orderByChild("customerId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val customerRooms = snapshot.children.mapNotNull {
                        it.getValue(FirebaseChatRoom::class.java)
                    }

                    // Also get rooms where user is the driver
                    chatRoomsRef.orderByChild("driverId").equalTo(userId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(driverSnapshot: DataSnapshot) {
                                val driverRooms = driverSnapshot.children.mapNotNull {
                                    it.getValue(FirebaseChatRoom::class.java)
                                }

                                // Combine and deduplicate
                                val allRooms = (customerRooms + driverRooms).distinctBy { it.id }
                                    .sortedByDescending { it.lastMessageTime }
                                trySend(allRooms)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                trySend(emptyList())
                            }
                        })
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            })
        awaitClose { chatRoomsRef.removeEventListener(customerListener) }
    }

    // Enhanced updateBookingStatus that auto-creates chat rooms
    suspend fun updateBookingStatusWithChatRoom(bookingId: String, status: String, driverId: String? = null): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "bookings/$bookingId/status" to status
            )

            driverId?.let {
                updates["bookings/$bookingId/assignedDriverId"] = it
                updates["bookings/$bookingId/driverRFID"] = it

                // Update bookingIndex with driver assignment
                updates["bookingIndex/$bookingId/driverRFID"] = it
            }

            // Update bookingIndex status
            updates["bookingIndex/$bookingId/status"] = status

            // Update the booking
            database.updateChildren(updates).await()

            // If status is IN_PROGRESS and we have a driver, create chat room
            if (status == "IN_PROGRESS" && driverId != null) {
                // Get booking details to create chat room
                val bookingSnapshot = bookingsRef.child(bookingId).get().await()
                val booking = bookingSnapshot.getValue(FirebaseBooking::class.java)

                if (booking != null) {
                    // Get driver details
                    val driverSnapshot = usersRef.child(driverId).get().await()
                    val driver = driverSnapshot.getValue(FirebaseUser::class.java)

                    if (driver != null) {
                        println("Auto-creating chat room for IN_PROGRESS booking")
                        createOrGetChatRoom(
                            bookingId = bookingId,
                            customerId = booking.customerId,
                            customerName = booking.customerName,
                            driverId = driverId,
                            driverName = driver.name
                        )
                    }
                }
            }

            true
        } catch (e: Exception) {
            println("Error updating booking status with chat room: ${e.message}")
            false
        }
    }

    // Driver Management
    suspend fun getDriverById(driverId: String): Map<String, Any>? {
        return try {
            val snapshot = hardwareDriversRef.child(driverId).get().await()
            if (snapshot.exists()) {
                snapshot.value as? Map<String, Any>
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // System Management
    suspend fun updateSystemStatus(component: String, status: FirebaseSystemStatus): Boolean {
        return try {
            systemStatusRef.child(component).setValue(status).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getSystemStatus(): Flow<Map<String, FirebaseSystemStatus>> = callbackFlow {
        val listener = systemStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusMap = mutableMapOf<String, FirebaseSystemStatus>()
                snapshot.children.forEach { child ->
                    child.key?.let { component ->
                        child.getValue(FirebaseSystemStatus::class.java)?.let { status ->
                            statusMap[component] = status
                        }
                    }
                }
                trySend(statusMap)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        })
        awaitClose { systemStatusRef.removeEventListener(listener) }
    }

    // Unified Queue Management (Combines Hardware + Mobile)
    fun getUnifiedDriverQueue(): Flow<List<UnifiedQueueEntry>> = callbackFlow {
        val listener = driverQueueRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val entries = snapshot.children.mapNotNull {
                        it.getValue(FirebaseDriverQueueEntry::class.java)?.let { entry ->
                            UnifiedQueueEntry(
                                driverId = entry.driverId,
                                driverName = entry.driverName,
                                queuePosition = entry.queuePosition,
                                timestamp = entry.timestamp,
                                source = "hardware",
                                isInPhysicalQueue = true
                            )
                        }
                    }
                    trySend(entries)
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            })
        awaitClose { driverQueueRef.removeEventListener(listener) }
    }

    // Driver Contributions Management
    suspend fun getDriverContributions(driverId: String): List<FirebaseContribution> {
        return try {
            println("=== GETTING DRIVER CONTRIBUTIONS ===")
            println("Driver ID: $driverId")

            // First get the driver's RFID
            val driverSnapshot = hardwareDriversRef.child(driverId).get().await()
            val driverRFID = driverSnapshot.child("rfidUID").getValue(String::class.java) ?: ""

            println("Driver RFID: $driverRFID")

            if (driverRFID.isEmpty()) {
                println("No RFID found for driver $driverId")
                return emptyList()
            }

            // Query contributions by driverRFID
            val snapshot = contributionsRef.orderByChild("driverRFID").equalTo(driverRFID).get().await()

            val contributions = mutableListOf<FirebaseContribution>()
            snapshot.children.forEach { child ->
                val amount = child.child("amount").getValue(Double::class.java) ?: 0.0
                val date = child.child("date").getValue(String::class.java) ?: ""
                val timestamp = child.child("timestamp").getValue(String::class.java) ?: "0"
                val driverName = child.child("driverName").getValue(String::class.java) ?: ""
                val todaNumber = child.child("todaNumber").getValue(String::class.java) ?: ""

                // Convert timestamp string to Long
                val timestampLong = try {
                    timestamp.toLong() * 1000 // Convert seconds to milliseconds
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                contributions.add(
                    FirebaseContribution(
                        id = child.key ?: "",
                        driverId = driverId,
                        driverName = driverName,
                        rfidUID = driverRFID,
                        amount = amount,
                        timestamp = timestampLong,
                        date = date,
                        contributionType = "MANUAL",
                        notes = "TODA $todaNumber",
                        verified = true,
                        source = "mobile"
                    )
                )
            }

            println("Found ${contributions.size} contributions for driver $driverId (RFID: $driverRFID)")
            contributions.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            println("Error getting driver contributions: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getDriverTodayContributions(driverId: String): List<FirebaseContribution> {
        return try {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val allContributions = getDriverContributions(driverId)
            allContributions.filter { it.timestamp >= todayStart }
        } catch (e: Exception) {
            println("Error getting driver today contributions: ${e.message}")
            emptyList()
        }
    }

    // Record coin insertion/contribution
    suspend fun recordCoinInsertion(rfidUID: String, amount: Double = 20.0, deviceId: String = "default"): Boolean {
        return try {
            println("=== RECORDING COIN INSERTION ===")
            println("RFID: $rfidUID, Amount: ₱$amount, Device: $deviceId")

            // First, find the driver by RFID
            val driverSnapshot = hardwareDriversRef.orderByChild("rfidUID").equalTo(rfidUID).get().await()

            if (!driverSnapshot.exists()) {
                println("No driver found with RFID: $rfidUID")
                return false
            }

            val driverData = driverSnapshot.children.firstOrNull()?.value as? Map<String, Any>
            val driverId = driverData?.get("driverId") as? String
            val driverName = driverData?.get("driverName") as? String

            if (driverId == null || driverName == null) {
                println("Invalid driver data for RFID: $rfidUID")
                return false
            }

            // Create contribution record
            val contributionRef = contributionsRef.push()
            val contributionId = contributionRef.key ?: return false

            val contribution = FirebaseContribution(
                id = contributionId,
                driverId = driverId,
                driverName = driverName,
                rfidUID = rfidUID,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                contributionType = "COIN_INSERTION",
                notes = "Coin inserted - driver can now receive bookings",
                deviceId = deviceId,
                verified = true
            )

            contributionRef.setValue(contribution).await()

            // Update driver's availability status
            val updates = mapOf(
                "drivers/$driverId/lastContribution" to System.currentTimeMillis(),
                "drivers/$driverId/canReceiveBookings" to true,
                "drivers/$driverId/contributionToday" to true
            )
            database.updateChildren(updates).await()

            println("✅ Coin insertion recorded successfully for driver: $driverName ($driverId)")

            // Trigger a notification or update to all listening apps
            notifyDriverStatusChange(driverId, true)

            true
        } catch (e: Exception) {
            println("❌ Error recording coin insertion: ${e.message}")
            false
        }
    }

    private suspend fun notifyDriverStatusChange(driverId: String, isOnline: Boolean) {
        try {
            // Update a status node that apps can listen to for real-time updates
            val statusUpdate = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "driverId" to driverId,
                "isOnline" to isOnline,
                "reason" to if (isOnline) "COIN_INSERTED" else "END_OF_DAY"
            )

            database.child("driverStatusUpdates").push().setValue(statusUpdate).await()
        } catch (e: Exception) {
            println("Error notifying driver status change: ${e.message}")
        }
    }

    // Method to listen for real-time driver status changes
    fun getDriverStatusUpdates(): Flow<Map<String, Any>> = callbackFlow {
        val listener = database.child("driverStatusUpdates")
            .orderByChild("timestamp")
            .limitToLast(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.lastOrNull()?.let { child ->
                        val statusUpdate = child.value as? Map<String, Any>
                        if (statusUpdate != null) {
                            trySend(statusUpdate)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
        awaitClose { database.child("driverStatusUpdates").removeEventListener(listener) }
    }

    suspend fun fixDriverVerificationStatus(phoneNumber: String): Boolean {
        return try {
            println("=== FIXING DRIVER VERIFICATION STATUS ===")
            println("Phone number: $phoneNumber")

            // Get the user ID from phone number index
            val userId = phoneNumberIndexRef.child(phoneNumber).get().await().getValue(String::class.java)

            if (userId != null) {
                println("Found user ID: $userId")

                // Get the current user record
                val currentUser = usersRef.child(userId).get().await().getValue(FirebaseUser::class.java)

                if (currentUser != null && currentUser.userType == "DRIVER") {
                    println("Current user: ${currentUser.name}, isVerified: ${currentUser.isVerified}")

                    // Update the isVerified field to true
                    usersRef.child(userId).child("isVerified").setValue(true).await()

                    // Add a small delay to ensure Firebase updates are propagated
                    kotlinx.coroutines.delay(500)

                    // Verify the update was successful
                    val updatedUser = usersRef.child(userId).get().await().getValue(FirebaseUser::class.java)
                    if (updatedUser != null && updatedUser.isVerified) {
                        println("Successfully updated and verified isVerified to true for driver: ${currentUser.name}")
                        true
                    } else {
                        println("Update failed - verification status still false")
                        false
                    }
                } else {
                    println("User not found or not a driver")
                    false
                }
            } else {
                println("No user ID found for phone number: $phoneNumber")
                false
            }
        } catch (e: Exception) {
            println("Error fixing driver verification status: ${e.message}")
            false
        }
    }

    suspend fun getDriverApplicationStatus(phoneNumber: String): String? {
        return try {
            println("=== CHECKING DRIVER APPLICATION STATUS ===")
            println("Phone number: $phoneNumber")

            // First, check if driver exists in the drivers table (new approach)
            val driversSnapshot = hardwareDriversRef.orderByChild("phoneNumber").equalTo(phoneNumber).get().await()

            if (driversSnapshot.exists()) {
                val driver = driversSnapshot.children.firstOrNull()?.getValue<Map<String, Any>>()
                if (driver != null) {
                    val isActive = driver["isActive"] as? Boolean ?: false
                    println("Found driver in drivers table for $phoneNumber: isActive = $isActive")
                    return if (isActive) "APPROVED" else "PENDING"
                }
            }

            // Fallback: Search for driver application by phone number in driverRegistrations (legacy approach)
            val snapshot = driverRegistrationsRef.orderByChild("phoneNumber").equalTo(phoneNumber).get().await()

            if (snapshot.exists()) {
                val application = snapshot.children.firstOrNull()?.getValue(FirebaseDriverRegistration::class.java)
                println("Found application for $phoneNumber: Status = ${application?.status}")
                application?.status
            } else {
                println("No driver application found for phone number: $phoneNumber")
                null
            }
        } catch (e: Exception) {
            println("Error checking driver application status: ${e.message}")
            null
        }
    }

    suspend fun getDriverContributionStatus(driverId: String): Boolean {
        return try {
            println("=== CHECKING DRIVER CONTRIBUTION STATUS ===")
            println("Driver ID: $driverId")

            // Check if driver has made any contributions today
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val snapshot = contributionsRef.orderByChild("driverId").equalTo(driverId).get().await()

            var hasContributedToday = false
            snapshot.children.forEach { child ->
                val contribution = child.getValue(FirebaseContribution::class.java)
                if (contribution != null && contribution.timestamp >= todayStart) {
                    hasContributedToday = true
                    return@forEach
                }
            }

            println("Driver $driverId contribution status: $hasContributedToday")
            hasContributedToday
        } catch (e: Exception) {
            println("Error checking driver contribution status: ${e.message}")
            false
        }
    }

    suspend fun isDriverInQueue(driverRFID: String): Boolean {
        return try {
            println("=== CHECKING IF DRIVER IS IN QUEUE ===")
            println("Driver RFID: $driverRFID")

            if (driverRFID.isEmpty()) {
                println("Driver RFID is empty, returning false")
                return false
            }

            // Check the queue table for this driver's RFID
            val queueRef = database.child("queue")
            val snapshot = queueRef.get().await()

            var isInQueue = false
            snapshot.children.forEach { child ->
                val queueDriverRFID = child.child("driverRFID").getValue(String::class.java) ?: ""
                val status = child.child("status").getValue(String::class.java) ?: ""

                if (queueDriverRFID == driverRFID && status == "waiting") {
                    isInQueue = true
                    println("✓ Driver $driverRFID found in queue with status: $status")
                    return@forEach
                }
            }

            println("Driver $driverRFID in queue status: $isInQueue")
            isInQueue
        } catch (e: Exception) {
            println("Error checking if driver is in queue: ${e.message}")
            false
        }
    }

    // Real-time observer for driver queue status
    fun observeDriverQueueStatus(driverRFID: String): Flow<Boolean> = callbackFlow {
        println("=== STARTING REAL-TIME QUEUE OBSERVER ===")
        println("Observing queue for driver RFID: $driverRFID")

        if (driverRFID.isEmpty()) {
            println("Driver RFID is empty, emitting false")
            trySend(false)
            close()
            return@callbackFlow
        }

        val queueRef = database.child("queue")
        val listener = queueRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("=== QUEUE DATA CHANGED ===")
                var isInQueue = false
                snapshot.children.forEach { child ->
                    val queueDriverRFID = child.child("driverRFID").getValue(String::class.java) ?: ""
                    val status = child.child("status").getValue(String::class.java) ?: ""

                    if (queueDriverRFID == driverRFID && status == "waiting") {
                        isInQueue = true
                        println("✓ Driver $driverRFID IS in queue with status: $status")
                        return@forEach
                    }
                }
                println("Queue status for $driverRFID: $isInQueue")
                trySend(isInQueue)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Queue observer cancelled: ${error.message}")
                trySend(false)
            }
        })

        awaitClose {
            println("Removing queue observer for driver $driverRFID")
            queueRef.removeEventListener(listener)
        }
    }

    fun observeDriverQueue(): Flow<List<QueueEntry>> = callbackFlow {
        println("=== STARTING REAL-TIME QUEUE LIST OBSERVER ===")

        val queueRef = database.child("queue")
        val listener = queueRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("=== QUEUE LIST DATA CHANGED ===")
                println("Queue snapshot exists: ${snapshot.exists()}")
                println("Queue children count: ${snapshot.childrenCount}")

                val queueEntries = mutableListOf<QueueEntry>()

                snapshot.children.forEachIndexed { index, child ->
                    try {
                        println("Processing queue entry: ${child.key}")
                        val driverRFID = child.child("driverRFID").getValue(String::class.java) ?: ""
                        val driverName = child.child("driverName").getValue(String::class.java) ?: ""
                        val status = child.child("status").getValue(String::class.java) ?: ""

                        // Handle timestamp - try queueTime first (Unix timestamp in seconds as string), then timestamp field
                        val queueTimeStr = child.child("queueTime").getValue(String::class.java)
                        val timestampStr = child.child("timestamp").getValue(String::class.java)

                        val timestamp = when {
                            queueTimeStr != null -> {
                                try {
                                    queueTimeStr.toLong() * 1000 // Convert seconds to milliseconds
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                            }
                            timestampStr != null -> {
                                // Try parsing the date string "2025-10-06 16:40:44"
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    sdf.parse(timestampStr)?.time ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                            }
                            else -> {
                                // Try getting as Long directly
                                child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            }
                        }

                        println("  driverRFID: $driverRFID")
                        println("  driverName: $driverName")
                        println("  status: $status")
                        println("  timestamp: $timestamp")

                        if (status == "waiting" && driverRFID.isNotEmpty()) {
                            val queueEntry = QueueEntry(
                                driverRFID = driverRFID,
                                driverName = driverName,
                                timestamp = timestamp,
                                position = index + 1
                            )
                            queueEntries.add(queueEntry)
                            println("✓ Added queue entry: $driverName ($driverRFID) at position ${index + 1}")
                        } else {
                            println("✗ Skipped entry - status: $status, RFID empty: ${driverRFID.isEmpty()}")
                        }
                    } catch (e: Exception) {
                        println("Error parsing queue entry ${child.key}: ${e.message}")
                        e.printStackTrace()
                    }
                }

                println("Total queue entries found: ${queueEntries.size}")
                trySend(queueEntries)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Queue list observer cancelled: ${error.message}")
                trySend(emptyList())
            }
        })

        awaitClose {
            println("Removing queue list observer")
            queueRef.removeEventListener(listener)
        }
    }

    suspend fun leaveQueue(driverRFID: String): Boolean {
        return try {
            println("=== LEAVING QUEUE ===")
            println("Driver RFID: $driverRFID")

            if (driverRFID.isEmpty()) {
                println("Driver RFID is empty, cannot leave queue")
                return false
            }

            // Find and delete the queue entry for this driver
            val queueRef = database.child("queue")
            val snapshot = queueRef.get().await()

            println("Found ${snapshot.childrenCount} entries in queue")

            var deleted = false
            for (child in snapshot.children) {
                val queueDriverRFID = child.child("driverRFID").getValue(String::class.java) ?: ""
                println("Checking queue entry: key=${child.key}, driverRFID=$queueDriverRFID")

                if (queueDriverRFID == driverRFID) {
                    val queueKey = child.key
                    if (queueKey != null) {
                        println("Found matching entry, deleting key: $queueKey")
                        queueRef.child(queueKey).removeValue().await()
                        println("✓ Driver $driverRFID removed from queue (key: $queueKey)")
                        deleted = true
                        break
                    }
                }
            }

            if (!deleted) {
                println("✗ Driver $driverRFID was not found in queue")
            }

            deleted
        } catch (e: Exception) {
            println("❌ Error leaving queue: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getDriverTodayStats(driverId: String): Triple<Int, Double, Double> {
        return try {
            println("=== GETTING DRIVER TODAY STATS ===")
            println("Driver ID: $driverId")

            // First get the driver's RFID
            val driverSnapshot = hardwareDriversRef.child(driverId).get().await()
            val driverRFID = driverSnapshot.child("rfidUID").getValue(String::class.java) ?: ""

            println("Driver RFID: $driverRFID")

            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Get all bookings
            val bookingsSnapshot = bookingsRef.get().await()

            var todayTrips = 0
            var todayEarnings = 0.0

            bookingsSnapshot.children.forEach { child ->
                val status = child.child("status").getValue(String::class.java) ?: ""
                val bookingDriverRFID = child.child("driverRFID").getValue(String::class.java) ?: ""
                val assignedDriverId = child.child("assignedDriverId").getValue(String::class.java) ?: ""
                val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                // Parse completionTime which may be stored as String or Number
                val completionTimeAny = child.child("completionTime").value
                val completionTime: Long = when (completionTimeAny) {
                    is Number -> completionTimeAny.toLong()
                    is String -> completionTimeAny.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val effectiveTime = if (completionTime > 0L) completionTime else timestamp

                // Check if this booking belongs to the driver (by RFID or driver ID)
                val isMyBooking = (bookingDriverRFID == driverRFID && driverRFID.isNotEmpty()) ||
                                 (assignedDriverId == driverRFID && driverRFID.isNotEmpty()) ||
                                 (assignedDriverId == driverId)

                if (isMyBooking && status == "COMPLETED" && effectiveTime >= todayStart) {
                    todayTrips++

                    // Get fare (prefer actualFare, fallback to estimatedFare)
                    val actualFare = when (val v = child.child("actualFare").value) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val estimatedFare = when (val v = child.child("estimatedFare").value) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val fare = if (actualFare > 0.0) actualFare else estimatedFare

                    todayEarnings += fare

                    println("Found completed trip: fare=₱$fare, effectiveTime=$effectiveTime")
                }
            }

            // Compute today's average rating for this driver
            var driverRating = -1.0 // Sentinel for "no ratings today"
            try {
                val ratingsSnapshot = ratingsRef.orderByChild("driverId").equalTo(driverId).get().await()
                var sumStars = 0.0
                var count = 0
                ratingsSnapshot.children.forEach { child ->
                    val tsAny = child.child("timestamp").value
                    val timestamp = when (tsAny) {
                        is Number -> tsAny.toLong()
                        is String -> tsAny.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    if (timestamp < todayStart) return@forEach

                    val starsAny = child.child("stars").value
                    val stars = when (starsAny) {
                        is Number -> starsAny.toInt()
                        is String -> starsAny.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val ratedBy = child.child("ratedBy").getValue(String::class.java) ?: ""

                    // Count only customer-submitted ratings with stars > 0
                    if (stars > 0 && ratedBy.equals("CUSTOMER", ignoreCase = true)) {
                        sumStars += stars
                        count++
                    }
                }
                if (count > 0) {
                    driverRating = sumStars / count
                }
            } catch (e: Exception) {
                println("Error computing today's ratings: ${e.message}")
            }

            println("Today's stats: $todayTrips trips, ₱$todayEarnings earnings, rating=${if (driverRating < 0) "--" else driverRating}")

            Triple(todayTrips, todayEarnings, driverRating)
        } catch (e: Exception) {
            println("Error getting driver today stats: ${e.message}")
            e.printStackTrace()
            Triple(0, 0.0, -1.0)
        }
    }

    // Emergency Management
    suspend fun createEmergencyAlert(alert: FirebaseEmergencyAlert): String? {
        return try {
            val alertRef = emergencyAlertsRef.push()
            val alertId = alertRef.key ?: return null
            val alertWithId = alert.copy(id = alertId)
            alertRef.setValue(alertWithId).await()
            alertId
        } catch (e: Exception) {
            println("Error creating emergency alert: ${e.message}")
            null
        }
    }

    // Rating Management
    suspend fun createRatingEntry(bookingId: String): Boolean {
        return try {
            println("=== CREATING RATING ENTRY ===")
            println("Booking ID: $bookingId")

            // Get booking details
            val bookingSnapshot = bookingsRef.child(bookingId).get().await()
            val booking = bookingSnapshot.getValue(FirebaseBooking::class.java)

            if (booking == null) {
                println("✗ Booking not found: $bookingId")
                return false
            }

            // Create rating entry with initial values
            val ratingRef = ratingsRef.push()
            val ratingId = ratingRef.key ?: return false

            val rating = FirebaseRating(
                id = ratingId,
                bookingId = bookingId,
                customerId = booking.customerId,
                customerName = booking.customerName,
                driverId = booking.assignedDriverId,
                driverName = booking.driverName,
                stars = 0, // Default, can be updated later
                feedback = "", // Default, can be updated later
                timestamp = System.currentTimeMillis(),
                ratedBy = "DRIVER"
            )

            ratingRef.setValue(rating).await()
            println("✓ Rating entry created: $ratingId for booking $bookingId")
            true
        } catch (e: Exception) {
            println("✗ Error creating rating entry: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateRating(bookingId: String, stars: Int, feedback: String): Boolean {
        return try {
            println("=== UPDATING RATING ===")
            println("Booking ID: $bookingId, Stars: $stars")

            // Find the rating entry for this booking
            val ratingSnapshot = ratingsRef.orderByChild("bookingId").equalTo(bookingId).get().await()

            if (!ratingSnapshot.exists()) {
                println("✗ No rating entry found for booking: $bookingId")
                return false
            }

            // Get the first (and should be only) rating entry
            val ratingKey = ratingSnapshot.children.firstOrNull()?.key

            if (ratingKey != null) {
                val updates = mapOf(
                    "ratings/$ratingKey/stars" to stars,
                    "ratings/$ratingKey/feedback" to feedback,
                    "ratings/$ratingKey/timestamp" to System.currentTimeMillis(),
                    "ratings/$ratingKey/ratedBy" to "CUSTOMER"
                )
                database.updateChildren(updates).await()
                println("✓ Rating updated successfully")
                true
            } else {
                println("✗ Rating key not found")
                false
            }
        } catch (e: Exception) {
            println("✗ Error updating rating: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Mark driver as arrived at pickup point
    suspend fun markArrivedAtPickup(bookingId: String): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "bookings/$bookingId/arrivedAtPickup" to true,
                "bookings/$bookingId/arrivedAtPickupTime" to System.currentTimeMillis(),
                "bookingIndex/$bookingId/arrivedAtPickup" to true,
                "bookingIndex/$bookingId/arrivedAtPickupTime" to System.currentTimeMillis()
            )

            database.updateChildren(updates).await()
            println("✓ Marked booking $bookingId as arrived at pickup")
            true
        } catch (e: Exception) {
            println("✗ Error marking arrived at pickup: ${e.message}")
            false
        }
    }

    // Report customer no-show
    suspend fun reportNoShow(bookingId: String): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "bookings/$bookingId/isNoShow" to true,
                "bookings/$bookingId/noShowReportedTime" to System.currentTimeMillis(),
                "bookings/$bookingId/status" to "NO_SHOW",
                "bookingIndex/$bookingId/isNoShow" to true,
                "bookingIndex/$bookingId/noShowReportedTime" to System.currentTimeMillis(),
                "bookingIndex/$bookingId/status" to "NO_SHOW"
            )

            database.updateChildren(updates).await()
            println("✓ Reported no-show for booking $bookingId")
            true
        } catch (e: Exception) {
            println("✗ Error reporting no-show: ${e.message}")
            false
        }
    }

    // Report missing RFID for a driver
    suspend fun reportMissingRfid(driverId: String): Boolean {
        return try {
            println("=== REPORTING MISSING RFID ===")
            println("Driver ID: $driverId")

            // Get driver details first
            val driverSnapshot = hardwareDriversRef.child(driverId).get().await()
            if (!driverSnapshot.exists()) {
                println("✗ Driver not found: $driverId")
                return false
            }

            val driverName = driverSnapshot.child("driverName").getValue(String::class.java) ?: ""
            // Support both rfidUID and rfidNumber fields
            val currentRFID = driverSnapshot.child("rfidUID").getValue(String::class.java)
                ?: driverSnapshot.child("rfidNumber").getValue(String::class.java)
                ?: ""

            if (currentRFID.isEmpty()) {
                println("✗ Driver has no RFID assigned")
                return false
            }

            println("Driver: $driverName, Current RFID: $currentRFID")

            // Add a record to the rfidChangeHistory
            val historyRef = rfidChangeHistoryRef.push()
            val historyId = historyRef.key ?: return false

            val historyEntry = mapOf(
                "id" to historyId,
                "driverId" to driverId,
                "driverName" to driverName,
                "oldRfidUID" to currentRFID,
                "newRfidUID" to "", // Empty because RFID is being unlinked
                "changeType" to "REPORTED_MISSING",
                "reason" to "Driver reported RFID as missing/lost",
                "changedBy" to driverId,
                "changedByName" to driverName,
                "timestamp" to System.currentTimeMillis(),
                "notes" to "Auto-unlinked due to missing report"
            )

            historyRef.setValue(historyEntry).await()

            // ⚠️ AUTO-UNLINK: Remove RFID from driver
            val updates = mutableMapOf<String, Any?>(
                // Clear both RFID fields
                "drivers/$driverId/rfidUID" to "",
                "drivers/$driverId/rfidNumber" to "",

                // Set missing flags
                "drivers/$driverId/rfidMissing" to true,
                "drivers/$driverId/rfidReported" to true,

                // Save old RFID for reference
                "drivers/$driverId/oldRfidUID" to currentRFID,
                "drivers/$driverId/rfidReportedMissingAt" to System.currentTimeMillis(),

                // Update assignment flags
                "drivers/$driverId/hasRfidAssigned" to false,
                "drivers/$driverId/needsRfidAssignment" to true,
                "drivers/$driverId/rfidStatus" to "missing",
                "drivers/$driverId/lastRfidChange" to System.currentTimeMillis(),

                // Remove from RFID index
                "rfidUIDIndex/$currentRFID" to null
            )

            database.updateChildren(updates).await()

            // Create admin notification
            val notificationRef = notificationsRef.push()
            val notificationId = notificationRef.key ?: ""

            val notification = mapOf(
                "id" to notificationId,
                "type" to "RFID_MISSING",
                "title" to "RFID Reported Missing",
                "message" to "$driverName (ID: $driverId) reported RFID $currentRFID as missing. RFID has been auto-unlinked.",
                "driverId" to driverId,
                "driverName" to driverName,
                "oldRfidUID" to currentRFID,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "priority" to "high",
                "actionRequired" to true
            )

            notificationRef.setValue(notification).await()

            println("✓ RFID reported missing and auto-unlinked successfully")
            println("✓ Admin notification created")
            println("✓ History entry created: $historyId")
            println("✓ Fields updated: rfidMissing=true, rfidReported=true, oldRfidUID=$currentRFID")
            true
        } catch (e: Exception) {
            println("✗ Error reporting missing RFID: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Get RFID change history for a driver
    fun getRfidChangeHistory(driverId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val listener = rfidChangeHistoryRef.orderByChild("driverId").equalTo(driverId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val history = snapshot.children.mapNotNull {
                        @Suppress("UNCHECKED_CAST")
                        it.value as? Map<String, Any>
                    }
                    trySend(history)
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            })
        awaitClose { rfidChangeHistoryRef.removeEventListener(listener) }
    }

    // Assign the first driver in queue to a booking immediately upon creation
    suspend fun matchBookingToFirstDriver(bookingId: String): Boolean {
        return try {
            // Helper to safely read a node as string regardless of backing type
            fun DataSnapshot.asSafeString(): String = when (val v = this.value) {
                is String -> v
                is Number -> v.toString()
                is Boolean -> v.toString()
                else -> ""
            }
            fun DataSnapshot.asSafeLongOrNull(): Long? = when (val v = this.value) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }

            // Ensure booking is still PENDING before attempting assignment
            val statusSnap = bookingsRef.child(bookingId).child("status").get().await()
            val currentStatus = statusSnap.asSafeString().ifBlank { "PENDING" }
            if (currentStatus != "PENDING") {
                println("Booking $bookingId is $currentStatus; skipping auto-match")
                return false
            }

            val queueRef = database.child("queue")
            val queueSnapshot = queueRef.get().await()
            if (!queueSnapshot.exists() || !queueSnapshot.hasChildren()) {
                println("No drivers in queue to match for booking $bookingId")
                return false
            }

            // Choose the earliest 'waiting' entry using the same rules as UI
            var selectedKey: String? = null
            var selectedTs: Long = Long.MAX_VALUE
            var selectedEntry: DataSnapshot? = null

            for (child in queueSnapshot.children) {
                val status = child.child("status").asSafeString()
                val driverRFID = child.child("driverRFID").asSafeString()
                if (!status.equals("waiting", ignoreCase = true) || driverRFID.isBlank()) continue

                // Determine timestamp: prefer queueTime (seconds) -> timestamp (string date) -> timestamp (long) -> key as long
                val queueTimeNode = child.child("queueTime")
                val timestampNode = child.child("timestamp")

                val ts: Long = when {
                    // queueTime stored as seconds (String or Number)
                    queueTimeNode.exists() -> {
                        queueTimeNode.asSafeLongOrNull()?.let { it * 1000 } ?: Long.MAX_VALUE
                    }
                    // timestamp as a formatted date string (yyyy-MM-dd HH:mm:ss)
                    timestampNode.value is String -> {
                        val s = timestampNode.asSafeString()
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            sdf.parse(s)?.time ?: Long.MAX_VALUE
                        } catch (_: Exception) { Long.MAX_VALUE }
                    }
                    // timestamp already numeric milliseconds
                    timestampNode.value is Number -> timestampNode.asSafeLongOrNull() ?: Long.MAX_VALUE
                    else -> child.key?.toLongOrNull() ?: Long.MAX_VALUE
                }

                if (ts < selectedTs) {
                    selectedTs = ts
                    selectedKey = child.key
                    selectedEntry = child
                }
            }

            if (selectedKey == null || selectedEntry == null) {
                println("No eligible 'waiting' driver entries found in queue")
                return false
            }

            // Atomically claim the selected entry
            val claimRef = queueRef.child(selectedKey).child("claimed")
            var claimed = false
            claimRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val v = mutableData.getValue(Boolean::class.java) ?: false
                    return if (!v) {
                        mutableData.value = true
                        Transaction.success(mutableData)
                    } else {
                        Transaction.abort()
                    }
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    claimed = committed && (currentData?.getValue(Boolean::class.java) == true)
                }
            })
            // Confirm claimed from DB (in case of async callback timing)
            val claimNow = claimRef.get().await().getValue(Boolean::class.java) ?: false
            if (!claimed && !claimNow) {
                println("Queue entry $selectedKey already claimed by another process; aborting match")
                return false
            }

            // Re-check booking still PENDING after claim
            val statusAfterClaim = bookingsRef.child(bookingId).child("status").get().await().asSafeString().ifBlank { "PENDING" }
            if (statusAfterClaim != "PENDING") {
                println("Booking $bookingId changed to $statusAfterClaim after claim; releasing entry")
                claimRef.setValue(false)
                return false
            }

            val driverName = selectedEntry.child("driverName").asSafeString()
            val todaNumber = selectedEntry.child("todaNumber").asSafeString()
            val driverRFID = selectedEntry.child("driverRFID").asSafeString()

            if (driverRFID.isBlank()) {
                println("Selected queue entry missing driverRFID; releasing claim and skipping match")
                claimRef.setValue(false)
                return false
            }

            // Map RFID to driverId (user/driver table) via rfidUIDIndex if available
            val driverId = try {
                database.child("rfidUIDIndex").child(driverRFID).get().await().getValue(String::class.java)
            } catch (_: Exception) { null }

            val updates = mutableMapOf<String, Any>(
                "bookings/$bookingId/driverRFID" to driverRFID,
                "bookings/$bookingId/driverName" to driverName,
                "bookings/$bookingId/assignedTricycleId" to todaNumber,
                "bookings/$bookingId/todaNumber" to todaNumber,
                "bookings/$bookingId/status" to "ACCEPTED",
                "bookingIndex/$bookingId/status" to "ACCEPTED",
                "bookingIndex/$bookingId/driverRFID" to driverRFID
            )

            // Prefer setting assignedDriverId to real driverId when resolvable
            if (!driverId.isNullOrBlank()) {
                updates["bookings/$bookingId/assignedDriverId"] = driverId
            } else {
                println("rfidUIDIndex mapping not found for RFID=$driverRFID; assignedDriverId will not be set")
            }

            database.updateChildren(updates).await()
            // Remove the queue entry now that it's assigned
            queueRef.child(selectedKey).removeValue().await()

            println("Matched booking $bookingId with RFID=$driverRFID, driverId=${driverId ?: "?"}, driverName=$driverName, todaNumber=$todaNumber")
            true
        } catch (e: Exception) {
            println("Error matching booking to first driver: ${e.message}")
            false
        }
    }

    // One-off fetch of a single booking by ID
    suspend fun getBookingByIdOnce(bookingId: String): FirebaseBooking? {
        return try {
            val snapshot = bookingsRef.child(bookingId).get().await()
            snapshot.getValue(FirebaseBooking::class.java) ?: run {
                // Fallback manual conversion in case of schema mismatches
                val data = snapshot.value as? Map<String, Any> ?: return null
                FirebaseBooking(
                    id = data["id"] as? String ?: snapshot.key ?: "",
                    customerId = data["customerId"] as? String ?: "",
                    customerName = data["customerName"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    isPhoneVerified = (data["isPhoneVerified"] as? Boolean)
                        ?: (data["phoneVerified"] as? Boolean) ?: false,
                    pickupLocation = data["pickupLocation"] as? String ?: "",
                    destination = data["destination"] as? String ?: "",
                    pickupCoordinates = (data["pickupCoordinates"] as? Map<String, Any>)?.mapValues {
                        when (val v = it.value) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                    } ?: emptyMap(),
                    dropoffCoordinates = (data["dropoffCoordinates"] as? Map<String, Any>)?.mapValues {
                        when (val v = it.value) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                    } ?: emptyMap(),
                    estimatedFare = when (val v = data["estimatedFare"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    },
                    actualFare = when (val v = data["actualFare"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    },
                    distance = when (val v = data["distance"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    },
                    status = data["status"] as? String ?: "PENDING,
                    timestamp = (data["timestamp"] as? Number)?.toLong()
                        ?: (data["timestamp"] as? String)?.toLongOrNull() ?: System.currentTimeMillis(),
                    assignedDriverId = data["assignedDriverId"] as? String ?: "",
                    assignedTricycleId = data["assignedTricycleId"] as? String ?: "",
                    driverName = data["driverName"] as? String ?: "",
                    driverRFID = data["driverRFID"] as? String ?: "",
                    todaNumber = data["todaNumber"] as? String ?: "",
                    verificationCode = data["verificationCode"] as? String ?: "",
                    paymentMethod = data["paymentMethod"] as? String ?: "CASH",
                    duration = (data["duration"] as? Number)?.toInt() ?: 0,
                    tripType = data["tripType"] as? String ?: "App Booking"
                )
            }
        } catch (e: Exception) {
            println("Error getBookingByIdOnce: ${e.message}")
            null
        }
    }
}
