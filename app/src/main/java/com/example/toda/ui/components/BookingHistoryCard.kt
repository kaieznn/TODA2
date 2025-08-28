package com.example.toda.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toda.data.Booking
import com.example.toda.data.BookingStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookingHistoryCard(
    booking: Booking,
    isCustomerView: Boolean = true,
    modifier: Modifier = Modifier
) {
    val statusColor = when (booking.status) {
        BookingStatus.COMPLETED -> Color(0xFF4CAF50) // Green
        BookingStatus.CANCELLED -> Color(0xFFF44336) // Red
        BookingStatus.REJECTED -> Color(0xFFFF9800)  // Orange - Fixed this line
        else -> MaterialTheme.colorScheme.primary
    }

    val statusIcon = when (booking.status) {
        BookingStatus.COMPLETED -> Icons.Default.CheckCircle
        BookingStatus.CANCELLED -> Icons.Default.Cancel
        BookingStatus.REJECTED -> Icons.Default.Close
        else -> Icons.Default.Schedule
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status and Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = booking.status.name,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = booking.status.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        .format(Date(booking.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer/Driver Info
            if (!isCustomerView) {
                Text(
                    text = "Customer: ${booking.customerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Phone: ${booking.phoneNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Trip Details
            Text(
                text = "From: ${booking.pickupLocation}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "To: ${booking.destination}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fare and Additional Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fare: ₱${String.format("%.2f", booking.estimatedFare)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (booking.assignedTricycleId.isNotEmpty()) {
                    Text(
                        text = "Tricycle: ${booking.assignedTricycleId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}