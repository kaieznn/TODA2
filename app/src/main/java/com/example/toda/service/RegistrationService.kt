package com.example.toda.service

import com.example.toda.data.*
import kotlinx.coroutines.delay
import java.util.*

class RegistrationService {

    // Mock databases - in production, these would be proper databases
    private val registrations = mutableListOf<Any>()
    private val users = mutableListOf<User>()
    private val tricycles = mutableListOf<Tricycle>()
    private val todaOrganizations = mutableListOf<TODAOrganization>()
    private val todaMemberships = mutableListOf<TODAMembership>()

    init {
        // Initialize with sample TODA organization
        val sampleTODA = TODAOrganization(
            id = "toda_brgy177_001",
            name = "TODA Barangay 177",
            registrationNumber = "TODA-177-2024",
            address = "Barangay 177, Caloocan City",
            contactNumber = "09123456789",
            presidentName = "Juan dela Cruz"
        )
        todaOrganizations.add(sampleTODA)

        // Initialize with sample tricycles
        initializeSampleTricycles()
    }

    private fun initializeSampleTricycles() {
        val sampleTricycles = listOf(
            Tricycle(
                id = "tricycle_001",
                plateNumber = "ABC-123",
                todaNumber = "177-001",
                bodyNumber = "BODY001",
                engineNumber = "ENG001",
                ownerName = "Pedro Santos",
                ownerContact = "09111111111",
                todaOrganizationId = "toda_brgy177_001",
                registeredDrivers = listOf("driver_001") // Already has one driver
            ),
            Tricycle(
                id = "tricycle_002",
                plateNumber = "DEF-456",
                todaNumber = "177-002",
                bodyNumber = "BODY002",
                engineNumber = "ENG002",
                ownerName = "Maria Garcia",
                ownerContact = "09222222222",
                todaOrganizationId = "toda_brgy177_001",
                registeredDrivers = emptyList() // Available for new drivers
            ),
            Tricycle(
                id = "tricycle_003",
                plateNumber = "GHI-789",
                todaNumber = "177-003",
                bodyNumber = "BODY003",
                engineNumber = "ENG003",
                ownerName = "Jose Rodriguez",
                ownerContact = "09333333333",
                todaOrganizationId = "toda_brgy177_001",
                registeredDrivers = listOf("driver_002", "driver_003") // Has multiple drivers
            )
        )
        tricycles.addAll(sampleTricycles)
    }

    // Passenger Registration
    suspend fun registerPassenger(
        registration: PassengerRegistration,
        password: String
    ): RegistrationResult {
        delay(1000) // Simulate network delay

        // Check if phone number already exists
        if (users.any { it.phoneNumber == registration.phoneNumber }) {
            return RegistrationResult.Error("Phone number already registered")
        }

        try {
            // Create user account
            val user = User(
                id = UUID.randomUUID().toString(),
                phoneNumber = registration.phoneNumber,
                name = registration.name,
                password = password, // In production, hash this
                userType = UserType.PASSENGER,
                isVerified = false, // Will be verified via SMS
                profile = UserProfile(
                    phoneNumber = registration.phoneNumber,
                    name = registration.name,
                    userType = UserType.PASSENGER,
                    address = registration.address,
                    emergencyContact = registration.emergencyContact
                )
            )

            users.add(user)
            registrations.add(registration)

            // Generate verification code
            val verificationCode = generateVerificationCode()

            return RegistrationResult.Success(
                user = user,
                verificationCode = verificationCode,
                message = "Registration successful! Please verify your phone number."
            )

        } catch (e: Exception) {
            return RegistrationResult.Error("Registration failed: ${e.message}")
        }
    }

