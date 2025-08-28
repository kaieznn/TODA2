package com.example.toda.ui.operator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toda.data.*
import com.example.toda.service.RegistrationService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverApplicationManagementScreen(
    registrationService: RegistrationService,
    currentUser: User,
    onBack: () -> Unit
) {
    var currentView by remember { mutableStateOf("pending") } // "pending", "all", "details"
    var selectedApplication by remember { mutableStateOf<DriverRegistration?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    var pendingApplications by remember { mutableStateOf(registrationService.getPendingDriverApplications()) }
    var allApplications by remember { mutableStateOf(registrationService.getAllDriverApplications()) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Refresh data
    fun refreshData() {
        pendingApplications = registrationService.getPendingDriverApplications()
        allApplications = registrationService.getAllDriverApplications()
    }

    when (currentView) {
        "pending", "all" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Driver Applications",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Success/Error messages
                successMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                errorMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Tab selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentView == "pending",
                        onClick = { currentView = "pending" },
                        label = { Text("Pending (${pendingApplications.size})") },
                        leadingIcon = if (currentView == "pending") {
                            { Icon(Icons.Default.Pending, contentDescription = null) }
                        } else null
                    )

                    FilterChip(
                        selected = currentView == "all",
                        onClick = { currentView = "all" },
                        label = { Text("All Applications (${allApplications.size})") },
                        leadingIcon = if (currentView == "all") {
                            { Icon(Icons.Default.List, contentDescription = null) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Applications list
                val applicationsToShow = if (currentView == "pending") pendingApplications else allApplications

                if (applicationsToShow.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = "No applications",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (currentView == "pending") "No pending applications" else "No applications yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(applicationsToShow) { application ->
                            DriverApplicationCard(
                                application = application,
                                registrationService = registrationService,
                                onViewDetails = {
                                    selectedApplication = application
                                    currentView = "details"
                                },
                                onApprove = {
                                    coroutineScope.launch {
                                        val success = registrationService.approveDriverRegistration(
                                            application.id,
                                            currentUser.id
                                        )
                                        if (success) {
                                            successMessage = "Application approved successfully!"
                                            errorMessage = null
                                            refreshData()
                                        } else {
                                            errorMessage = "Failed to approve application"
                                            successMessage = null
                                        }
                                        // Clear messages after delay
                                        kotlinx.coroutines.delay(3000)
                                        successMessage = null
                                        errorMessage = null
                                    }
                                },
                                onReject = {
                                    selectedApplication = application
                                    showRejectDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
        "details" -> {
            selectedApplication?.let { application ->
                DriverApplicationDetailsScreen(
                    application = application,
                    registrationService = registrationService,
                    onBack = { currentView = "pending" },
                    onApprove = {
                        coroutineScope.launch {
                            val success = registrationService.approveDriverRegistration(
                                application.id,
                                currentUser.id
                            )
                            if (success) {
                                successMessage = "Application approved successfully!"
                                refreshData()
                                currentView = "pending"
                            } else {
                                errorMessage = "Failed to approve application"
                            }
                        }
                    },
                    onReject = {
                        showRejectDialog = true
                    }
                )
            }
        }
    }

    // Rejection dialog
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Application") },
            text = {
                Column {
                    Text("Please provide a reason for rejecting this application:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Rejection Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedApplication?.let { application ->
                            coroutineScope.launch {
                                val success = registrationService.rejectDriverRegistration(
                                    application.id,
                                    rejectionReason,
                                    currentUser.id
                                )
                                if (success) {
                                    successMessage = "Application rejected"
                                    refreshData()
                                } else {
                                    errorMessage = "Failed to reject application"
                                }
                                showRejectDialog = false
                                rejectionReason = ""
                                currentView = "pending"
                            }
                        }
                    },
                    enabled = rejectionReason.isNotBlank()
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRejectDialog = false
                        rejectionReason = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DriverApplicationCard(
    application: DriverRegistration,
    registrationService: RegistrationService,
    onViewDetails: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    text = application.applicantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusBadge(status = application.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            ApplicationDetailRow("Phone:", application.phoneNumber)
            ApplicationDetailRow("License:", application.licenseNumber)
            ApplicationDetailRow("TODA Number:", application.todaNumber)

            val tricycle = registrationService.getTricycleById(application.tricycleId)
            ApplicationDetailRow("Tricycle:", tricycle?.plateNumber ?: "Unknown")

            ApplicationDetailRow(
                "Applied:",
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(Date(application.applicationDate))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Document status
            DocumentStatusRow(application)

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Details")
                }

                if (application.status == DriverApplicationStatus.PENDING) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject")
                    }

                    Button(
                        onClick = onApprove
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DocumentStatusRow(application: DriverRegistration) {
    Column {
        Text(
            text = "Documents:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DocumentStatusChip("License", application.hasValidLicense)
            DocumentStatusChip("Barangay", application.hasBarangayClearance)
            DocumentStatusChip("Police", application.hasPoliceClearance)
            DocumentStatusChip("Medical", application.hasMedicalCertificate)
        }
    }
}

@Composable
private fun DocumentStatusChip(label: String, hasDocument: Boolean) {
    Surface(
        color = if (hasDocument) Color(0xFF4CAF50) else Color(0xFFFF9800),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun StatusBadge(status: DriverApplicationStatus) {
    val (backgroundColor, textColor) = when (status) {
        DriverApplicationStatus.PENDING -> Color(0xFFFF9800) to Color.White
        DriverApplicationStatus.APPROVED -> Color(0xFF4CAF50) to Color.White
        DriverApplicationStatus.REJECTED -> Color(0xFFF44336) to Color.White
        DriverApplicationStatus.UNDER_REVIEW -> Color(0xFF2196F3) to Color.White
        DriverApplicationStatus.REQUIRES_DOCUMENTS -> Color(0xFF9C27B0) to Color.White
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.name.replace("_", " "),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun DriverApplicationDetailsScreen(
    application: DriverRegistration,
    registrationService: RegistrationService,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Application Details",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Applicant Information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Applicant Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("Name:", application.applicantName)
                DetailRow("Phone Number:", application.phoneNumber)
                DetailRow("Address:", application.address)
                DetailRow("Emergency Contact:", application.emergencyContact)
                DetailRow("Years of Experience:", "${application.yearsOfExperience} years")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // License Information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "License Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("License Number:", application.licenseNumber)
                DetailRow(
                    "License Expiry:",
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(application.licenseExpiry))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TODA & Tricycle Information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "TODA & Tricycle Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("TODA Number:", application.todaNumber)

                val tricycle = registrationService.getTricycleById(application.tricycleId)
                if (tricycle != null) {
                    DetailRow("Tricycle Plate:", tricycle.plateNumber)
                    DetailRow("TODA Number:", tricycle.todaNumber)
                    DetailRow("Owner:", tricycle.ownerName)
                    DetailRow("Registered Drivers:", "${tricycle.registeredDrivers.size}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document Checklist
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Required Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                DocumentCheckRow("Valid Driver's License", application.hasValidLicense)
                DocumentCheckRow("Barangay Clearance", application.hasBarangayClearance)
                DocumentCheckRow("Police Clearance", application.hasPoliceClearance)
                DocumentCheckRow("Medical Certificate", application.hasMedicalCertificate)
                DocumentCheckRow("Driver Training Certificate", application.hasDriverTrainingCertificate)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Application Status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Application Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status = application.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Applied on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(application.applicationDate))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (application.approvedBy != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(
                        "Processed by:",
                        application.approvedBy!!
                    )
                    application.approvalDate?.let { date ->
                        DetailRow(
                            "Processed on:",
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(date))
                        )
                    }
                }

                application.rejectionReason?.let { reason ->
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Rejection Reason:", reason)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        if (application.status == DriverApplicationStatus.PENDING) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reject Application")
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Approve Application")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun DocumentCheckRow(label: String, hasDocument: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasDocument) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = if (hasDocument) "Available" else "Missing",
            tint = if (hasDocument) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
