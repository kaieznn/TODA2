package com.example.toda.ui.operator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toda.data.Booking
import com.example.toda.data.BookingStatus
import com.example.toda.data.NotificationState
import com.example.toda.service.DriverTrackingService
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import com.example.toda.viewmodel.EnhancedBookingViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorInterface(
    bookings: List<Booking>,
    notificationState: NotificationState,
    driverTrackingService: DriverTrackingService,
    onBookingUpdate: (List<Booking>) -> Unit,
    onBack: () -> Unit,
    onNotificationStateChange: (NotificationState) -> Unit,
    enhancedBookingViewModel: EnhancedBookingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Use real Firebase bookings instead of local bookings
    val firebaseBookings by enhancedBookingViewModel.activeBookings.collectAsStateWithLifecycle()
    val pendingBookings = firebaseBookings.filter { it.status == BookingStatus.PENDING }
    val bookingState by enhancedBookingViewModel.bookingState.collectAsStateWithLifecycle()

    fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle permission or other errors
        }
    }

    fun acceptBooking(booking: Booking) {
        // Update booking status to ACCEPTED using Firebase
        enhancedBookingViewModel.updateBookingStatusOnly(booking.id, "ACCEPTED")

        // Hardware system will handle driver assignment and tracking
    }

    fun rejectBooking(booking: Booking) {
        // Update booking status to CANCELLED using Firebase
        enhancedBookingViewModel.updateBookingStatusOnly(booking.id, "CANCELLED")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OperatorTopBar(
            onBack = onBack,
            notificationState = notificationState,
            onNotificationToggle = { isEnabled ->
                onNotificationStateChange(
                    notificationState.copy(isOperatorOnline = isEnabled)
                )
            }
        )

        NotificationStatusCard(notificationState)

        PrivacyNotice()

        BookingsList(
            pendingBookings = pendingBookings,
            onAccept = ::acceptBooking,
            onReject = ::rejectBooking,
            onCall = ::makeCall
        )
    }
}

@Composable
private fun OperatorTopBar(
    onBack: () -> Unit,
    notificationState: NotificationState,
    onNotificationToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Text(
            text = "Operator Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        IconButton(
            onClick = { onNotificationToggle(!notificationState.isOperatorOnline) }
        ) {
            Icon(
                if (notificationState.isOperatorOnline) Icons.Default.Notifications
                else Icons.Default.NotificationsOff,
                contentDescription = if (notificationState.isOperatorOnline) "Online" else "Offline"
            )
        }
    }
}

@Composable
private fun NotificationStatusCard(notificationState: NotificationState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Status: ${if (notificationState.isOperatorOnline) "Online" else "Offline"}",
                style = MaterialTheme.typography.titleMedium,
                color = if (notificationState.isOperatorOnline) Color.Green else Color.Red
            )
            Text(
                text = "Ready to receive booking notifications",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PrivacyNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Privacy Notice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Customer phone numbers are provided for booking coordination only. Please respect customer privacy.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BookingsList(
    pendingBookings: List<Booking>,
    onAccept: (Booking) -> Unit,
    onReject: (Booking) -> Unit,
    onCall: (String) -> Unit
) {
    Text(
        text = "Pending Bookings (${pendingBookings.size})",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    if (pendingBookings.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "No pending bookings",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pendingBookings) { booking ->
                BookingDetailCard(
                    booking = booking,
                    onAccept = { onAccept(booking) },
                    onReject = { onReject(booking) },
                    onCallCustomer = onCall
                )
            }
        }
    }
}

@Composable
private fun BookingDetailCard(
    booking: Booking,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCallCustomer: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Customer: ${booking.customerName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "From: ${booking.pickupLocation}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "To: ${booking.destination}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Fare: ₱${String.format(Locale.getDefault(), "%.2f", booking.estimatedFare)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Time: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(booking.timestamp))}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept")
                }

                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }

                Button(
                    onClick = { onCallCustomer(booking.phoneNumber) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call")
                }
            }
        }
    }
}