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

    // Hardware Integration Methods (ESP32 Support)
    private val hardwareDriversRef = database.child("drivers")
    private val driverQueueRef = database.child("driverQueue")
    private val contributionsRef = database.child("contributions")
    private val activeTripsRef = database.child("activeTrips")
    private val systemStatusRef = database.child("systemStatus")

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
                    println("Raw data: ${child.value}")

                    try {
                        // Try direct conversion first
                        val booking = child.getValue(FirebaseBooking::class.java)
                        if (booking != null) {
                            println("Direct conversion SUCCESS for ${booking.id}")
                            println("Booking status: ${booking.status}")
                            println("Customer ID: ${booking.customerId}")

                            // Include PENDING, ACCEPTED, and IN_PROGRESS bookings as "active"
                            if (booking.status in listOf("PENDING", "ACCEPTED", "IN_PROGRESS")) {
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

                                if (manualBooking.status in listOf("PENDING", "ACCEPTED", "IN_PROGRESS")) {
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
                    println("Active booking: ${booking.id} - Status: ${booking.status} - Customer: ${booking.customerId}")
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
                todaNumber = data["todaNumber"] as? String ?: ""
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
        yearsOfExperience: Int
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
            val updates = mapOf(
                "drivers/$driverId/rfidUID" to rfidUID,
                "drivers/$driverId/needsRfidAssignment" to false
            )

            database.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            println("Error assigning RFID to driver: ${e.message}")
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
            }

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

    // Helper Methods
    private suspend fun getNextQueuePosition(): Int {
        return try {
            val snapshot = driverQueueRef.get().await()
            snapshot.childrenCount.toInt() + 1
        } catch (e: Exception) {
            1
        }
    }

    private suspend fun updateAvailableDriverQueueStatus(driverId: String, inQueue: Boolean, position: Int) {
        try {
            val updates = mapOf(
                "availableDrivers/$driverId/isInPhysicalQueue" to inQueue,
                "availableDrivers/$driverId/queuePosition" to position
            )
            database.updateChildren(updates).await()
        } catch (e: Exception) {
            // Handle error
        }
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

    suspend fun getDriverTodayStats(driverId: String): Triple<Int, Double, Double> {
        return try {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Get completed bookings for today
            val bookingsSnapshot = bookingsRef.orderByChild("assignedDriverId").equalTo(driverId).get().await()

            var todayTrips = 0
            var todayEarnings = 0.0

            bookingsSnapshot.children.forEach { child ->
                val booking = child.getValue(FirebaseBooking::class.java)
                if (booking != null &&
                    booking.status == "COMPLETED" &&
                    booking.timestamp >= todayStart) {
                    todayTrips++
                    todayEarnings += booking.actualFare
                }
            }

            // Get driver rating (this should come from user profile or rating system)
            val driverRating = 5.0 // Default rating, replace with actual rating logic

            Triple(todayTrips, todayEarnings, driverRating)
        } catch (e: Exception) {
            println("Error getting driver today stats: ${e.message}")
            Triple(0, 0.0, 5.0)
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
}
