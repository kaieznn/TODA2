package com.example.toda.ui.booking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.*
import com.example.toda.ui.chat.ActiveBookingWithChat
import com.example.toda.ui.chat.ChatFloatingActionButton
import com.example.toda.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveBookingScreen(
    booking: Booking,
    currentUser: User,
    driver: User? = null,
    operator: User? = null,
    onBack: () -> Unit,
    onLocationShare: () -> Unit = {},
    onNavigateToFullChat: () -> Unit,
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Booking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Emergency call button
                    IconButton(onClick = { /* Handle emergency call */ }) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Emergency Call",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ChatFloatingActionButton(
                booking = booking,
                currentUser = currentUser,
                onOpenChat = onNavigateToFullChat
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ActiveBookingWithChat(
                    booking = booking,
                    currentUser = currentUser,
                    driver = driver,
                    operator = operator,
                    onNavigateToFullChat = onNavigateToFullChat,
                    onLocationShare = onLocationShare
                )
            }

            // Additional booking information
            item {
                BookingDetailsCard(booking = booking)
            }

            // Safety information
            item {
                SafetyCard()
            }
        }
    }
}

@Composable
private fun BookingDetailsCard(
    booking: Booking,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Trip Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(
                icon = Icons.Default.LocationOn,
                label = "Pickup",
                value = booking.pickupLocation
            )

            DetailRow(
                icon = Icons.Default.Flag,
                label = "Destination",
                value = booking.destination
            )

            DetailRow(
                icon = Icons.Default.AttachMoney,
                label = "Estimated Fare",
                value = "₱${String.format("%.2f", booking.estimatedFare)}"
            )

            if (booking.verificationCode.isNotEmpty()) {
                DetailRow(
                    icon = Icons.Default.Security,
                    label = "Verification Code",
                    value = booking.verificationCode
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SafetyCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Safety Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Share your trip details with a trusted contact\n" +
                        "• Use the chat to communicate with your driver\n" +
                        "• Report any issues immediately using the emergency button\n" +
                        "• Verify the driver and vehicle details before getting in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
