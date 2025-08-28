package com.example.toda

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.toda.data.User
import com.example.toda.data.UserType
import com.example.toda.data.FirebaseUser
import com.example.toda.data.NotificationState
import com.example.toda.data.BookingStatus
import com.example.toda.ui.auth.AuthenticationScreen
import com.example.toda.ui.auth.CustomerLoginScreen
import com.example.toda.ui.common.UserTypeSelection
import com.example.toda.ui.customer.CustomerInterface
import com.example.toda.ui.customer.CustomerDashboardScreen
import com.example.toda.ui.operator.OperatorInterface
import com.example.toda.ui.driver.DriverRegistrationScreen
import com.example.toda.ui.driver.DriverLoginScreen
import com.example.toda.ui.driver.DriverInterface
import com.example.toda.ui.admin.AdminDashboardScreen
import com.example.toda.ui.admin.AdminDriverManagementScreen
import com.example.toda.ui.registration.RegistrationTypeScreen
import com.example.toda.ui.theme.TODATheme
import com.example.toda.utils.NotificationManager
import com.example.toda.viewmodel.EnhancedBookingViewModel
import com.example.toda.viewmodel.BookingViewModel
import com.example.toda.service.FirebaseSyncService
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var firebaseSyncService: FirebaseSyncService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationManager = NotificationManager(this)

        // Start Firebase sync service to create local JSON files
        firebaseSyncService.startSyncing(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            TODATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BookingApp(
                        modifier = Modifier.padding(innerPadding),
                        notificationManager = notificationManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the sync service when app is destroyed
        firebaseSyncService.stopSyncing()
    }
}

