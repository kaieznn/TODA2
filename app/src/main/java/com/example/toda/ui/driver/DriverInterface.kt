package com.example.toda.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.*
import com.example.toda.ui.chat.SimpleChatScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverInterface(
    user: User,
    onLogout: () -> Unit,
    viewModel: EnhancedBookingViewModel = hiltViewModel()
) {
    val activeBookings by viewModel.activeBookings.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0=Dashboard, 1=Bookings, 2=History

    // Chat state management
    var showChat by remember { mutableStateOf(false) }
    var activeChatBooking by remember { mutableStateOf<Booking?>(null) }

    // Driver status and stats from Firebase
    var isOnline by remember { mutableStateOf(false) }
    var todaysTrips by remember { mutableStateOf(0) }
    var todaysEarnings by remember { mutableStateOf(0.0) }
    var driverRating by remember { mutableStateOf(5.0) }
    var isLoading by remember { mutableStateOf(true) }
    var driverRFID by remember { mutableStateOf("") }

    // Load driver contribution status, today's stats, and RFID
    LaunchedEffect(user.id) {
        try {
            // Get driver RFID from the drivers table
            val driverResult = viewModel.getDriverById(user.id)
            driverResult.fold(
                onSuccess = { driverData ->
                    driverRFID = driverData["rfidUID"] as? String ?: ""
                    println("Driver ${user.id} RFID: $driverRFID")
                },
                onFailure = { error ->
                    println("Error getting driver RFID: ${error.message}")
                    driverRFID = ""
                }
            )

            // Check if driver has contributed today (determines online status)
            val contributionResult = viewModel.getDriverContributionStatus(user.id)
            contributionResult.fold(
                onSuccess = { hasContributed ->
                    isOnline = hasContributed
                    println("Driver ${user.id} contribution status: $hasContributed")
                },
                onFailure = { error ->
                    println("Error checking contribution status: ${error.message}")
                    isOnline = false
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
        booking.status == BookingStatus.PENDING && isOnline // Only show if driver is online (contributed)
    }

    // Get completed bookings for history (from all bookings that are completed by this driver)
    val completedBookings = activeBookings.filter { booking ->
        booking.assignedDriverId == user.id && booking.status == BookingStatus.COMPLETED
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
                            Icons.Default.ArrowBack,
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
                        IconButton(onClick = { /* Menu action */ }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.Black
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
                        driverRating = driverRating
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
    driverRating: Double
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
                        SummaryItem(
                            icon = Icons.Default.AttachMoney,
                            value = "₱${todaysEarnings.toInt()}",
                            label = "Earnings",
                            iconColor = Color(0xFF4CAF50)
                        )
                        SummaryItem(
                            icon = Icons.Default.Star,
                            value = driverRating.toString(),
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
fun BookingsContent(
    myBookings: List<Booking>,
    availableBookings: List<Booking>,
    isOnline: Boolean,
    onAcceptBooking: (Booking) -> Unit,
    onUpdateBookingStatus: (String, String) -> Unit,
    onChatClick: (Booking) -> Unit
) {
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
                    onChatClick = onChatClick
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
    onChatClick: (Booking) -> Unit
) {
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
            BookingDetailRow("Fare", "₱${booking.estimatedFare}")

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons (only for monitoring, no accept/reject)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (booking.status == BookingStatus.ACCEPTED) {
                    Button(
                        onClick = { onStatusUpdate(booking.id, "IN_PROGRESS") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Text("Start Trip")
                    }
                } else if (booking.status == BookingStatus.IN_PROGRESS) {
                    Button(
                        onClick = { onStatusUpdate(booking.id, "COMPLETED") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
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
                    Icon(Icons.Default.Chat, contentDescription = "Chat")
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
            BookingDetailRow("Fare", "₱${booking.estimatedFare}")
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
fun HistoryContent(completedBookings: List<Booking>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Booking History",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(vertical = 8.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = booking.customerName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = "₱${booking.estimatedFare}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${booking.pickupLocation} → ${booking.destination}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(booking.timestamp)),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
