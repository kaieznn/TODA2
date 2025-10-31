package com.example.toda.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.Booking
import com.example.toda.data.BookingStatus
import com.example.toda.data.User
import com.example.toda.viewmodel.EnhancedBookingViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.toda.ui.chat.SimpleChatScreen
import com.example.toda.data.RfidChangeHistory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverInterface(
    user: User,
    onLogout: () -> Unit,
    viewModel: EnhancedBookingViewModel = hiltViewModel()
) {
    val activeBookings by viewModel.activeBookings.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0=Dashboard, 1=Bookings, 2=History, 3=Contributions, 4=RFID

    // Chat state management
    var showChat by remember { mutableStateOf(false) }
    var activeChatBooking by remember { mutableStateOf<Booking?>(null) }

    // Leave queue confirmation dialog state
    var showLeaveQueueDialog by remember { mutableStateOf(false) }

    // RFID Management state
    var rfidHistory by remember { mutableStateOf<List<RfidChangeHistory>>(emptyList()) }
    var rfidHistoryLoading by remember { mutableStateOf(false) }
    var rfidActionMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // message, isSuccess

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Driver status and stats from Firebase
    var isOnline by remember { mutableStateOf(false) }
    var todaysTrips by remember { mutableStateOf(0) }
    var todaysEarnings by remember { mutableStateOf(0.0) }
    var driverRating by remember { mutableStateOf(5.0) }
    var isLoading by remember { mutableStateOf(true) }
    var driverRFID by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }

    // Load driver RFID and stats (no contribution check)
    LaunchedEffect(user.id) {
        try {
            // Get driver RFID from the drivers table
            val driverResult = viewModel.getDriverById(user.id)
            driverResult.fold(
                onSuccess = { driverData ->
                    driverRFID = driverData["rfidUID"] as? String ?: ""
                    driverName = driverData["driverName"] as? String ?: user.name
                    println("Driver ${user.id} RFID: $driverRFID, Name: $driverName")
                },
                onFailure = { error ->
                    println("Error getting driver RFID: ${error.message}")
                    driverRFID = ""
                    driverName = user.name
                }
            )

            // Get today's statistics
            val statsResult = viewModel.getDriverTodayStats(user.id)
            statsResult.fold(
                onSuccess = { (trips, earnings, rating) ->
                    todaysTrips = trips
                    todaysEarnings = earnings
                    driverRating = rating
                    println("Driver stats: $trips trips, ₱$earnings earnings, $rating rating")
                },
                onFailure = { error ->
                    println("Error getting driver stats: ${error.message}")
                }
            )

            isLoading = false
        } catch (e: Exception) {
            println("Error loading driver data: ${e.message}")
            isLoading = false
        }
    }

    // Load RFID history when RFID tab is selected
    LaunchedEffect(selectedTab, user.id) {
        if (selectedTab == 4) { // RFID Management tab
            rfidHistoryLoading = true
            viewModel.getRfidChangeHistory(user.id).fold(
                onSuccess = { history ->
                    rfidHistory = history
                    rfidHistoryLoading = false
                },
                onFailure = { error ->
                    println("Error loading RFID history: ${error.message}")
                    rfidHistoryLoading = false
                }
            )
        }
    }

    // Real-time observer for queue status - driver is online ONLY if in queue
    LaunchedEffect(driverRFID) {
        if (driverRFID.isNotEmpty()) {
            viewModel.observeDriverQueueStatus(driverRFID).collect { isInQueue ->
                println("=== QUEUE STATUS UPDATE ===")
                println("Driver $driverRFID is in queue: $isInQueue")
                println("Setting online status to: $isInQueue")
                isOnline = isInQueue
            }
        }
    }

    // Refresh stats whenever bookings change (to update dashboard in real-time)
    LaunchedEffect(activeBookings.size, activeBookings.hashCode()) {
        if (user.id.isNotEmpty()) {
            println("=== REFRESHING DRIVER STATS ===")
            println("Active bookings changed, recalculating stats...")
            val statsResult = viewModel.getDriverTodayStats(user.id)
            statsResult.fold(
                onSuccess = { (trips, earnings, rating) ->
                    todaysTrips = trips
                    todaysEarnings = earnings
                    driverRating = rating
                    println("✓ Updated stats: $trips trips, ₱$earnings earnings, $rating rating")
                },
                onFailure = { error ->
                    println("✗ Error refreshing driver stats: ${error.message}")
                }
            )
        }
    }

    // Filter bookings for this driver using driverRFID
    val myBookings = activeBookings.filter { booking ->
        println("Checking booking ${booking.id}: driverRFID=${booking.driverRFID}, assignedDriverId=${booking.assignedDriverId}, status=${booking.status}")

        // Check both driverRFID and assignedDriverId for compatibility
        val isMyBooking = (booking.driverRFID == driverRFID && driverRFID.isNotEmpty()) ||
                         (booking.assignedDriverId == user.id)

        val isActiveStatus = booking.status == BookingStatus.ACCEPTED ||
                           booking.status == BookingStatus.IN_PROGRESS

        val result = isMyBooking && isActiveStatus

        if (result) {
            println("✓ Matched booking ${booking.id} for driver $driverRFID")
        }

        result
    }

    val availableBookings = activeBookings.filter { booking ->
        val isPending = booking.status == BookingStatus.PENDING
        val result = isPending && isOnline

        if (isPending) {
            println("=== AVAILABLE BOOKING DEBUG ===")
            println("Found PENDING booking: ${booking.id}")
            println("Customer: ${booking.customerName}")
            println("Pickup: ${booking.pickupLocation}")
            println("Destination: ${booking.destination}")
            println("Driver is online: $isOnline")
            println("Will show to driver: $result")
        }

        result
    }

    // Get completed bookings for history (check both driverRFID and assignedDriverId)
    val completedBookings = remember(activeBookings, driverRFID, user.id) {
        println("=== FILTERING COMPLETED BOOKINGS ===")
        println("Total active bookings: ${activeBookings.size}")
        println("Driver RFID: $driverRFID")
        println("Driver User ID: ${user.id}")

        // Match by RFID in multiple ways:
        // 1. Booking's driverRFID matches driver's RFID
        // 2. Booking's assignedDriverId matches driver's RFID (legacy bookings)
        // 3. Booking's assignedDriverId matches driver's user ID
        val filtered = activeBookings.filter { booking ->
            println("Checking booking ${booking.id}:")
            println("  - driverRFID: ${booking.driverRFID}")
            println("  - assignedDriverId: ${booking.assignedDriverId}")
            println("  - status: ${booking.status}")
            println("  - customerName: ${booking.customerName}")

            val isMyBooking = (booking.driverRFID == driverRFID && driverRFID.isNotEmpty()) ||
                             (booking.assignedDriverId == driverRFID && driverRFID.isNotEmpty()) ||
                             (booking.assignedDriverId == user.id)
            val isCompleted = booking.status == BookingStatus.COMPLETED

            println("  - isMyBooking: $isMyBooking")
            println("  - isCompleted: $isCompleted")

            val result = isMyBooking && isCompleted

            if (result) {
                println("  ✓✓✓ MATCHED - Adding to completed bookings!")
            }

            result
        }

        println("=== COMPLETED BOOKINGS COUNT: ${filtered.size} ===")
        filtered
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            // Top App Bar - NO TOGGLE SWITCH, status is read-only based on contributions
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TODA Driver",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            fontSize = 12.sp,
                            color = if (isOnline) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status indicator (read-only, no toggle)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Sign Out Button
                        IconButton(
                            onClick = onLogout,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )

            // Tab Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabButton(
                    icon = Icons.Default.Dashboard,
                    text = "Dashboard",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                TabButton(
                    icon = Icons.Default.DirectionsCar,
                    text = "Bookings",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                TabButton(
                    icon = Icons.Default.History,
                    text = "History",
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                TabButton(
                    icon = Icons.Default.AccountBalanceWallet,
                    text = "Contributions",
                    isSelected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                TabButton(
                    icon = Icons.Default.VpnKey,
                    text = "RFID",
                    isSelected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }

            // Show loading state
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Content based on selected tab
                when (selectedTab) {
                    0 -> DashboardContent(
                        isOnline = isOnline,
                        todaysTrips = todaysTrips,
                        todaysEarnings = todaysEarnings,
                        driverRating = driverRating,
                        onViewDocuments = { /* No-op */ }
                    )
                    1 -> BookingsContent(
                        myBookings = myBookings,
                        availableBookings = availableBookings,
                        isOnline = isOnline,
                        onAcceptBooking = { booking ->
                            viewModel.acceptBooking(booking.id, user.id)
                        },
                        onUpdateBookingStatus = { bookingId, status ->
                            viewModel.updateBookingStatusOnly(bookingId, status)
                        },
                        onChatClick = { booking ->
                            activeChatBooking = booking
                            showChat = true
                        }
                    )
                    2 -> HistoryContent(
                        completedBookings = completedBookings
                    )
                    3 -> ContributionsContent(
                        userId = user.id,
                        viewModel = viewModel
                    )
                    4 -> {
                        // RFID Management Content - Use the dedicated screen
                        RfidManagementScreen(
                            driverId = user.id,
                            driverName = user.name,
                            currentRfidUID = driverRFID,
                            rfidHistory = rfidHistory,
                            onBack = { selectedTab = 0 }, // Go back to dashboard
                            onReportMissing = { reason ->
                                coroutineScope.launch {
                                    viewModel.reportMissingRfid(user.id, reason).fold(
                                        onSuccess = {
                                            rfidActionMessage = "RFID reported as missing successfully. Please contact admin for a new card." to true
                                            // Reload RFID and history
                                            driverRFID = ""
                                            viewModel.getRfidChangeHistory(user.id).fold(
                                                onSuccess = { history ->
                                                    rfidHistory = history
                                                },
                                                onFailure = { error ->
                                                    println("Error reloading RFID history: ${error.message}")
                                                }
                                            )
                                        },
                                        onFailure = { error ->
                                            rfidActionMessage = "Failed to report missing RFID: ${error.message}" to false
                                        }
                                    )
                                }
                            },
                            onRefresh = {
                                coroutineScope.launch {
                                    rfidHistoryLoading = true
                                    // Reload driver RFID
                                    viewModel.getDriverById(user.id).fold(
                                        onSuccess = { driverData ->
                                            driverRFID = driverData["rfidUID"] as? String ?: ""
                                        },
                                        onFailure = { error ->
                                            println("Error getting driver RFID: ${error.message}")
                                        }
                                    )
                                    // Reload RFID history
                                    viewModel.getRfidChangeHistory(user.id).fold(
                                        onSuccess = { history ->
                                            rfidHistory = history
                                            rfidHistoryLoading = false
                                        },
                                        onFailure = { error ->
                                            println("Error loading RFID history: ${error.message}")
                                            rfidHistoryLoading = false
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Leave Queue Button - appears at the bottom when driver is online
        if (isOnline && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        // Show confirmation dialog
                        showLeaveQueueDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Leave Queue",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Leave Queue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Chat screen overlay - now properly positioned as a full-screen overlay
        if (showChat && activeChatBooking != null) {
            SimpleChatScreen(
                user = user,
                booking = activeChatBooking!!,
                onBack = { showChat = false },
                viewModel = viewModel
            )
        }

        // Leave queue confirmation dialog
        if (showLeaveQueueDialog) {
            val coroutineScope = rememberCoroutineScope()

            AlertDialog(
                onDismissRequest = { showLeaveQueueDialog = false },
                title = { Text("Leave Queue") },
                text = { Text("Are you sure you want to leave the queue?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Confirm leave queue action
                            showLeaveQueueDialog = false
                            // Perform the leave queue operation
                            coroutineScope.launch {
                                val result = viewModel.leaveQueue(driverRFID)
                                result.fold(
                                    onSuccess = { success ->
                                        if (success) {
                                            println("✓ Successfully left the queue")
                                        } else {
                                            println("✗ Failed to leave queue - driver not found in queue")
                                        }
                                    },
                                    onFailure = { error ->
                                        println("✗ Error leaving queue: ${error.message}")
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLeaveQueueDialog = false }
                    ) {
                        Text("No")
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}

@Composable
fun TabButton(
    icon: ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isSelected) Color(0xFF1976D2) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isSelected) Color(0xFF1976D2) else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun DashboardContent(
    isOnline: Boolean,
    todaysTrips: Int,
    todaysEarnings: Double,
    driverRating: Double,
    onViewDocuments: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Driver Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Driver Status",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            text = if (isOnline) "You are online" else "You are offline",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        item {
            // Today's Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Today's Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryItem(
                            icon = Icons.Default.DirectionsCar,
                            value = todaysTrips.toString(),
                            label = "Trips",
                            iconColor = Color(0xFF1976D2)
                        )
                        SummaryItemSymbol(
                            symbol = "₱",
                            value = String.format(Locale.US, "%.2f", todaysEarnings),
                            label = "Earnings",
                            iconColor = Color(0xFF4CAF50)
                        )
                        SummaryItem(
                            icon = Icons.Default.Star,
                            value = if (driverRating < 0) "--" else String.format(Locale.US, "%.1f", driverRating),
                            label = "Rating",
                            iconColor = Color(0xFFFF9800)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun SummaryItemSymbol(
    symbol: String,
    value: String,
    label: String,
    iconColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = symbol,
            color = iconColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun BookingsContent(
    myBookings: List<Booking>,
    availableBookings: List<Booking>,
    isOnline: Boolean,
    onAcceptBooking: (Booking) -> Unit,
    onUpdateBookingStatus: (String, String) -> Unit,
    onChatClick: (Booking) -> Unit
) {
    val viewModel: EnhancedBookingViewModel = hiltViewModel()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // My Active Bookings
        if (myBookings.isNotEmpty()) {
            item {
                Text(
                    text = "Active Trips",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(myBookings) { booking ->
                BookingMonitoringCard(
                    booking = booking,
                    onStatusUpdate = onUpdateBookingStatus,
                    onChatClick = onChatClick,
                    viewModel = viewModel
                )
            }
        }

        // Available Bookings (only show if online)
        if (isOnline && availableBookings.isNotEmpty()) {
            item {
                Text(
                    text = "Available Bookings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(availableBookings) { booking ->
                AvailableBookingDisplayCard(booking = booking)
            }
        }

        // Empty state
        if (myBookings.isEmpty() && (availableBookings.isEmpty() || !isOnline)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (isOnline) Icons.Default.Schedule else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isOnline) "No bookings available" else "You're offline",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Text(
                            text = if (isOnline) "New bookings will appear here" else "Go online to see available bookings",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookingMonitoringCard(
    booking: Booking,
    onStatusUpdate: (String, String) -> Unit,
    onChatClick: (Booking) -> Unit,
    viewModel: EnhancedBookingViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // State for showing/hiding the No Show button based on arrival time
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    val showNoShowButton = remember(booking.arrivedAtPickup, booking.arrivedAtPickupTime, currentTime.value) {
        booking.arrivedAtPickup &&
        booking.arrivedAtPickupTime > 0L &&
        (currentTime.value - booking.arrivedAtPickupTime) >= 5 * 60 * 1000 // 5 minutes
    }

    // Update timer every minute when driver has arrived
    LaunchedEffect(booking.arrivedAtPickup) {
        if (booking.arrivedAtPickup) {
            while (true) {
                kotlinx.coroutines.delay(60000) // Update every minute
                currentTime.value = System.currentTimeMillis()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with customer name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.customerName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                StatusChip(booking.status.name)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Booking details
            BookingDetailRow("Time", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(booking.timestamp)))
            BookingDetailRow("Pick up", booking.pickupLocation)
            BookingDetailRow("Drop off", booking.destination)
            BookingDetailRow("Fare", formatFare(booking.estimatedFare))

            // Show arrival time if driver has arrived
            if (booking.arrivedAtPickup && booking.arrivedAtPickupTime > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Arrived",
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val waitTime = (currentTime.value - booking.arrivedAtPickupTime) / 60000 // minutes
                    Text(
                        text = "Arrived at pickup • Waiting ${waitTime}m",
                        fontSize = 14.sp,
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First row of buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (booking.status == BookingStatus.ACCEPTED) {
                        // Show "Arrived at Pickup" button if not yet arrived
                        if (!booking.arrivedAtPickup) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.markArrivedAtPickup(booking.id).fold(
                                            onSuccess = {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Marked as arrived at pickup point",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onFailure = { error ->
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Error: ${error.message}",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Arrived at Pickup")
                            }
                        } else {
                            // Show "Start Trip" button after arrival
                            Button(
                                onClick = { onStatusUpdate(booking.id, "IN_PROGRESS") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Trip")
                            }
                        }
                    } else if (booking.status == BookingStatus.IN_PROGRESS) {
                        Button(
                            onClick = { onStatusUpdate(booking.id, "COMPLETED") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete Trip")
                        }
                    }

                    // Chat button
                    IconButton(
                        onClick = { onChatClick(booking) },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1976D2),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat")
                    }
                }

                // Second row: No Show button (only appears after 5 minutes of waiting)
                if (showNoShowButton && booking.status == BookingStatus.ACCEPTED) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.reportNoShow(booking.id).fold(
                                    onSuccess = {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Customer no-show reported",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailure = { error ->
                                        android.widget.Toast.makeText(
                                            context,
                                            "Error: ${error.message}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Report No Show")
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableBookingDisplayCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with customer name
            Text(
                text = booking.customerName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Booking details
            BookingDetailRow("Time", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(booking.timestamp)))
            BookingDetailRow("Pick up", booking.pickupLocation)
            BookingDetailRow("Drop off", booking.destination)
            BookingDetailRow("Fare", formatFare(booking.estimatedFare))
        }
    }
}

@Composable
fun BookingDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

// Formats a fare with two decimal places (e.g., 25.00)
private fun formatFare(amount: Double): String = "₱" + String.format(Locale.US, "%.2f", amount)

@Composable
fun StatusChip(status: String) {
    Surface(
        color = when (status) {
            "ACCEPTED" -> Color(0xFF4CAF50)
            "IN_PROGRESS" -> Color(0xFFFF9800)
            "COMPLETED" -> Color(0xFF2196F3)
            else -> Color.Gray
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HistoryContent(
    completedBookings: List<Booking>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Booking History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (completedBookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No trip history",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Text(
                            text = "Completed trips will appear here",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(completedBookings) { booking ->
                HistoryBookingCard(booking)
            }
        }
    }
}

@Composable
fun HistoryBookingCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with customer name and fare
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.customerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(booking.timestamp)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₱${booking.estimatedFare.toInt()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(booking.timestamp)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pickup location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pick up",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = booking.pickupLocation,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dropoff location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFD32F2F)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Drop off",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = booking.destination,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }

            // Additional info
            if (booking.todaNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TODA #${booking.todaNumber}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (booking.verificationCode.isNotEmpty()) {
                        Text(
                            text = "Code: ${booking.verificationCode}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContributionsContent(
    userId: String,
    viewModel: EnhancedBookingViewModel
) {
    // Simply use the existing DriverContributionsScreen
    DriverContributionsScreen(
        driverId = userId,
        viewModel = viewModel
    )
}
