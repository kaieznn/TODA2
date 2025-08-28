package com.example.toda.data

import org.osmdroid.util.GeoPoint

data class Booking(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val isPhoneVerified: Boolean = false,
    val pickupLocation: String = "",
    val destination: String = "",
    val pickupGeoPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val dropoffGeoPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val estimatedFare: Double = 0.0,
    val status: BookingStatus = BookingStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val assignedTricycleId: String = "",
    val verificationCode: String = "",
    // Driver assignment fields from Firebase
    val driverName: String = "",
    val driverRFID: String = "",
    val todaNumber: String = "",
    val assignedDriverId: String = ""
)

enum class BookingStatus {
    PENDING,
    ACCEPTED,
    IN_PROGRESS,
    REJECTED,
    COMPLETED,
    CANCELLED
}

// Enhanced User types and roles
enum class UserType {
    PASSENGER,
    DRIVER,
    OPERATOR,
    TODA_ADMIN
}

enum class DriverStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_APPROVAL
}

// TODA Organization structure
data class TODAOrganization(
    val id: String = "",
    val name: String = "",
    val registrationNumber: String = "",
    val address: String = "",
    val contactNumber: String = "",
    val presidentName: String = "",
    val isActive: Boolean = true,
    val registrationDate: Long = System.currentTimeMillis(),
    val serviceArea: String = "Barangay 177, Caloocan City"
)

// Enhanced User model with TODA membership
data class User(
    val id: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val password: String = "", // In production, this should be hashed
    val userType: UserType = UserType.PASSENGER,
    val isVerified: Boolean = false,
    val registrationDate: Long = System.currentTimeMillis(),
    val profile: UserProfile? = null,
    // TODA membership fields
    val todaId: String? = null, // For drivers and operators
    val membershipNumber: String? = null,
    val membershipStatus: String = "ACTIVE", // ACTIVE, SUSPENDED, EXPIRED
    val membershipExpiry: Long? = null
)

// Enhanced UserProfile
data class UserProfile(
    val phoneNumber: String,
    val name: String,
    val userType: UserType = UserType.PASSENGER,
    // Common fields
    val address: String = "",
    val emergencyContact: String = "",
    val profilePicture: String? = null,
    // Passenger specific
    val totalBookings: Int = 0,
    val completedBookings: Int = 0,
    val cancelledBookings: Int = 0,
    val trustScore: Double = 100.0,
    val isBlocked: Boolean = false,
    val lastBookingTime: Long = 0,
    // Driver specific
    val licenseNumber: String? = null,
    val licenseExpiry: Long? = null,
    val yearsOfExperience: Int = 0,
    val rating: Double = 5.0,
    val totalTrips: Int = 0,
    val earnings: Double = 0.0
)

// Tricycle with multiple drivers support
data class Tricycle(
    val id: String = "",
    val plateNumber: String = "",
    val todaNumber: String = "", // TODA registration number for the tricycle
    val bodyNumber: String = "",
    val engineNumber: String = "",
    val registrationDate: Long = System.currentTimeMillis(),
    val ownerName: String = "",
    val ownerContact: String = "",
    val isActive: Boolean = true,
    val primaryDriverId: String = "", // Main driver
    val registeredDrivers: List<String> = emptyList(), // List of driver IDs who can operate this tricycle
    val currentDriverId: String? = null, // Currently operating driver
    val lastMaintenanceDate: Long = 0,
    val nextMaintenanceDate: Long = 0,
    val todaOrganizationId: String = ""
)

// Driver registration and management
data class DriverRegistration(
    val id: String = "",
    val applicantName: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val licenseNumber: String = "",
    val licenseExpiry: Long = 0,
    val yearsOfExperience: Int = 0,
    val tricycleId: String = "", // Tricycle they want to register for
    val todaNumber: String = "",
    val applicationDate: Long = System.currentTimeMillis(),
    val status: DriverApplicationStatus = DriverApplicationStatus.PENDING,
    val approvedBy: String? = null,
    val approvalDate: Long? = null,
    val rejectionReason: String? = null,
    // Required documents
    val hasValidLicense: Boolean = false,
    val hasBarangayClearance: Boolean = false,
    val hasPoliceClearance: Boolean = false,
    val hasMedicalCertificate: Boolean = false,
    val hasDriverTrainingCertificate: Boolean = false
)

enum class DriverApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    UNDER_REVIEW,
    REQUIRES_DOCUMENTS
}