    // Driver Registration
    suspend fun registerDriver(
        registration: DriverRegistration,
        password: String
    ): RegistrationResult {
        delay(1500) // Simulate network delay

        // Check if phone number already exists
        if (users.any { it.phoneNumber == registration.phoneNumber }) {
            return RegistrationResult.Error("Phone number already registered")
        }

        // Validate TODA membership
        if (!isValidTODANumber(registration.todaNumber)) {
            return RegistrationResult.Error("Invalid TODA membership number")
        }

        // Check if tricycle exists and is available
        val tricycle = tricycles.find { it.id == registration.tricycleId }
        if (tricycle == null) {
            return RegistrationResult.Error("Selected tricycle not found")
        }

        try {
            // Create user account with pending approval
            val user = User(
                id = UUID.randomUUID().toString(),
                phoneNumber = registration.phoneNumber,
                name = registration.applicantName,
                password = password,
                userType = UserType.DRIVER,
                isVerified = false,
                todaId = "toda_brgy177_001",
                membershipNumber = registration.todaNumber,
                membershipStatus = "PENDING_APPROVAL",
                profile = UserProfile(
                    phoneNumber = registration.phoneNumber,
                    name = registration.applicantName,
                    userType = UserType.DRIVER,
                    address = registration.address,
                    emergencyContact = registration.emergencyContact,
                    licenseNumber = registration.licenseNumber,
                    licenseExpiry = registration.licenseExpiry,
                    yearsOfExperience = registration.yearsOfExperience
                )
            )

            users.add(user)
            registrations.add(registration)

            // Create TODA membership record
            val membership = TODAMembership(
                id = UUID.randomUUID().toString(),
                memberId = user.id,
                todaOrganizationId = "toda_brgy177_001",
                membershipNumber = registration.todaNumber,
                membershipType = MembershipType.DRIVER,
                status = MembershipStatus.ACTIVE
            )
            todaMemberships.add(membership)

            return RegistrationResult.Pending(
                user = user,
                message = "Driver application submitted successfully! Your application is under review. You will be notified within 3-5 business days."
            )

        } catch (e: Exception) {
            return RegistrationResult.Error("Registration failed: ${e.message}")
        }
    }

    // Get available tricycles for driver registration
    fun getAvailableTricycles(): List<Tricycle> {
        return tricycles.filter { it.isActive }
    }

    // Get tricycle details
    fun getTricycleById(id: String): Tricycle? {
        return tricycles.find { it.id == id }
    }

    // Approve driver registration (for operators/admins)
    suspend fun approveDriverRegistration(
        registrationId: String,
        approvedBy: String
    ): Boolean {
        val registration = registrations.find {
            it is DriverRegistration && it.id == registrationId
        } as? DriverRegistration ?: return false

        try {
            // Update registration status
            val updatedRegistration = registration.copy(
                status = DriverApplicationStatus.APPROVED,
                approvedBy = approvedBy,
                approvalDate = System.currentTimeMillis()
            )

            // Update user status
            val user = users.find { it.phoneNumber == registration.phoneNumber }
            if (user != null) {
                val updatedUser = user.copy(
                    isVerified = true,
                    membershipStatus = "ACTIVE"
                )
                users.removeIf { it.id == user.id }
                users.add(updatedUser)

                // Add driver to tricycle
                val tricycle = tricycles.find { it.id == registration.tricycleId }
                if (tricycle != null) {
                    val updatedTricycle = tricycle.copy(
                        registeredDrivers = tricycle.registeredDrivers + user.id,
                        primaryDriverId = if (tricycle.primaryDriverId.isEmpty()) user.id else tricycle.primaryDriverId
                    )
                    tricycles.removeIf { it.id == tricycle.id }
                    tricycles.add(updatedTricycle)
                }
            }

            // Update registration in list
            registrations.removeIf {
                it is DriverRegistration && it.id == registrationId
            }
            registrations.add(updatedRegistration)

            return true
        } catch (e: Exception) {
            return false
        }
    }

