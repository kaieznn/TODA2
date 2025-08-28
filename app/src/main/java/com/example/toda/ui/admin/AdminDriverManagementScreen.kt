package com.example.toda.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.DriverInfo
import com.example.toda.viewmodel.AdminDriverManagementViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDriverManagementScreen(
    onBack: () -> Unit,
    viewModel: AdminDriverManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allDrivers by viewModel.allDrivers.collectAsStateWithLifecycle()

    var selectedDriver by remember { mutableStateOf<DriverInfo?>(null) }
    var showDriverDetails by remember { mutableStateOf(false) }
    var showRfidAssignmentDialog by remember { mutableStateOf(false) }
    var rfidInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAllDrivers()
    }

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
                text = "Driver Management",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    title = "Total Drivers",
                    count = allDrivers.size,
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    title = "Need RFID",
                    count = allDrivers.count { it.needsRfidAssignment },
                    color = MaterialTheme.colorScheme.secondary
                )
                StatItem(
                    title = "Active",
                    count = allDrivers.count { it.isActive },
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Success/Error messages
        uiState.successMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        uiState.errorMessage?.let { error ->
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
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Loading indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Drivers List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allDrivers) { driver ->
                    DriverCard(
                        driver = driver,
                        onDriverClick = {
                            selectedDriver = driver
                            showDriverDetails = true
                        },
                        onAssignRfid = {
                            selectedDriver = driver
                            rfidInput = ""
                            showRfidAssignmentDialog = true
                        }
                    )
                }
            }
        }
    }

    // RFID Assignment Dialog
    if (showRfidAssignmentDialog && selectedDriver != null) {
        AlertDialog(
            onDismissRequest = { showRfidAssignmentDialog = false },
            title = { Text("Assign RFID Card") },
            text = {
                Column {
                    Text("Assign RFID card to ${selectedDriver!!.driverName}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = rfidInput,
                        onValueChange = { rfidInput = it.uppercase() },
                        label = { Text("RFID UID") },
                        placeholder = { Text("e.g., 2A8B5505") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rfidInput.isNotBlank()) {
                            viewModel.assignRfidToDriver(selectedDriver!!.driverId, rfidInput)
                            showRfidAssignmentDialog = false
                        }
                    },
                    enabled = rfidInput.isNotBlank()
                ) {
                    Text("Assign")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRfidAssignmentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Driver Details Dialog
    if (showDriverDetails && selectedDriver != null) {
        DriverDetailsDialog(
            driver = selectedDriver!!,
            onDismiss = { showDriverDetails = false },
            onAssignRfid = {
                showDriverDetails = false
                rfidInput = ""
                showRfidAssignmentDialog = true
            }
        )
    }
}

@Composable
fun DriverCard(
    driver: DriverInfo,
    onDriverClick: () -> Unit,
    onAssignRfid: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDriverClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.driverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TODA: ${driver.todaNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = driver.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    // RFID Status
                    if (driver.rfidUID.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = "RFID: ${driver.rfidUID}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        Button(
                            onClick = onAssignRfid,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Assign RFID",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverDetailsDialog(
    driver: DriverInfo,
    onDismiss: () -> Unit,
    onAssignRfid: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Driver Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Driver Info
                InfoRow("Name", driver.driverName)
                InfoRow("Phone Number", driver.phoneNumber)
                InfoRow("TODA Number", driver.todaNumber)
                InfoRow("Address", driver.address)
                InfoRow("Emergency Contact", driver.emergencyContact)
                InfoRow("License Number", driver.licenseNumber)
                InfoRow("Years of Experience", driver.yearsOfExperience.toString())

                if (driver.rfidUID.isNotEmpty()) {
                    InfoRow("RFID UID", driver.rfidUID)
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onAssignRfid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Assign RFID Card")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun StatItem(
    title: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