// Enhanced passenger registration
data class PassengerRegistration(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val emergencyContactName: String = "",
    val dateOfBirth: Long? = null,
    val gender: String = "",
    val occupation: String = "",
    val registrationDate: Long = System.currentTimeMillis(),
    val isPhoneVerified: Boolean = false,
    val agreesToTerms: Boolean = false,
    val notificationPreferences: NotificationPreferences = NotificationPreferences()
)

// Driver information for admin management
data class DriverInfo(
    val driverId: String = "",
    val driverName: String = "",
    val rfidUID: String = "",
    val todaNumber: String = "",
    val isActive: Boolean = true,
    val registrationDate: Long = System.currentTimeMillis(),
    val phoneNumber: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val licenseNumber: String = "",
    val licenseExpiry: Long = 0,
    val yearsOfExperience: Int = 0,
    val needsRfidAssignment: Boolean = false
)

data class NotificationPreferences(
    val smsNotifications: Boolean = true,
    val bookingUpdates: Boolean = true,
    val promotionalMessages: Boolean = false,
    val emergencyAlerts: Boolean = true
)

// TODA Membership management
data class TODAMembership(
    val id: String = "",
    val memberId: String = "", // User ID
    val todaOrganizationId: String = "",
    val membershipNumber: String = "",
    val membershipType: MembershipType = MembershipType.DRIVER,
    val status: MembershipStatus = MembershipStatus.ACTIVE,
    val joinDate: Long = System.currentTimeMillis(),
    val renewalDate: Long = 0,
    val expiryDate: Long = 0,
    val monthlyDues: Double = 0.0,
    val lastPaymentDate: Long = 0,
    val isGoodStanding: Boolean = true
)

enum class MembershipType {
    DRIVER,
    OPERATOR,
    OFFICER,
    HONORARY
}

enum class MembershipStatus {
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    TERMINATED
}

data class AuthState(
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val error: String? = null
)

data class DriverTracking(
    val driverId: String,
    val driverName: String,
    val currentLocation: GeoPoint,
    val heading: Float,
    val speed: Float,
    val estimatedArrival: Long,
    val distanceToPickup: Double,
    val isMoving: Boolean,
    val lastUpdated: Long
)

data class CustomerLocation(
    val customerId: String,
    val location: GeoPoint,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float = 0f,
    val bearing: Float = 0f
)

data class NotificationState(
    val isOperatorOnline: Boolean = false,
    val hasPermission: Boolean = false,
    val lastNotificationTime: Long = 0
)

data class AppState(
    val bookings: List<Booking> = emptyList(),
    val selectedUserType: String? = null,
    val currentUser: User? = null,
    val notificationState: NotificationState = NotificationState()
)

enum class BookingValidationResult {
    VALID,
    PHONE_NOT_VERIFIED,
    TOO_MANY_BOOKINGS,
    TOO_SOON_SINCE_LAST_BOOKING,
    HIGH_CANCELLATION_RATE,
    LOW_TRUST_SCORE,
    USER_BLOCKED
}

// Security configuration for booking validation
data class SecurityConfig(
    val maxBookingsPerDay: Int = 3,
    val minTimeBetweenBookings: Long = 30 * 60 * 1000, // 30 minutes in milliseconds
    val maxCancellationRate: Double = 0.3, // 30%
    val minTrustScore: Double = 50.0
)

// Operator profile for TODA operators
data class OperatorProfile(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isVerified: Boolean = false,
    val isActive: Boolean = true,
    val callLogs: List<CallLog> = emptyList()
)

data class CallLog(
    val customerPhone: String,
    val timestamp: Long = System.currentTimeMillis(),
    val purpose: String // "booking_confirmation", "pickup_coordination", etc.
)

// Chat System Models
data class ChatMessage(
    val id: String = "",
    val bookingId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: String = "TEXT",
    val isRead: Boolean = false
)

data class ChatRoom(
    val id: String = "",
    val bookingId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val isActive: Boolean = true
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val isTyping: Boolean = false,
    val typingUser: String? = null,
    val unreadCount: Int = 0,
    val lastMessageTime: Long = 0
)

data class ActiveChat(
    val bookingId: String = "",
    val participants: List<ChatParticipant> = emptyList(),
    val isActive: Boolean = true,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatParticipant(
    val userId: String = "",
    val name: String = "",
    val userType: UserType = UserType.PASSENGER,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val hasUnreadMessages: Boolean = false
)

// Message Type Enum for Chat System
enum class MessageType {
    TEXT,
    IMAGE,
    LOCATION,
    SYSTEM,
    EMERGENCY
}
