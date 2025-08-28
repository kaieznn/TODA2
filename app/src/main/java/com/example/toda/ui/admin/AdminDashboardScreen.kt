package com.example.toda.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onDriverManagement: () -> Unit,
    onTricycleManagement: () -> Unit,
    onBookingOverview: () -> Unit,
    onReportsAnalytics: () -> Unit,
    onSystemSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "TODA Admin Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Welcome Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Welcome to TODA Administration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Barangay 177, Camarin, Caloocan City",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manage driver applications, tricycle registrations, and monitor system operations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Admin Functions Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(getAdminFunctions()) { function ->
                AdminFunctionCard(
                    adminFunction = function,
                    onClick = {
                        when (function.id) {
                            "driver_management" -> onDriverManagement()
                            "tricycle_management" -> onTricycleManagement()
                            "booking_overview" -> onBookingOverview()
                            "reports_analytics" -> onReportsAnalytics()
                            "system_settings" -> onSystemSettings()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TODA Management System v1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "For administrative use only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdminFunctionCard(
    adminFunction: AdminFunction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = adminFunction.icon,
                contentDescription = adminFunction.title,
                modifier = Modifier.size(48.dp),
                tint = adminFunction.iconColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = adminFunction.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = adminFunction.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

private data class AdminFunction(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color = Color.Unspecified
)

private fun getAdminFunctions(): List<AdminFunction> {
    return listOf(
        AdminFunction(
            id = "driver_management",
            title = "Driver Applications",
            description = "Review and approve new driver registrations",
            icon = Icons.Default.Person,
            iconColor = Color.Unspecified // Will use theme color in Composable context
        ),
        AdminFunction(
            id = "tricycle_management",
            title = "Tricycle Registry",
            description = "Manage tricycle registrations and TODA numbers",
            icon = Icons.Default.DirectionsCar,
            iconColor = Color.Unspecified
        ),
        AdminFunction(
            id = "booking_overview",
            title = "Booking Overview",
            description = "Monitor active bookings and trip history",
            icon = Icons.Default.Assignment,
            iconColor = Color.Unspecified
        ),
        AdminFunction(
            id = "reports_analytics",
            title = "Reports & Analytics",
            description = "View system statistics and performance metrics",
            icon = Icons.Default.Analytics,
            iconColor = Color.Unspecified
        ),
        AdminFunction(
            id = "system_settings",
            title = "System Settings",
            description = "Configure system parameters and preferences",
            icon = Icons.Default.Settings,
            iconColor = Color.Unspecified
        ),
        AdminFunction(
            id = "emergency_alerts",
            title = "Emergency Alerts",
            description = "Monitor and respond to emergency situations",
            icon = Icons.Default.Emergency,
            iconColor = Color.Unspecified
        )
    )
}
