package com.example.toda.repository

import com.example.toda.data.*
import com.example.toda.service.FirebaseRealtimeDatabaseService
import com.example.toda.service.FirebaseAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TODARepository @Inject constructor(
    private val firebaseService: FirebaseRealtimeDatabaseService,
    private val authService: FirebaseAuthService
) {

    // User Management
    suspend fun registerUser(
        phoneNumber: String,
        name: String,
        userType: UserType,
        password: String
    ): Result<String> {
        return try {
            // First create user in Firebase Auth
            val authResult = authService.createUserWithPhoneNumber(phoneNumber, password)

            authResult.fold(
                onSuccess = { userId ->
                    // Then create user profile in Realtime Database
                    val user = FirebaseUser(
                        id = userId,
                        phoneNumber = phoneNumber,
                        name = name,
                        userType = userType.name,
                        isVerified = true, // Since we're using Firebase Auth
                        registrationDate = System.currentTimeMillis()
                    )

                    val success = firebaseService.createUser(user)
                    if (success) {
                        Result.success(userId)
                    } else {
                        Result.failure(Exception("Failed to create user profile"))
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(phoneNumber: String, password: String): Result<FirebaseUser> {
        return try {
            println("=== CUSTOMER LOGIN DEBUG ===")
            println("Attempting login for phone: $phoneNumber")

            // First authenticate with Firebase Auth
            val authResult = authService.signInWithPhoneNumber(phoneNumber, password)

            authResult.fold(
                onSuccess = { userId ->
                    println("Firebase Auth login successful for userId: $userId")

                    // Then get the user profile from Realtime Database
                    val userProfile = firebaseService.getUserByPhoneNumber(phoneNumber)

                    if (userProfile != null) {
                        println("User profile found - Type: ${userProfile.userType}, Verified: ${userProfile.isVerified}")
                        Result.success(userProfile)
                    } else {
                        println("User authenticated but no profile found. Creating profile...")

                        // If auth is successful but no profile exists, create one
                        // This handles cases where the profile creation might have failed during registration
                        val newUser = FirebaseUser(
                            id = userId,
                            phoneNumber = phoneNumber,
                            name = "Customer", // Default name, should be updated in profile
                            userType = "PASSENGER",
                            isVerified = true,
                            registrationDate = System.currentTimeMillis()
                        )

                        val profileCreated = firebaseService.createUser(newUser)
                        if (profileCreated) {
                            println("Profile created successfully")
                            Result.success(newUser)
                        } else {
                            Result.failure(Exception("Authentication successful but failed to create user profile"))
                        }
                    }
                },
                onFailure = { error ->
                    println("Firebase Auth login failed: ${error.message}")

                    // Fallback: Check if user exists in database (for existing users before this fix)
                    val userProfile = firebaseService.getUserByPhoneNumber(phoneNumber)
                    if (userProfile != null) {
                        println("Found existing user profile, allowing login (legacy user)")
                        Result.success(userProfile)
                    } else {
                        Result.failure(Exception("Invalid credentials. Please check your phone number and password."))
                    }
                }
            )
        } catch (e: Exception) {
            println("Login error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<Unit> {
        return try {
            val firebaseProfile = FirebaseUserProfile(
                phoneNumber = profile.phoneNumber,
                name = profile.name,
                userType = profile.userType.name,
                address = profile.address,
                emergencyContact = profile.emergencyContact,
                profilePicture = profile.profilePicture,
                totalBookings = profile.totalBookings,
                completedBookings = profile.completedBookings,
                cancelledBookings = profile.cancelledBookings,
                trustScore = profile.trustScore,
                isBlocked = profile.isBlocked,
                lastBookingTime = profile.lastBookingTime,
                licenseNumber = profile.licenseNumber,
                licenseExpiry = profile.licenseExpiry,
                yearsOfExperience = profile.yearsOfExperience,
                rating = profile.rating,
                totalTrips = profile.totalTrips,
                earnings = profile.earnings
            )

            val success = firebaseService.updateUserProfile(userId, firebaseProfile)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserProfile(userId: String): Flow<UserProfile?> {
        return firebaseService.getUserProfile(userId).map { firebaseProfile ->
            firebaseProfile?.let { profile ->
                UserProfile(
                    phoneNumber = profile.phoneNumber,
                    name = profile.name,
                    userType = UserType.valueOf(profile.userType),
                    address = profile.address,
                    emergencyContact = profile.emergencyContact,
                    profilePicture = profile.profilePicture,
                    totalBookings = profile.totalBookings,
                    completedBookings = profile.completedBookings,
                    cancelledBookings = profile.cancelledBookings,
                    trustScore = profile.trustScore,
                    isBlocked = profile.isBlocked,
                    lastBookingTime = profile.lastBookingTime,
                    licenseNumber = profile.licenseNumber,
                    licenseExpiry = profile.licenseExpiry,
                    yearsOfExperience = profile.yearsOfExperience,
                    rating = profile.rating,
                    totalTrips = profile.totalTrips,
                    earnings = profile.earnings
                )
            }
        }
    }

    // Booking Management
    suspend fun createBooking(booking: Booking): Result<String> {
        return try {
            println("=== REPOSITORY CREATE BOOKING DEBUG ===")
            println("Input booking: $booking")

            val firebaseBooking = FirebaseBooking(
                customerId = booking.customerId,
                customerName = booking.customerName,
                phoneNumber = booking.phoneNumber,
                isPhoneVerified = booking.isPhoneVerified,
                pickupLocation = booking.pickupLocation,
                destination = booking.destination,
                pickupCoordinates = mapOf(
                    "lat" to booking.pickupGeoPoint.latitude,
                    "lng" to booking.pickupGeoPoint.longitude
                ),
                dropoffCoordinates = mapOf(
                    "lat" to booking.dropoffGeoPoint.latitude,
                    "lng" to booking.dropoffGeoPoint.longitude
                ),
                estimatedFare = booking.estimatedFare,
                actualFare = booking.estimatedFare, // Initially same as estimated, will be updated later
                distance = calculateDistance(booking.pickupGeoPoint, booking.dropoffGeoPoint),
                status = booking.status.name,
                timestamp = booking.timestamp,
                assignedDriverId = "", // Initially empty
                assignedTricycleId = booking.assignedTricycleId ?: "",
                verificationCode = booking.verificationCode,
                paymentMethod = "CASH", // Default payment method
                duration = 0 // Will be calculated during trip
            )

            println("Converted Firebase booking: $firebaseBooking")
            println("Calling firebaseService.createBooking...")

            val bookingId = firebaseService.createBooking(firebaseBooking)

            println("Firebase service returned booking ID: $bookingId")

            if (bookingId != null) {
                println("SUCCESS: Repository createBooking completed with ID: $bookingId")
                Result.success(bookingId)
            } else {
                println("ERROR: Firebase service returned null booking ID")
                Result.failure(Exception("Failed to create booking"))
            }
        } catch (e: Exception) {
            println("ERROR: Exception in repository createBooking: ${e.message}")
            println("Exception type: ${e::class.java.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Helper function to calculate distance between two points
    private fun calculateDistance(pickup: org.osmdroid.util.GeoPoint, dropoff: org.osmdroid.util.GeoPoint): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers

        val lat1Rad = Math.toRadians(pickup.latitude)
        val lat2Rad = Math.toRadians(dropoff.latitude)
        val deltaLatRad = Math.toRadians(dropoff.latitude - pickup.latitude)
        val deltaLngRad = Math.toRadians(dropoff.longitude - pickup.longitude)

        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c // Distance in kilometers
    }

    fun getActiveBookings(): Flow<List<Booking>> {
        return firebaseService.getActiveBookings().map { firebaseBookings ->
            println("=== REPOSITORY ACTIVE BOOKINGS DEBUG ===")
            println("Firebase bookings count: ${firebaseBookings.size}")

            firebaseBookings.mapNotNull { firebaseBooking ->
                try {
                    println("Processing Firebase booking: ${firebaseBooking.id} with status: ${firebaseBooking.status}")

                    val booking = Booking(
                        id = firebaseBooking.id,
                        customerId = firebaseBooking.customerId,
                        customerName = firebaseBooking.customerName,
                        phoneNumber = firebaseBooking.phoneNumber,
                        isPhoneVerified = firebaseBooking.isPhoneVerified,
                        pickupLocation = firebaseBooking.pickupLocation,
                        destination = firebaseBooking.destination,
                        pickupGeoPoint = GeoPoint(
                            firebaseBooking.pickupCoordinates["lat"] ?: 0.0,
                            firebaseBooking.pickupCoordinates["lng"] ?: 0.0
                        ),
                        dropoffGeoPoint = GeoPoint(
                            firebaseBooking.dropoffCoordinates["lat"] ?: 0.0,
                            firebaseBooking.dropoffCoordinates["lng"] ?: 0.0
                        ),
                        estimatedFare = firebaseBooking.estimatedFare,
                        status = try {
                            BookingStatus.valueOf(firebaseBooking.status)
                        } catch (e: IllegalArgumentException) {
                            println("ERROR: Unknown booking status: ${firebaseBooking.status}, defaulting to PENDING")
                            BookingStatus.PENDING
                        },
                        timestamp = firebaseBooking.timestamp,
                        assignedTricycleId = firebaseBooking.assignedTricycleId,
                        verificationCode = firebaseBooking.verificationCode,
                        // Add the missing driver fields from FirebaseBooking
                        driverName = firebaseBooking.driverName,
                        driverRFID = firebaseBooking.driverRFID,
                        todaNumber = firebaseBooking.todaNumber,
                        assignedDriverId = firebaseBooking.assignedDriverId
                    )

                    println("Successfully converted booking: ${booking.id} with status: ${booking.status}")
                    booking
                } catch (e: Exception) {
                    println("ERROR: Failed to convert Firebase booking ${firebaseBooking.id}: ${e.message}")
                    null
                }
            }
        }
    }

    suspend fun acceptBooking(bookingId: String, driverId: String): Result<Unit> {
        return try {
            // Use the enhanced method that auto-creates chat rooms
            val success = firebaseService.updateBookingStatusWithChatRoom(bookingId, "ACCEPTED", driverId)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to accept booking"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatusOnly(bookingId: String, status: String): Result<Unit> {
        return try {
            val success = firebaseService.updateBookingStatus(bookingId, status, null)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update booking status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeBooking(bookingId: String): Result<Unit> {
        return try {
            val success = firebaseService.updateBookingStatus(bookingId, "COMPLETED")
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to complete booking"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Driver Location Management
    suspend fun updateDriverLocation(
        driverId: String,
        tricycleId: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean,
        isAvailable: Boolean,
        currentBookingId: String? = null
    ): Result<Unit> {
        return try {
            val location = FirebaseDriverLocation(
                driverId = driverId,
                tricycleId = tricycleId,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis(),
                isOnline = isOnline,
                isAvailable = isAvailable,
                currentBookingId = currentBookingId
            )

            val success = firebaseService.updateDriverLocation(location)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAvailableDrivers(): Flow<List<AvailableDriver>> {
        return firebaseService.getAvailableDrivers().map { driversMap ->
            driversMap.map { (driverId, driverData) ->
                AvailableDriver(
                    driverId = driverId,
                    tricycleId = driverData["tricycleId"] as? String ?: "",
                    latitude = driverData["lat"] as? Double ?: 0.0,
                    longitude = driverData["lng"] as? Double ?: 0.0,
                    timestamp = driverData["timestamp"] as? Long ?: 0L
                )
            }
        }
    }

    // Driver Registration Management
    suspend fun submitDriverApplication(registration: DriverRegistration): Result<String> {
        return try {
            // Add driver directly to the drivers table instead of creating a registration
            val driverId = firebaseService.createDriverInDriversTable(
                driverName = registration.applicantName,
                todaNumber = registration.todaNumber.ifEmpty { "001" }, // Default TODA number if not provided
                phoneNumber = registration.phoneNumber,
                address = registration.address,
                emergencyContact = registration.emergencyContact,
                licenseNumber = registration.licenseNumber,
                licenseExpiry = registration.licenseExpiry,
                yearsOfExperience = registration.yearsOfExperience
            )

            if (driverId != null) {
                Result.success(driverId) // Return the driver ID
            } else {
                Result.failure(Exception("Failed to add driver to system"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // New method to get all drivers for admin management
    suspend fun getAllDrivers(): Result<List<DriverInfo>> {
        return try {
            val driversData = firebaseService.getAllDrivers()
            val drivers = driversData.map { driverData ->
                DriverInfo(
                    driverId = driverData["id"] as? String ?: "",
                    driverName = driverData["driverName"] as? String ?: "",
                    rfidUID = driverData["rfidUID"] as? String ?: "",
                    todaNumber = driverData["todaNumber"] as? String ?: "",
                    isActive = driverData["isActive"] as? Boolean ?: true,
                    registrationDate = System.currentTimeMillis(), // Convert string date if needed
                    phoneNumber = driverData["phoneNumber"] as? String ?: "",
                    address = driverData["address"] as? String ?: "",
                    emergencyContact = driverData["emergencyContact"] as? String ?: "",
                    licenseNumber = driverData["licenseNumber"] as? String ?: "",
                    licenseExpiry = driverData["licenseExpiry"] as? Long ?: 0L,
                    yearsOfExperience = driverData["yearsOfExperience"] as? Int ?: 0,
                    needsRfidAssignment = driverData["needsRfidAssignment"] as? Boolean ?: false
                )
            }
            Result.success(drivers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // New method to assign RFID to a driver
    suspend fun assignRfidToDriver(driverId: String, rfidUID: String): Result<Unit> {
        return try {
            val success = firebaseService.assignRfidToDriver(driverId, rfidUID)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to assign RFID to driver"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // Chat Management
    suspend fun createOrGetChatRoom(bookingId: String, customerId: String, customerName: String, driverId: String, driverName: String): Result<String> {
        return try {
            val chatRoomId = firebaseService.createOrGetChatRoom(bookingId, customerId, customerName, driverId, driverName)
            if (chatRoomId != null) {
                Result.success(chatRoomId)
            } else {
                Result.failure(Exception("Failed to create chat room"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatMessage(
        bookingId: String,
        senderId: String,
        senderName: String,
        receiverId: String,
        message: String
    ): Result<Unit> {
        return try {
            val chatMessage = FirebaseChatMessage(
                bookingId = bookingId,
                senderId = senderId,
                senderName = senderName,
                receiverId = receiverId,
                message = message,
                timestamp = System.currentTimeMillis(),
                messageType = "TEXT",
                isRead = false
            )

            val success = firebaseService.sendMessage(chatMessage)
            if (success) {
                // Update chat room's last message
                firebaseService.updateChatRoomLastMessage(bookingId, message)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getChatMessages(bookingId: String): Flow<List<ChatMessage>> {
        return firebaseService.getChatMessages(bookingId).map { firebaseMessages ->
            firebaseMessages.map { firebaseMessage ->
                ChatMessage(
                    id = firebaseMessage.id,
                    bookingId = firebaseMessage.bookingId,
                    senderId = firebaseMessage.senderId,
                    senderName = firebaseMessage.senderName,
                    receiverId = firebaseMessage.receiverId,
                    message = firebaseMessage.message,
                    timestamp = firebaseMessage.timestamp,
                    messageType = firebaseMessage.messageType,
                    isRead = firebaseMessage.isRead
                )
            }
        }
    }

    fun getChatRoom(bookingId: String): Flow<ChatRoom?> {
        return firebaseService.getChatRoom(bookingId).map { firebaseChatRoom ->
            firebaseChatRoom?.let { room ->
                ChatRoom(
                    id = room.id,
                    bookingId = room.bookingId,
                    customerId = room.customerId,
                    customerName = room.customerName,
                    driverId = room.driverId,
                    driverName = room.driverName,
                    createdAt = room.createdAt,
                    lastMessageTime = room.lastMessageTime,
                    lastMessage = room.lastMessage,
                    isActive = room.isActive
                )
            }
        }
    }

    fun getUserChatRooms(userId: String): Flow<List<ChatRoom>> {
        return firebaseService.getUserChatRooms(userId).map { firebaseChatRooms ->
            firebaseChatRooms.map { room ->
                ChatRoom(
                    id = room.id,
                    bookingId = room.bookingId,
                    customerId = room.customerId,
                    customerName = room.customerName,
                    driverId = room.driverId,
                    driverName = room.driverName,
                    createdAt = room.createdAt,
                    lastMessageTime = room.lastMessageTime,
                    lastMessage = room.lastMessage,
                    isActive = room.isActive
                )
            }
        }
    }

    // Emergency Management
    suspend fun createEmergencyAlert(
        userId: String,
        userName: String,
        bookingId: String?,
        latitude: Double,
        longitude: Double,
        message: String
    ): Result<String> {
        return try {
            val alert = FirebaseEmergencyAlert(
                userId = userId,
                userName = userName,
                bookingId = bookingId,
                location = mapOf("lat" to latitude, "lng" to longitude),
                message = message,
                timestamp = System.currentTimeMillis(),
                isResolved = false
            )

            val alertId = firebaseService.createEmergencyAlert(alert)
            if (alertId != null) {
                Result.success(alertId)
            } else {
                Result.failure(Exception("Failed to create emergency alert"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverInfo(driverId: String): Result<DriverInfo?> {
        return try {
            val driverData = firebaseService.getDriverById(driverId)
            if (driverData != null) {
                val driverInfo = DriverInfo(
                    driverId = driverId,
                    driverName = driverData["driverName"] as? String ?: "",
                    rfidUID = driverData["rfidUID"] as? String ?: "",
                    todaNumber = driverData["todaNumber"] as? String ?: "",
                    isActive = driverData["isActive"] as? Boolean ?: true,
                    registrationDate = driverData["registrationDate"] as? Long ?: System.currentTimeMillis()
                )
                Result.success(driverInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Fix for existing drivers who have isVerified = false
    suspend fun fixDriverVerificationStatus(phoneNumber: String): Result<Unit> {
        return try {
            val success = firebaseService.fixDriverVerificationStatus(phoneNumber)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fix driver verification status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // New method to check driver application status
    suspend fun getDriverApplicationStatus(phoneNumber: String): Result<String> {
        return try {
            val status = firebaseService.getDriverApplicationStatus(phoneNumber)
            if (status != null) {
                Result.success(status)
            } else {
                Result.failure(Exception("No driver application found for this phone number"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverContributionStatus(driverId: String): Result<Boolean> {
        return try {
            val hasContributed = firebaseService.getDriverContributionStatus(driverId)
            Result.success(hasContributed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverTodayStats(driverId: String): Result<Triple<Int, Double, Double>> {
        return try {
            val stats = firebaseService.getDriverTodayStats(driverId)
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverById(driverId: String): Result<Map<String, Any>> {
        return try {
            val driverData = firebaseService.getDriverById(driverId)
            if (driverData != null) {
                Result.success(driverData)
            } else {
                Result.failure(Exception("Driver not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper functions
    private fun generateUserId(): String = "user_${System.currentTimeMillis()}_${(1000..9999).random()}"
}

// Additional data classes for repository
data class AvailableDriver(
    val driverId: String,
    val tricycleId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

// Hardware integration data classes
data class HardwareDriver(
    val rfidUID: String,
    val name: String,
    val licenseNumber: String,
    val tricycleId: String,
    val todaNumber: String,
    val isRegistered: Boolean,
    val totalContributions: Double,
    val isActive: Boolean
)

data class DriverContribution(
    val driverId: String,
    val driverName: String,
    val amount: Double,
    val timestamp: Long,
    val source: String
)
