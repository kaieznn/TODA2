package com.example.toda.ui.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.osmdroid.util.GeoPoint
import com.example.toda.data.*
import com.example.toda.service.CustomerLocationService
import com.example.toda.service.GeocodingService
import com.example.toda.service.DriverTrackingService
import com.example.toda.service.LocationSuggestionService
import com.example.toda.service.LocationSuggestion
import com.example.toda.ui.components.OSMMapView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import androidx.compose.runtime.collectAsState
import com.example.toda.ui.chat.SimpleChatScreen
import com.example.toda.viewmodel.EnhancedBookingViewModel
import androidx.hilt.navigation.compose.hiltViewModel

data class LocationValidation(
    val isValid: Boolean,
    val message: String
)

data class FareBreakdown(
    val passengerDistance: Double,
    val driverToPickupDistance: Double,
    val baseFare: Double,
    val driverTravelFee: Double,
    val totalFare: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerInterface(
    user: User,
    bookings: List<Booking>,
    bookingHistory: List<Booking>,
    customerLocationService: CustomerLocationService?,
    driverTrackingService: DriverTrackingService,
    onBookingSubmitted: (Booking) -> Unit,
    onCompleteBooking: (String) -> Unit = {},
    onCancelBooking: (String) -> Unit = {},
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: EnhancedBookingViewModel = hiltViewModel() // Add the ViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geocodingService = remember { GeocodingService() }
    val locationSuggestionService = remember { LocationSuggestionService(context) }

    // UI State
    var currentView by remember { mutableStateOf("booking") } // "booking", "history", "chat"
    var showChat by remember { mutableStateOf(false) }
    var activeChatBooking by remember { mutableStateOf<Booking?>(null) }
    var pickupLocation by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<String?>(null) }
    var pickupGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var dropoffGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var isSelectingPickup by remember { mutableStateOf(false) }
    var isSelectingDropoff by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationValidation by remember { mutableStateOf(LocationValidation(true, "")) }
    var fareBreakdown by remember { mutableStateOf<FareBreakdown?>(null) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    // New state for location input and suggestions
    var pickupInputText by remember { mutableStateOf("") }
    var destinationInputText by remember { mutableStateOf("") }
    var pickupSuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var destinationSuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var showPickupSuggestions by remember { mutableStateOf(false) }
    var showDestinationSuggestions by remember { mutableStateOf(false) }

    // Location tracking state
    val customerLocation by customerLocationService?.customerLocation?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val isCurrentlyTracking by customerLocationService?.isCurrentlyTracking?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }

    // Driver tracking state
    val driverTracking by driverTrackingService.driverUpdates.collectAsState(initial = null)

    // Find assigned booking (accepted or in progress booking for current user)
    val assignedBooking = bookings.find { booking ->
        val isMyBooking = booking.customerId == user.id
        val isActiveStatus = booking.status == BookingStatus.ACCEPTED ||
                           booking.status == BookingStatus.IN_PROGRESS

        // Add debugging
        if (isMyBooking) {
            println("=== CUSTOMER INTERFACE BOOKING DEBUG ===")
            println("Found booking for user: ${booking.id}")
            println("Booking status: ${booking.status}")
            println("Status name: ${booking.status.name}")
            println("Is active status: $isActiveStatus")
            println("======================================")
        }

        isMyBooking && isActiveStatus
    }

    // Add more debugging for assignedBooking
    LaunchedEffect(assignedBooking) {
        println("=== ASSIGNED BOOKING CHANGED ===")
        println("Assigned booking: $assignedBooking")
        println("Current bookings count: ${bookings.size}")
        bookings.forEach { booking ->
            if (booking.customerId == user.id) {
                println("User booking: ${booking.id} - Status: ${booking.status}")
            }
        }
        println("===============================")
    }

    // Add comprehensive debugging for real device testing
    LaunchedEffect(bookings, user) {
        println("=== COMPREHENSIVE DEVICE DEBUG ===")
        println("Device: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})")
        println("User ID: ${user.id}")
        println("User Name: ${user.name}")
        println("Total bookings received: ${bookings.size}")
        println("Bookings list:")
        bookings.forEachIndexed { index, booking ->
            println("  [$index] ID: ${booking.id}")
            println("  [$index] Customer ID: ${booking.customerId}")
            println("  [$index] Customer Name: ${booking.customerName}")
            println("  [$index] Status: ${booking.status}")
            println("  [$index] Driver Name: '${booking.driverName}'")
            println("  [$index] TODA Number: '${booking.todaNumber}'")
            println("  [$index] Is my booking: ${booking.customerId == user.id}")
            println("  [$index] Timestamp: ${booking.timestamp}")
            println("  ---")
        }

        val myBookings = bookings.filter { it.customerId == user.id }
        println("My bookings count: ${myBookings.size}")

        val activeBookings = myBookings.filter {
            it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.IN_PROGRESS
        }
        println("My active bookings count: ${activeBookings.size}")

        if (activeBookings.isEmpty()) {
            println("❌ NO ACTIVE BOOKINGS FOUND - This is why cards don't show!")
            println("Checking all possible statuses for user ${user.id}:")
            myBookings.forEach { booking ->
                println("  Booking ${booking.id}: Status = ${booking.status} (${booking.status.name})")
            }
        } else {
            println("✅ Found active bookings - Cards should display")
            activeBookings.forEach { booking ->
                println("Active booking: ${booking.id} - ${booking.status}")
            }
        }
        println("================================")
    }

    // Permission launcher for location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            customerLocationService?.startLocationTracking(user.id)
        }
    }

    // Auto-start location tracking for assigned bookings
    LaunchedEffect(assignedBooking) {
        if (assignedBooking != null && customerLocationService != null) {
            if (!isCurrentlyTracking) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            customerLocationService?.stopLocationTracking()
        }
    }

    // Calculate fare when both locations are selected
    LaunchedEffect(pickupGeoPoint, dropoffGeoPoint) {
        if (pickupGeoPoint != null && dropoffGeoPoint != null) {
            val driverLocation = GeoPoint(14.746, 121.048) // Default driver location
            fareBreakdown = calculateDetailedFare(pickupGeoPoint!!, dropoffGeoPoint!!, driverLocation)
        } else {
            fareBreakdown = null
        }
    }

    // Update location strings when geo points change
    LaunchedEffect(pickupGeoPoint) {
        if (pickupGeoPoint != null && isSelectingPickup) {
            isLoadingLocation = true
            pickupLocation = geocodingService.reverseGeocode(
                pickupGeoPoint!!.latitude,
                pickupGeoPoint!!.longitude
            ) ?: "Selected Location"
            isLoadingLocation = false
            isSelectingPickup = false
        }
    }

    LaunchedEffect(dropoffGeoPoint) {
        if (dropoffGeoPoint != null && isSelectingDropoff) {
            isLoadingLocation = true
            destination = geocodingService.reverseGeocode(
                dropoffGeoPoint!!.latitude,
                dropoffGeoPoint!!.longitude
            ) ?: "Selected Location"
            isLoadingLocation = false
            isSelectingDropoff = false
        }
    }

    // Validate locations
    LaunchedEffect(pickupGeoPoint, dropoffGeoPoint) {
        locationValidation = validateBookingLocations(pickupGeoPoint, dropoffGeoPoint)
    }

    // Hide success message after delay
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            delay(5000)
            showSuccessMessage = false
        }
    }

    fun submitBooking() {
        // Add debugging information
        println("=== BOOKING SUBMISSION DEBUG ===")
        println("pickupGeoPoint: $pickupGeoPoint")
        println("dropoffGeoPoint: $dropoffGeoPoint")
        println("pickupLocation: $pickupLocation")
        println("destination: $destination")
        println("locationValidation.isValid: ${locationValidation.isValid}")
        println("locationValidation.message: ${locationValidation.message}")
        println("fareBreakdown: $fareBreakdown")
        println("===============================")

        if (pickupGeoPoint != null && dropoffGeoPoint != null &&
            pickupLocation != null && destination != null &&
            locationValidation.isValid && fareBreakdown != null) {

            val booking = Booking(
                id = "booking_${System.currentTimeMillis()}",
                customerId = user.id,
                customerName = user.name,
                phoneNumber = user.phoneNumber,
                pickupLocation = pickupLocation!!,
                destination = destination!!,
                pickupGeoPoint = pickupGeoPoint!!,
                dropoffGeoPoint = dropoffGeoPoint!!,
                estimatedFare = fareBreakdown!!.totalFare,
                verificationCode = generateVerificationCode()
            )

            println("Submitting booking: $booking")
            onBookingSubmitted(booking)
            showSuccessMessage = true

            // Reset form
            pickupInputText = ""
            destinationInputText = ""
            pickupLocation = null
            destination = null
            pickupGeoPoint = null
            dropoffGeoPoint = null
            fareBreakdown = null
        } else {
            println("Booking submission failed - validation conditions not met")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ImprovedTopBar(
            userName = user.name,
            onBack = onBack,
            onLogout = onLogout
        )

        // View Toggle Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { currentView = "booking" },
                modifier = Modifier.weight(1f),
                colors = if (currentView == "booking")
                    ButtonDefaults.buttonColors()
                else
                    ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Book Ride")
            }

            Button(
                onClick = { currentView = "history" },
                modifier = Modifier.weight(1f),
                colors = if (currentView == "history")
                    ButtonDefaults.buttonColors()
                else
                    ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("History (${bookingHistory.size})")
            }
        }

        when (currentView) {
            "booking" -> {
                BookingView(
                    user = user,
                    assignedBooking = assignedBooking,
                    customerLocation = customerLocation,
                    isCurrentlyTracking = isCurrentlyTracking,
                    driverTracking = driverTracking,
                    pickupLocation = pickupLocation,
                    destination = destination,
                    pickupGeoPoint = pickupGeoPoint,
                    dropoffGeoPoint = dropoffGeoPoint,
                    isSelectingPickup = isSelectingPickup,
                    isSelectingDropoff = isSelectingDropoff,
                    isLoadingLocation = isLoadingLocation,
                    locationValidation = locationValidation,
                    fareBreakdown = fareBreakdown,
                    showSuccessMessage = showSuccessMessage,
                    // Autocomplete parameters
                    pickupInputText = pickupInputText,
                    destinationInputText = destinationInputText,
                    pickupSuggestions = pickupSuggestions,
                    destinationSuggestions = destinationSuggestions,
                    showPickupSuggestions = showPickupSuggestions,
                    showDestinationSuggestions = showDestinationSuggestions,
                    locationSuggestionService = locationSuggestionService,
                    coroutineScope = coroutineScope,
                    onPickupLocationSelect = { isSelectingPickup = true },
                    onDropoffLocationSelect = { isSelectingDropoff = true },
                    onMapClick = { geoPoint ->
                        when {
                            isSelectingPickup -> {
                                pickupGeoPoint = geoPoint
                                coroutineScope.launch {
                                    isLoadingLocation = true
                                    val locationName = geocodingService.reverseGeocode(
                                        geoPoint.latitude,
                                        geoPoint.longitude
                                    ) ?: "Selected Location"
                                    pickupInputText = locationName
                                    pickupLocation = locationName
                                    isLoadingLocation = false
                                    isSelectingPickup = false
                                }
                            }
                            isSelectingDropoff -> {
                                dropoffGeoPoint = geoPoint
                                coroutineScope.launch {
                                    isLoadingLocation = true
                                    val locationName = geocodingService.reverseGeocode(
                                        geoPoint.latitude,
                                        geoPoint.longitude
                                    ) ?: "Selected Location"
                                    destinationInputText = locationName
                                    destination = locationName
                                    isLoadingLocation = false
                                    isSelectingDropoff = false
                                }
                            }
                        }
                    },
                    onPickupLocationDragged = { geoPoint ->
                        pickupGeoPoint = geoPoint
                        coroutineScope.launch {
                            isLoadingLocation = true
                            val locationName = geocodingService.reverseGeocode(
                                geoPoint.latitude,
                                geoPoint.longitude
                            ) ?: "Selected Location"
                            pickupInputText = locationName
                            pickupLocation = locationName
                            isLoadingLocation = false
                        }
                    },
                    onDropoffLocationDragged = { geoPoint ->
                        dropoffGeoPoint = geoPoint
                        coroutineScope.launch {
                            isLoadingLocation = true
                            val locationName = geocodingService.reverseGeocode(
                                geoPoint.latitude,
                                geoPoint.longitude
                            ) ?: "Selected Location"
                            destinationInputText = locationName
                            destination = locationName
                            isLoadingLocation = false
                        }
                    },
                    onSubmitBooking = ::submitBooking,
                    onSuccessMessageDismiss = { showSuccessMessage = false },
                    // Autocomplete callbacks
                    onPickupTextChange = { text ->
                        pickupInputText = text
                        pickupLocation = text.takeIf { it.isNotEmpty() }
                        if (text.length >= 2) {
                            showPickupSuggestions = true
                            coroutineScope.launch {
                                pickupSuggestions = locationSuggestionService.searchLocations(text)
                            }
                        } else {
                            showPickupSuggestions = false
                            pickupSuggestions = emptyList()
                        }
                    },
                    onDestinationTextChange = { text ->
                        destinationInputText = text
                        destination = text.takeIf { it.isNotEmpty() }
                        if (text.length >= 2) {
                            showDestinationSuggestions = true
                            coroutineScope.launch {
                                destinationSuggestions = locationSuggestionService.searchLocations(text)
                            }
                        } else {
                            showDestinationSuggestions = false
                            destinationSuggestions = emptyList()
                        }
                    },
                    onPickupSuggestionSelected = { suggestion ->
                        pickupInputText = suggestion.name
                        pickupLocation = suggestion.name
                        pickupGeoPoint = suggestion.geoPoint
                        showPickupSuggestions = false
                        pickupSuggestions = emptyList()
                    },
                    onDestinationSuggestionSelected = { suggestion ->
                        destinationInputText = suggestion.name
                        destination = suggestion.name
                        dropoffGeoPoint = suggestion.geoPoint
                        showDestinationSuggestions = false
                        destinationSuggestions = emptyList()
                    },
                    // Chat parameters
                    onChatClick = {
                        showChat = true
                        currentView = "chat"
                    }
                )
            }
            "history" -> {
                BookingHistoryView(
                    bookingHistory = bookingHistory,
                    currentBookings = bookings.filter { it.customerId == user.id }
                )
            }
        }

        // Chat feature - Always show the chat button when there's an active booking
        if (assignedBooking != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FloatingActionButton(
                    onClick = {
                        showChat = !showChat
                        if (showChat) {
                            currentView = "chat"
                        } else {
                            currentView = "booking"
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (showChat) Icons.Default.Close else Icons.Default.Chat,
                        contentDescription = "Chat"
                    )
                }
            }

            // Chat screen - Full screen overlay
            if (showChat) {
                SimpleChatScreen(
                    user = user,
                    booking = assignedBooking,
                    onBack = { showChat = false },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun BookingView(
    user: User,
    assignedBooking: Booking?,
    customerLocation: CustomerLocation?,
    isCurrentlyTracking: Boolean,
    driverTracking: DriverTracking?,
    pickupLocation: String?,
    destination: String?,
    pickupGeoPoint: GeoPoint?,
    dropoffGeoPoint: GeoPoint?,
    isSelectingPickup: Boolean,
    isSelectingDropoff: Boolean,
    isLoadingLocation: Boolean,
    locationValidation: LocationValidation,
    fareBreakdown: FareBreakdown?,
    showSuccessMessage: Boolean,
    onPickupLocationSelect: () -> Unit,
    onDropoffLocationSelect: () -> Unit,
    onMapClick: (GeoPoint) -> Unit,
    onPickupLocationDragged: (GeoPoint) -> Unit,
    onDropoffLocationDragged: (GeoPoint) -> Unit,
    onSubmitBooking: () -> Unit,
    onSuccessMessageDismiss: () -> Unit,
    // Autocomplete parameters
    pickupInputText: String,
    destinationInputText: String,
    pickupSuggestions: List<LocationSuggestion>,
    destinationSuggestions: List<LocationSuggestion>,
    showPickupSuggestions: Boolean,
    showDestinationSuggestions: Boolean,
    locationSuggestionService: LocationSuggestionService,
    coroutineScope: CoroutineScope,
    onPickupTextChange: (String) -> Unit,
    onDestinationTextChange: (String) -> Unit,
    onPickupSuggestionSelected: (LocationSuggestion) -> Unit,
    onDestinationSuggestionSelected: (LocationSuggestion) -> Unit,
    // Chat parameters
    onChatClick: () -> Unit = {} // Add chat callback parameter
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp), // Add extra bottom padding
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ImprovedUserInfoCard(user)

        if (assignedBooking != null) {
            ActiveBookingCard(
                booking = assignedBooking,
                customerLocation = customerLocation,
                isTracking = isCurrentlyTracking,
                driverTracking = driverTracking,
                onChatClick = onChatClick // Use the passed callback
            )
        } else {
            if (showSuccessMessage) {
                ImprovedSuccessMessage(onSuccessMessageDismiss)
            }

            if (isLoadingLocation) {
                LoadingIndicator()
            }

            // Location Input Fields with Autocomplete
            LocationInputWithSuggestions(
                pickupInputText = pickupInputText,
                destinationInputText = destinationInputText,
                pickupSuggestions = pickupSuggestions,
                destinationSuggestions = destinationSuggestions,
                showPickupSuggestions = showPickupSuggestions,
                showDestinationSuggestions = showDestinationSuggestions,
                locationSuggestionService = locationSuggestionService,
                coroutineScope = coroutineScope,
                onPickupTextChange = onPickupTextChange,
                onDestinationTextChange = onDestinationTextChange,
                onPickupSuggestionSelected = onPickupSuggestionSelected,
                onDestinationSuggestionSelected = onDestinationSuggestionSelected,
                onPickupLocationSelect = onPickupLocationSelect,
                onDropoffLocationSelect = onDropoffLocationSelect,
                isSelectingPickup = isSelectingPickup,
                isSelectingDropoff = isSelectingDropoff
            )

            // Map - Reduced height and properly contained
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    // Map header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Service Area Map",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Drag markers to adjust",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Map with fixed height
                    RestrictedOSMMapView(
                        pickupLocation = pickupGeoPoint,
                        dropoffLocation = dropoffGeoPoint,
                        onMapClick = onMapClick,
                        onPickupLocationDragged = onPickupLocationDragged,
                        onDropoffLocationDragged = onDropoffLocationDragged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                }
            }

            if (!locationValidation.isValid) {
                LocationValidationWarning(locationValidation.message)
            }

            if (fareBreakdown != null) {
                EnhancedFareEstimationCard(fareBreakdown, "Juan Dela Cruz")
            }

            AreaRestrictionCard()
            SpecialTripInfoCard()

            // Submit button with extra spacing
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSubmitBooking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp), // Add vertical padding to the button
                enabled = pickupGeoPoint != null &&
                        dropoffGeoPoint != null &&
                        locationValidation.isValid
            ) {
                Text(
                    text = "Submit Booking Request",
                    modifier = Modifier.padding(vertical = 4.dp) // Add padding inside button
                )
            }

            // Extra spacer at the bottom to ensure button is fully visible
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BookingHistoryView(
    bookingHistory: List<Booking>,
    currentBookings: List<Booking>
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Current Active Bookings Section
        if (currentBookings.isNotEmpty()) {
            item {
                Text(
                    text = "Current Bookings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(currentBookings) { booking ->
                CurrentBookingCard(booking)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Booking History Section
        item {
            Text(
                text = "Booking History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (bookingHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No booking history yet",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(bookingHistory) { booking ->
                BookingHistoryCard(booking)
            }
        }
    }
}

@Composable
private fun ImprovedTopBar(
    userName: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Welcome, $userName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
        }
    }
}

@Composable
private fun ImprovedUserInfoCard(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = user.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveBookingCard(
    booking: Booking,
    customerLocation: CustomerLocation?,
    isTracking: Boolean,
    driverTracking: DriverTracking?,
    onChatClick: () -> Unit = {} // Add chat callback parameter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Booking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge {
                    Text(booking.status.name)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Driver Status Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getDriverStatusText(booking.status, driverTracking),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Driver Information
                            if (driverTracking != null) {
                                // Use driver tracking data if available (real-time)
                                Text(
                                    text = "Driver: ${driverTracking.driverName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                if (driverTracking.estimatedArrival > System.currentTimeMillis()) {
                                    val minutesAway = ((driverTracking.estimatedArrival - System.currentTimeMillis()) / 60000).toInt()
                                    Text(
                                        text = "ETA: ${if (minutesAway > 0) "$minutesAway min" else "Arriving soon"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.IN_PROGRESS) {
                                // Show driver info from booking data if no real-time tracking
                                if (booking.assignedTricycleId.isNotEmpty()) {
                                    Text(
                                        text = "Driver ID: ${booking.assignedTricycleId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "Connecting to driver...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Chat Button
                        if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.IN_PROGRESS) {
                            FilledTonalButton(
                                onClick = onChatClick, // Connect to the callback
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = "Chat with driver",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // TODA Number and Driver Details (if assigned)
                    if (booking.status == BookingStatus.ACCEPTED || booking.status == BookingStatus.IN_PROGRESS) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // TODA Information Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Badge,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TODA Information",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Driver Name (if available from Firebase data)
                                Text(
                                    text = "Driver: ${getDriverDisplayName(driverTracking, booking)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                // TODA Number
                                Text(
                                    text = "TODA #: ${getTODANumber(booking)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                // Tricycle/Driver ID
                                if (booking.assignedTricycleId.isNotEmpty()) {
                                    Text(
                                        text = "Vehicle ID: ${booking.assignedTricycleId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trip Details
            Row {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "From: ${booking.pickupLocation}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "To: ${booking.destination}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fare Information
            Row {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fare: ₱${String.format(Locale.getDefault(), "%.2f", booking.estimatedFare)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Location Tracking Status
            if (isTracking) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color.Green)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Location tracking active",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
            }

            // Verification Code (for driver verification)
            if (booking.verificationCode.isNotEmpty() && booking.status == BookingStatus.ACCEPTED) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Verification Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = booking.verificationCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Show this to your driver",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

// Helper function to get driver status text based on booking status
private fun getDriverStatusText(status: BookingStatus, driverTracking: DriverTracking?): String {
    return when (status) {
        BookingStatus.PENDING -> "Looking for available driver..."
        BookingStatus.ACCEPTED -> {
            if (driverTracking != null && driverTracking.isMoving) {
                "Driver is on the way!"
            } else {
                "Driver accepted your request"
            }
        }
        BookingStatus.IN_PROGRESS -> "Trip in progress"
        else -> "Booking ${status.name.lowercase()}"
    }
}

@Composable
private fun ImprovedSuccessMessage(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Booking submitted successfully!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Loading location...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LocationInputWithSuggestions(
    pickupInputText: String,
    destinationInputText: String,
    pickupSuggestions: List<LocationSuggestion>,
    destinationSuggestions: List<LocationSuggestion>,
    showPickupSuggestions: Boolean,
    showDestinationSuggestions: Boolean,
    locationSuggestionService: LocationSuggestionService,
    coroutineScope: CoroutineScope,
    onPickupTextChange: (String) -> Unit,
    onDestinationTextChange: (String) -> Unit,
    onPickupSuggestionSelected: (LocationSuggestion) -> Unit,
    onDestinationSuggestionSelected: (LocationSuggestion) -> Unit,
    onPickupLocationSelect: () -> Unit,
    onDropoffLocationSelect: () -> Unit,
    isSelectingPickup: Boolean,
    isSelectingDropoff: Boolean
) {
    Column {
        // Pickup Location Input
        OutlinedTextField(
            value = pickupInputText,
            onValueChange = onPickupTextChange,
            label = { Text("Pickup Location") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onPickupLocationSelect) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Select on map",
                        tint = if (isSelectingPickup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Pickup Suggestions
        if (showPickupSuggestions && pickupSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    pickupSuggestions.take(5).forEach { suggestion ->
                        Surface(
                            onClick = { onPickupSuggestionSelected(suggestion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Destination Location Input
        OutlinedTextField(
            value = destinationInputText,
            onValueChange = onDestinationTextChange,
            label = { Text("Destination") },
            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onDropoffLocationSelect) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Select on map",
                        tint = if (isSelectingDropoff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Destination Suggestions
        if (showDestinationSuggestions && destinationSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    destinationSuggestions.take(5).forEach { suggestion ->
                        Surface(
                            onClick = { onDestinationSuggestionSelected(suggestion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictedOSMMapView(
    pickupLocation: GeoPoint?,
    dropoffLocation: GeoPoint?,
    onMapClick: (GeoPoint) -> Unit,
    onPickupLocationDragged: (GeoPoint) -> Unit,
    onDropoffLocationDragged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    // For now, use the existing OSMMapView component
    // In a real implementation, this would be a restricted version of the map
    OSMMapView(
        pickupLocation = pickupLocation,
        dropoffLocation = dropoffLocation,
        onMapClick = onMapClick,
        onPickupLocationDragged = onPickupLocationDragged,
        onDropoffLocationDragged = onDropoffLocationDragged,
        modifier = modifier
    )
}

@Composable
private fun LocationValidationWarning(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EnhancedFareEstimationCard(fareBreakdown: FareBreakdown, driverName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Fare Estimation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Base Fare:")
                Text("₱${String.format("%.2f", fareBreakdown.baseFare)}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Distance (${String.format("%.2f", fareBreakdown.passengerDistance)} km):")
                Text("₱${String.format("%.2f", fareBreakdown.baseFare)}")
            }

            if (fareBreakdown.driverTravelFee > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Driver Travel Fee:")
                    Text("₱${String.format("%.2f", fareBreakdown.driverTravelFee)}")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Estimated Fare:",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "₱${String.format("%.2f", fareBreakdown.totalFare)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Assigned Driver: $driverName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AreaRestrictionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Service Area Information",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Service available within Camarin, Caloocan area\n" +
                        "• Additional charges may apply for locations outside the main service area\n" +
                        "• Maximum distance: 5 kilometers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecialTripInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Special Trip Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Real-time location tracking\n" +
                        "• Driver rating and feedback system\n" +
                        "• Cashless payment options",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun CurrentBookingCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Booking #${booking.id.takeLast(6)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = when (booking.status.name) {
                        "COMPLETED" -> Color.Green
                        "CANCELLED" -> Color.Red
                        else -> MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Text(booking.status.name)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "From: ${booking.pickupLocation}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "To: ${booking.destination}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Fare: ₱${String.format(Locale.getDefault(), "%.2f", booking.estimatedFare)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BookingHistoryCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Booking #${booking.id.takeLast(6)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = when (booking.status.name) {
                        "COMPLETED" -> Color.Green
                        "CANCELLED" -> Color.Red
                        else -> MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Text(booking.status.name)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "From: ${booking.pickupLocation}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "To: ${booking.destination}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Fare: ₱${String.format(Locale.getDefault(), "%.2f", booking.estimatedFare)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Date: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(booking.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper function to calculate distance between two geo points using Haversine formula
private fun calculateDistance(pickup: GeoPoint, dropoff: GeoPoint): Double {
    val earthRadius = 6371.0 // Earth's radius in kilometers

    val lat1Rad = Math.toRadians(pickup.latitude)
    val lat2Rad = Math.toRadians(dropoff.latitude)
    val deltaLatRad = Math.toRadians(dropoff.latitude - pickup.latitude)
    val deltaLngRad = Math.toRadians(dropoff.longitude - pickup.longitude)

    val a = sin(deltaLatRad / 2).pow(2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}

// Helper function to calculate fare based on distance
private fun calculateFare(distance: Double): Double {
    val baseFare = 25.0 // Base fare in PHP
    val perKmRate = 10.0 // Rate per kilometer in PHP

    return if (distance <= 2.0) {
        baseFare
    } else {
        baseFare + ((distance - 2.0) * perKmRate)
    }
}

// Helper function to check if a location is within Barangay 177 (Camarin)
private fun isWithinBarangay177(geoPoint: GeoPoint): Boolean {
    // Expanded bounds for Barangay 177, Camarin, Caloocan to be more inclusive
    // Based on the Firebase data coordinates: lat ~14.748-14.750, lng ~121.050-121.052
    val minLat = 14.740  // Expanded from 14.745
    val maxLat = 14.760  // Expanded from 14.755
    val minLng = 121.040 // Expanded from 121.045
    val maxLng = 121.060 // Expanded from 121.055

    return geoPoint.latitude in minLat..maxLat &&
            geoPoint.longitude in minLng..maxLng
}

// Helper function to validate booking locations
private fun validateBookingLocations(pickupGeoPoint: GeoPoint?, dropoffGeoPoint: GeoPoint?): LocationValidation {
    if (pickupGeoPoint == null || dropoffGeoPoint == null) {
        return LocationValidation(false, "Please select both pickup and dropoff locations")
    }

    // More lenient validation - check if coordinates are reasonable for the area
    if (pickupGeoPoint.latitude < 14.7 || pickupGeoPoint.latitude > 14.8 ||
        pickupGeoPoint.longitude < 121.0 || pickupGeoPoint.longitude > 121.1) {
        return LocationValidation(false, "Pickup location seems to be outside the service area")
    }

    if (dropoffGeoPoint.latitude < 14.7 || dropoffGeoPoint.latitude > 14.8 ||
        dropoffGeoPoint.longitude < 121.0 || dropoffGeoPoint.longitude > 121.1) {
        return LocationValidation(false, "Dropoff location seems to be outside the service area")
    }

    val distance = calculateDistance(pickupGeoPoint, dropoffGeoPoint)
    if (distance < 0.01) { // Changed from 0.1 to 0.01 (about 10 meters)
        return LocationValidation(false, "Pickup and dropoff locations are too close")
    }

    if (distance > 20.0) { // Increased from 10.0 to 20.0 km
        return LocationValidation(false, "Trip distance exceeds maximum allowed distance")
    }

    return LocationValidation(true, "")
}

// Helper function to generate verification code
private fun generateVerificationCode(): String {
    return (1000..9999).random().toString()
}

// Helper function to format timestamp
private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

// Main function to calculate detailed fare breakdown
private fun calculateDetailedFare(pickupLocation: GeoPoint, dropoffLocation: GeoPoint, driverLocation: GeoPoint): FareBreakdown {
    // Calculate passenger trip distance
    val passengerDistance = calculateDistance(pickupLocation, dropoffLocation)

    // Calculate driver travel distance to pickup
    val driverToPickupDistance = calculateDistance(driverLocation, pickupLocation)

    // Calculate base fare for passenger trip
    val baseFare = calculateFare(passengerDistance)

    // Calculate driver travel fee (reduced rate for driver positioning)
    val driverTravelFee = if (driverToPickupDistance <= 1.0) {
        0.0 // No extra fee for nearby drivers
    } else {
        (driverToPickupDistance - 1.0) * 5.0 // 5 PHP per km beyond 1km
    }

    // Calculate total fare
    val totalFare = baseFare + driverTravelFee

    return FareBreakdown(
        passengerDistance = passengerDistance,
        driverToPickupDistance = driverToPickupDistance,
        baseFare = baseFare,
        driverTravelFee = driverTravelFee,
        totalFare = totalFare
    )
}

// Helper function to get driver display name
private fun getDriverDisplayName(driverTracking: DriverTracking?, booking: Booking): String {
    return when {
        driverTracking != null && driverTracking.driverName.isNotEmpty() -> driverTracking.driverName
        booking.driverName.isNotEmpty() -> booking.driverName
        booking.assignedDriverId.isNotEmpty() -> "Driver ID: ${booking.assignedDriverId}"
        booking.assignedTricycleId.isNotEmpty() -> "Driver ID: ${booking.assignedTricycleId}"
        booking.driverRFID.isNotEmpty() -> "RFID: ${booking.driverRFID}"
        else -> "Not assigned"
    }
}

// Helper function to get TODA number
private fun getTODANumber(booking: Booking): String {
    return when {
        booking.todaNumber.isNotEmpty() -> booking.todaNumber
        booking.assignedTricycleId.isNotEmpty() -> booking.assignedTricycleId
        else -> "Not assigned"
    }
}
