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
                },
                onFailure = { error ->
                    _bookingState.value = _bookingState.value.copy(
                        error = "Failed to update booking status: ${error.message}"
                    )
                }
            )
        }
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

    suspend fun getDriverTodayStats(driverId: String): Result<Triple<Int, Double, Double>> {
        return repository.getDriverTodayStats(driverId)
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
}

data class BookingState(
    val isLoading: Boolean = false,
    val currentBookingId: String? = null,
    val message: String? = null,
    val error: String? = null
)