    // Reject driver registration
    suspend fun rejectDriverRegistration(
        registrationId: String,
        rejectionReason: String,
        rejectedBy: String
    ): Boolean {
        val registration = registrations.find {
            it is DriverRegistration && it.id == registrationId
        } as? DriverRegistration ?: return false

        try {
            val updatedRegistration = registration.copy(
                status = DriverApplicationStatus.REJECTED,
                rejectionReason = rejectionReason,
                approvedBy = rejectedBy,
                approvalDate = System.currentTimeMillis()
            )

            registrations.removeIf {
                it is DriverRegistration && it.id == registrationId
            }
            registrations.add(updatedRegistration)

            return true
        } catch (e: Exception) {
            return false
        }
    }

    // Get pending driver applications (for operators)
    fun getPendingDriverApplications(): List<DriverRegistration> {
        return registrations.filterIsInstance<DriverRegistration>()
            .filter { it.status == DriverApplicationStatus.PENDING }
    }

    // Get all driver applications
    fun getAllDriverApplications(): List<DriverRegistration> {
        return registrations.filterIsInstance<DriverRegistration>()
    }

    // Verify phone number
    suspend fun verifyPhoneNumber(
        phoneNumber: String,
        verificationCode: String
    ): Boolean {
        delay(500) // Simulate verification delay

        // In production, verify against sent SMS code
        // For demo, accept any 4-digit code
        if (verificationCode.length == 4 && verificationCode.all { it.isDigit() }) {
            // Update user verification status
            val user = users.find { it.phoneNumber == phoneNumber }
            if (user != null) {
                val updatedUser = user.copy(isVerified = true)
                users.removeIf { it.id == user.id }
                users.add(updatedUser)
                return true
            }
        }
        return false
    }

    // Get user by phone number
    fun getUserByPhoneNumber(phoneNumber: String): User? {
        return users.find { it.phoneNumber == phoneNumber }
    }

    // Get TODA membership by user ID
    fun getTODAMembership(userId: String): TODAMembership? {
        return todaMemberships.find { it.memberId == userId }
    }

    // Get tricycles for a specific driver
    fun getTricyclesForDriver(driverId: String): List<Tricycle> {
        return tricycles.filter { driverId in it.registeredDrivers }
    }

    // Add driver to existing tricycle
    suspend fun addDriverToTricycle(
        tricycleId: String,
        driverId: String
    ): Boolean {
        val tricycle = tricycles.find { it.id == tricycleId } ?: return false

        if (driverId in tricycle.registeredDrivers) {
            return false // Driver already registered
        }

        try {
            val updatedTricycle = tricycle.copy(
                registeredDrivers = tricycle.registeredDrivers + driverId
            )
            tricycles.removeIf { it.id == tricycleId }
            tricycles.add(updatedTricycle)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // Remove driver from tricycle
    suspend fun removeDriverFromTricycle(
        tricycleId: String,
        driverId: String
    ): Boolean {
        val tricycle = tricycles.find { it.id == tricycleId } ?: return false

        try {
            val updatedTricycle = tricycle.copy(
                registeredDrivers = tricycle.registeredDrivers - driverId,
                primaryDriverId = if (tricycle.primaryDriverId == driverId) {
                    tricycle.registeredDrivers.firstOrNull { it != driverId } ?: ""
                } else tricycle.primaryDriverId
            )
            tricycles.removeIf { it.id == tricycleId }
            tricycles.add(updatedTricycle)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun isValidTODANumber(todaNumber: String): Boolean {
        // In production, validate against TODA database
        return todaNumber.isNotEmpty() && todaNumber.length >= 6
    }

    private fun generateVerificationCode(): String {
        return (1000..9999).random().toString()
    }
}

sealed class RegistrationResult {
    data class Success(
        val user: User,
        val verificationCode: String? = null,
        val message: String
    ) : RegistrationResult()

    data class Pending(
        val user: User,
        val message: String
    ) : RegistrationResult()

    data class Error(
        val message: String
    ) : RegistrationResult()
}