@Composable
fun BookingApp(
    modifier: Modifier = Modifier,
    notificationManager: NotificationManager
) {
    val context = LocalContext.current
    val enhancedBookingViewModel: EnhancedBookingViewModel = hiltViewModel()
    val locationBookingViewModel = remember { BookingViewModel() }

    // Collect real-time data from EnhancedBookingViewModel
    val activeBookings by enhancedBookingViewModel.activeBookings.collectAsState()
    val bookingState by enhancedBookingViewModel.bookingState.collectAsState()

    var selectedUserType by remember { mutableStateOf<String?>(null) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf("user_selection") } // "user_selection", "customer_auth", "customer_login", "customer_dashboard", "operator"
    var notificationState by remember {
        mutableStateOf(
            NotificationState(
                hasPermission = notificationManager.hasNotificationPermission()
            )
        )
    }

    LaunchedEffect(Unit) {
        locationBookingViewModel.initializeCustomerLocationService(context)
    }

    // Helper function to convert FirebaseUser to User
    fun convertFirebaseUserToUser(firebaseUser: FirebaseUser): User {
        return User(
            id = firebaseUser.id,
            phoneNumber = firebaseUser.phoneNumber,
            name = firebaseUser.name,
            password = "", // Don't store password in User object
            userType = when (firebaseUser.userType) {
                "PASSENGER" -> UserType.PASSENGER
                "DRIVER" -> UserType.DRIVER
                "OPERATOR" -> UserType.OPERATOR
                "TODA_ADMIN" -> UserType.TODA_ADMIN
                else -> UserType.PASSENGER
            },
            isVerified = firebaseUser.isVerified,
            registrationDate = firebaseUser.registrationDate,
            todaId = firebaseUser.todaId,
            membershipNumber = firebaseUser.membershipNumber,
            membershipStatus = firebaseUser.membershipStatus
        )
    }


    when (currentScreen) {
        "user_selection" -> {
            UserTypeSelection(
                onUserTypeSelected = { userType ->
                    selectedUserType = userType
                    when (userType) {
                        "customer" -> currentScreen = "customer_login"
                        "driver_login" -> currentScreen = "driver_login"
                        "registration" -> currentScreen = "registration_portal"
                        "admin" -> currentScreen = "admin_dashboard"
                    }
                }
            )
        }

        "registration_portal" -> {
            RegistrationTypeScreen(
                onUserTypeSelected = { userType ->
                    when (userType) {
                        UserType.DRIVER -> currentScreen = "driver_registration"
                        UserType.PASSENGER -> currentScreen = "customer_auth"
                        UserType.OPERATOR -> {
                            // Operators should register through admin dashboard
                            // For now, redirect back to registration portal
                            currentScreen = "registration_portal"
                        }
                        else -> currentScreen = "registration_portal"
                    }
                },
                onBack = {
                    selectedUserType = null
                    currentScreen = "user_selection"
                }
            )
        }

        "customer_login" -> {
            CustomerLoginScreen(
                onLoginSuccess = { userId, firebaseUser ->
                    currentUserId = userId
                    currentUser = convertFirebaseUserToUser(firebaseUser)
                    currentScreen = "customer_dashboard"
                },
                onRegisterClick = {
                    currentScreen = "customer_auth"
                }
            )
        }

        "customer_auth" -> {
            AuthenticationScreen(
                onAuthSuccess = { user ->
                    currentUser = user
                    currentUserId = user.id
                    currentScreen = "customer_dashboard"
                },
                onBack = {
                    currentScreen = "customer_login"
                }
            )
        }

        "customer_dashboard" -> {
            currentUserId?.let { userId ->
                CustomerDashboardScreen(
                    userId = userId,
                    onBookRide = {
                        currentScreen = "customer_interface"
                    },
                    onViewProfile = {
                        // TODO: Navigate to profile screen
                    },
                    onLogout = {
                        currentUser = null
                        currentUserId = null
                        selectedUserType = null
                        currentScreen = "user_selection"
                        locationBookingViewModel.stopCustomerLocationTracking()
                    }
                )
            }
        }

        "customer_interface" -> {
            currentUser?.let { user ->
                // Filter bookings for the current customer - include ALL active statuses for active bookings
                val customerBookings = activeBookings.filter {
                    it.customerId == user.id &&
                    (it.status == BookingStatus.PENDING || it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.IN_PROGRESS)
                }
                val customerBookingHistory = activeBookings.filter {
                    it.customerId == user.id &&
                    (it.status == BookingStatus.COMPLETED || it.status == BookingStatus.CANCELLED)
                }

                // Debug logging
                LaunchedEffect(activeBookings) {
                    println("CustomerInterface - Total active bookings: ${activeBookings.size}")
                    println("CustomerInterface - Customer bookings: ${customerBookings.size}")
                    customerBookings.forEach { booking ->
                        println("CustomerInterface - Booking ${booking.id}: ${booking.status}")
                    }
                }

                CustomerInterface(
                    user = user,
                    bookings = customerBookings,
                    bookingHistory = customerBookingHistory,
                    customerLocationService = locationBookingViewModel.getCustomerLocationService(),
                    driverTrackingService = locationBookingViewModel.driverTrackingService,
                    onBookingSubmitted = { booking ->
                        // Save to Firebase using EnhancedBookingViewModel
                        enhancedBookingViewModel.createBooking(
                            customerId = booking.customerId,
                            customerName = booking.customerName,
                            phoneNumber = booking.phoneNumber,
                            pickupLocation = booking.pickupLocation,
                            destination = booking.destination,
                            pickupGeoPoint = booking.pickupGeoPoint,
                            dropoffGeoPoint = booking.dropoffGeoPoint,
                            estimatedFare = booking.estimatedFare
                        )
                    },
                    onCompleteBooking = { bookingId ->
                        enhancedBookingViewModel.updateBookingStatusOnly(bookingId, "COMPLETED")
                    },
                    onCancelBooking = { bookingId ->
                        enhancedBookingViewModel.updateBookingStatusOnly(bookingId, "CANCELLED")
                    },
                    onBack = {
                        currentScreen = "customer_dashboard"
                    },
                    onLogout = {
                        currentUser = null
                        currentUserId = null
                        selectedUserType = null
                        currentScreen = "user_selection"
                        locationBookingViewModel.stopCustomerLocationTracking()
                    }
                )
            }
        }

        "driver_registration" -> {
            DriverRegistrationScreen(
                onBack = {
                    currentScreen = "registration_portal"
                },
                onRegistrationComplete = {
                    // Navigate back to registration portal after successful registration
                    currentScreen = "registration_portal"
                }
            )
        }

        "admin_dashboard" -> {
            AdminDashboardScreen(
                onBack = {
                    selectedUserType = null
                    currentScreen = "user_selection"
                },
                onDriverManagement = {
                    currentScreen = "driver_management"
                },
                onTricycleManagement = {
                    currentScreen = "tricycle_management"
                },
                onBookingOverview = {
                    currentScreen = "admin_booking_overview"
                },
                onReportsAnalytics = {
                    // TODO: Implement reports and analytics screen
                },
                onSystemSettings = {
                    // TODO: Implement system settings screen
                }
            )
        }

        "admin_booking_overview" -> {
            // This is the operator interface moved to admin dashboard
            LaunchedEffect(Unit) {
                notificationState = notificationState.copy(
                    isOperatorOnline = true
                )
            }

            OperatorInterface(
                bookings = activeBookings.filter {
                    it.status == BookingStatus.PENDING || it.status == BookingStatus.ACCEPTED
                },
                notificationState = notificationState,
                driverTrackingService = locationBookingViewModel.driverTrackingService,
                onBookingUpdate = { updatedBookings ->
                    // This is no longer needed as we're using real-time Firebase data
                    // but kept for interface compatibility
                },
                onBack = {
                    notificationState = notificationState.copy(isOperatorOnline = false)
                    currentScreen = "admin_dashboard"
                },
                onNotificationStateChange = { newState ->
                    notificationState = newState
                }
            )
        }

        "driver_management" -> {
            AdminDriverManagementScreen(
                onBack = {
                    currentScreen = "admin_dashboard"
                }
            )
        }

        "tricycle_management" -> {
            // TODO: Implement TricycleManagementScreen
            // For now, navigate back to admin dashboard
            LaunchedEffect(Unit) {
                currentScreen = "admin_dashboard"
            }
        }

        "driver_login" -> {
            DriverLoginScreen(
                onLoginSuccess = { userId, firebaseUser ->
                    currentUserId = userId
                    currentUser = convertFirebaseUserToUser(firebaseUser)
                    currentScreen = "driver_interface"
                },
                onBack = {
                    selectedUserType = null
                    currentScreen = "user_selection"
                }
            )
        }

        "driver_interface" -> {
            currentUser?.let { user ->
                DriverInterface(
                    user = user,
                    onLogout = {
                        currentUser = null
                        currentUserId = null
                        selectedUserType = null
                        currentScreen = "user_selection"
                    }
                )
            }
        }
    }
}