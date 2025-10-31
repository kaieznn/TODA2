package com.example.toda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toda.data.*
import com.example.toda.repository.TODARepository
import com.example.toda.repository.AvailableDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@HiltViewModel
class EnhancedBookingViewModel @Inject constructor(
    private val repository: TODARepository
) : ViewModel() {

    private val _bookingState = MutableStateFlow(BookingState())
    val bookingState = _bookingState.asStateFlow()

    private val _availableDrivers = MutableStateFlow<List<AvailableDriver>>(emptyList())
    val availableDrivers = _availableDrivers.asStateFlow()

    private val _activeBookings = MutableStateFlow<List<Booking>>(emptyList())
    val activeBookings = _activeBookings.asStateFlow()

    private val _queueList = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queueList = _queueList.asStateFlow()

    init {
        // Observe available drivers in real-time
        viewModelScope.launch {
            repository.getAvailableDrivers().collect { drivers ->
                _availableDrivers.value = drivers
            }
        }

        // Observe active bookings in real-time (for operators)
        viewModelScope.launch {
            repository.getActiveBookings().collect { bookings ->
                _activeBookings.value = bookings
            }
        }

        // Observe driver queue in real-time
        viewModelScope.launch {
            repository.observeDriverQueue().collect { queueEntries ->
                _queueList.value = queueEntries
            }
        }
    }

    fun createBooking(
        customerId: String,
        customerName: String,
        phoneNumber: String,
        pickupLocation: String,
        destination: String,
        pickupGeoPoint: GeoPoint,
        dropoffGeoPoint: GeoPoint,
        estimatedFare: Double
    ) {
        viewModelScope.launch {
            println("=== VIEWMODEL CREATE BOOKING DEBUG ===")
            println("Input parameters:")
            println("  customerId: $customerId")
            println("  customerName: $customerName")
            println("  phoneNumber: $phoneNumber")
            println("  pickupLocation: $pickupLocation")
            println("  destination: $destination")
            println("  pickupGeoPoint: $pickupGeoPoint")
            println("  dropoffGeoPoint: $dropoffGeoPoint")
            println("  estimatedFare: $estimatedFare")

            _bookingState.value = _bookingState.value.copy(isLoading = true)

            val booking = Booking(
                customerId = customerId,
                customerName = customerName,
                phoneNumber = phoneNumber,
                isPhoneVerified = true, // Assume verified
                pickupLocation = pickupLocation,
                destination = destination,
                pickupGeoPoint = pickupGeoPoint,
                dropoffGeoPoint = dropoffGeoPoint,
                estimatedFare = estimatedFare,
                status = BookingStatus.PENDING,
                timestamp = System.currentTimeMillis(),
                verificationCode = generateVerificationCode()
            )

            println("Created booking object: $booking")
            println("Calling repository.createBooking...")

            repository.createBooking(booking).fold(
                onSuccess = { bookingId ->
                    println("SUCCESS: ViewModel received booking ID: $bookingId")
                    _bookingState.value = _bookingState.value.copy(
                        isLoading = false,
                        currentBookingId = bookingId,
                        message = "Booking created successfully! Waiting for driver acceptance."
                    )
                },
                onFailure = { error ->
                    println("ERROR: ViewModel received error: ${error.message}")
                    println("Error type: ${error::class.java.simpleName}")
                    error.printStackTrace()
                    _bookingState.value = _bookingState.value.copy(
                        isLoading = false,
                        error = "Failed to create booking: ${error.message}"
                    )
                }
            )
        }
    }

    fun acceptBooking(bookingId: String, driverId: String) {
        viewModelScope.launch {
            repository.acceptBooking(bookingId, driverId).fold(
                onSuccess = {
                    _bookingState.value = _bookingState.value.copy(
                        message = "Booking accepted successfully!"
                    )
                    // Create chat room after accepting booking
                    createChatRoomForBooking(bookingId, driverId)
                },
                onFailure = { error ->
                    _bookingState.value = _bookingState.value.copy(
                        error = "Failed to accept booking: ${error.message}"
                    )
                }
            )
        }
    }

    fun updateBookingStatusOnly(bookingId: String, status: String) {
        viewModelScope.launch {
            repository.updateBookingStatusOnly(bookingId, status).fold(
                onSuccess = {
                    _bookingState.value = _bookingState.value.copy(
                        message = "Booking status updated to $status successfully!"
                    )

                    // Create rating entry when trip is completed
                    if (status == "COMPLETED") {
                        repository.createRatingEntry(bookingId).fold(
                            onSuccess = {
                                println("✓ Rating entry created for booking: $bookingId")
                            },
                            onFailure = { error ->
                                println("✗ Failed to create rating entry: ${error.message}")
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _bookingState.value = _bookingState.value.copy(
                        error = "Failed to update booking status: ${error.message}"
                    )
                }
            )
        }
    }

    // Mark driver as arrived at pickup point
    suspend fun markArrivedAtPickup(bookingId: String): Result<Unit> {
        return repository.markArrivedAtPickup(bookingId)
    }

    // Report customer no-show
    suspend fun reportNoShow(bookingId: String): Result<Unit> {
        return repository.reportNoShow(bookingId)
    }

    fun updateDriverLocation(
        driverId: String,
        tricycleId: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean,
        isAvailable: Boolean
    ) {
        viewModelScope.launch {
            repository.updateDriverLocation(
                driverId, tricycleId, latitude, longitude, isOnline, isAvailable
            ).fold(
                onSuccess = {
                    // Location updated successfully
                },
                onFailure = { error ->
                    _bookingState.value = _bookingState.value.copy(
                        error = "Failed to update location: ${error.message}"
                    )
                }
            )
        }
    }

    fun sendMessage(
        bookingId: String,
        senderId: String,
        senderName: String,
        receiverId: String,
        message: String
    ) {
        viewModelScope.launch {
            println("🎯 ViewModel: Sending message")
            println("   BookingId: $bookingId")
            println("   From: $senderName ($senderId)")
            println("   To: $receiverId")
            println("   Message: $message")

            val result = repository.sendChatMessage(bookingId, senderId, senderName, receiverId, message)
            result.fold(
                onSuccess = {
                    println("✅ ViewModel: Message sent successfully")
                },
                onFailure = { error ->
                    println("❌ ViewModel: Failed to send message: ${error.message}")
                }
            )
        }
    }

    fun createEmergencyAlert(
        userId: String,
        userName: String,
        bookingId: String?,
        latitude: Double,
        longitude: Double,
        message: String
    ) {
        viewModelScope.launch {
            repository.createEmergencyAlert(
                userId = userId,
                userName = userName,
                bookingId = bookingId,
                latitude = latitude,
                longitude = longitude,
                message = message
            ).fold(
                onSuccess = { alertId ->
                    _bookingState.value = _bookingState.value.copy(
                        message = "Emergency alert sent! Alert ID: $alertId"
                    )
                },
                onFailure = { error ->
                    _bookingState.value = _bookingState.value.copy(
                        error = "Failed to send emergency alert: ${error.message}"
                    )
                }
            )
        }
    }

    // Driver-specific methods for DriverInterface
    suspend fun getDriverById(driverId: String): Result<Map<String, Any>> {
        return repository.getDriverById(driverId)
    }

    suspend fun getDriverContributionStatus(driverId: String): Result<Boolean> {
        return repository.getDriverContributionStatus(driverId)
    }

    suspend fun isDriverInQueue(driverRFID: String): Result<Boolean> {
        return repository.isDriverInQueue(driverRFID)
    }

    fun observeDriverQueueStatus(driverRFID: String): Flow<Boolean> {
        return repository.observeDriverQueueStatus(driverRFID)
    }

    suspend fun leaveQueue(driverRFID: String): Result<Boolean> {
        return repository.leaveQueue(driverRFID)
    }

    suspend fun getDriverTodayStats(driverId: String): Result<Triple<Int, Double, Double>> {
        return repository.getDriverTodayStats(driverId)
    }

    // RFID Management functions
    suspend fun reportMissingRfid(driverId: String, reason: String): Result<Unit> {
        return repository.reportMissingRfid(driverId, reason)
    }

    suspend fun getRfidChangeHistory(driverId: String): Result<List<RfidChangeHistory>> {
        return repository.getRfidChangeHistory(driverId)
    }

    // Rating submission function
    suspend fun submitRating(bookingId: String, stars: Int, feedback: String): Result<Unit> {
        return repository.updateRating(bookingId, stars, feedback)
    }

    // Enhanced Chat methods
    suspend fun sendChatMessage(
        bookingId: String,
        senderId: String,
        senderName: String,
        receiverId: String,
        message: String
    ): Result<Unit> {
        return repository.sendChatMessage(bookingId, senderId, senderName, receiverId, message)
    }

    fun getChatMessages(bookingId: String): Flow<List<ChatMessage>> {
        return repository.getChatMessages(bookingId)
    }

    fun getChatRoom(bookingId: String): Flow<ChatRoom?> {
        return repository.getChatRoom(bookingId)
    }

    fun getUserChatRooms(userId: String): Flow<List<ChatRoom>> {
        return repository.getUserChatRooms(userId)
    }

    // Add method to fetch user profile data including discount information
    fun getUserProfile(userId: String): Flow<UserProfile?> {
        return repository.getUserProfile(userId)
    }

    suspend fun createOrGetChatRoom(
        bookingId: String,
        customerId: String,
        customerName: String,
        driverId: String,
        driverName: String
    ): Result<String> {
        return repository.createOrGetChatRoom(bookingId, customerId, customerName, driverId, driverName)
    }

    fun clearMessage() {
        _bookingState.value = _bookingState.value.copy(message = null)
    }

    fun clearError() {
        _bookingState.value = _bookingState.value.copy(error = null)
    }

    private fun generateVerificationCode(): String {
        return (1000..9999).random().toString()
    }

    private fun createChatRoomForBooking(bookingId: String, driverId: String) {
        viewModelScope.launch {
            try {
                // Get booking details to create chat room
                val booking = _activeBookings.value.find { it.id == bookingId }
                if (booking != null) {
                    // Get driver details
                    val driverResult = getDriverById(driverId)
                    driverResult.fold(
                        onSuccess = { driverData ->
                            val driverName = driverData["name"] as? String ?: "Driver"

                            repository.createOrGetChatRoom(
                                bookingId = bookingId,
                                customerId = booking.customerId,
                                customerName = booking.customerName,
                                driverId = driverId,
                                driverName = driverName
                            ).fold(
                                onSuccess = { chatRoomId ->
                                    println("Chat room created successfully: $chatRoomId")
                                },
                                onFailure = { error ->
                                    println("Failed to create chat room: ${error.message}")
                                }
                            )
                        },
                        onFailure = { error ->
                            println("Failed to get driver details: ${error.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                println("Error creating chat room: ${e.message}")
            }
        }
    }

    // Driver Contributions Management
    suspend fun getDriverContributions(driverId: String): Result<List<FirebaseContribution>> {
        return try {
            val contributions = repository.getDriverContributions(driverId)
            Result.success(contributions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverTodayContributions(driverId: String): Result<List<FirebaseContribution>> {
        return try {
            val contributions = repository.getDriverTodayContributions(driverId)
            Result.success(contributions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get driver contribution summary with all statistics
    suspend fun getDriverContributionSummary(driverId: String): Result<ContributionSummary> {
        return try {
            val summary = repository.getDriverContributionSummary(driverId)
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Stop polling for a specific booking (called when manually cancelling or timeout)
    fun stopBookingPolling(bookingId: String) {
        // This is a placeholder method that can be used to stop any active polling
        // for a specific booking. Currently, we rely on Flow collectors being cancelled
        // when the composable is disposed, but this provides an explicit way to stop
        // polling if needed in the future.
        viewModelScope.launch {
            println("Stopping polling for booking: $bookingId")
            // Any cleanup logic can be added here if needed
        }
    }
}

data class BookingState(
    val isLoading: Boolean = false,
    val currentBookingId: String? = null,
    val message: String? = null,
    val error: String? = null
)
