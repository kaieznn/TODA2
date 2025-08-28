package com.example.toda.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.toda.data.DriverRegistration
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverApplicationDetailsDialog(
    application: DriverRegistration,
    onDismiss: () -> Unit,
    onApprove: (DriverRegistration, String) -> Unit,
    onReject: (DriverRegistration, String) -> Unit,
    onMarkUnderReview: (DriverRegistration) -> Unit,
    onRequestDocuments: (DriverRegistration) -> Unit
) {
    var todaNumber by remember { mutableStateOf("") }
    var rejectionReason by remember { mutableStateOf("") }
    var showApprovalDialog by remember { mutableStateOf(false) }
    var showRejectionDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
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
                        text = "Driver Application",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Personal Information Section
                SectionCard(title = "Personal Information") {
                    DetailRow("Full Name", application.applicantName)
                    DetailRow("Phone Number", application.phoneNumber)
                    DetailRow("Address", application.address)
                    DetailRow("Emergency Contact", application.emergencyContact)
                    DetailRow("Application Date", formatDate(application.applicationDate))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // License Information Section
                SectionCard(title = "License Information") {
                    DetailRow("License Number", application.licenseNumber)
                    DetailRow("License Expiry", formatDate(application.licenseExpiry))
                    DetailRow("Years of Experience", "${application.yearsOfExperience} years")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle Information Section
                SectionCard(title = "Vehicle Information") {
                    DetailRow(
                        "Tricycle ID",
                        if (application.tricycleId.isNotEmpty()) application.tricycleId else "None (will be assigned)"
                    )
                    DetailRow(
                        "Current TODA Number",
                        if (application.todaNumber.isNotEmpty()) application.todaNumber else "Not assigned"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Documents Section
                SectionCard(title = "Required Documents") {
                    DocumentStatusItem("Valid Driver's License", application.hasValidLicense)
                    DocumentStatusItem("Barangay Clearance", application.hasBarangayClearance)
                    DocumentStatusItem("Police Clearance", application.hasPoliceClearance)
                    DocumentStatusItem("Medical Certificate", application.hasMedicalCertificate)
                    DocumentStatusItem("Driver Training Certificate", application.hasDriverTrainingCertificate)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Approve Button
                    Button(
                        onClick = { showApprovalDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve")
                    }

                    // Reject Button
                    Button(
                        onClick = { showRejectionDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Under Review Button
                    OutlinedButton(
                        onClick = { onMarkUnderReview(application) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Under Review")
                    }

                    // Request Documents Button
                    OutlinedButton(
                        onClick = { onRequestDocuments(application) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Need Docs")
                    }
                }
            }
        }
    }

    // Approval Dialog
    if (showApprovalDialog) {
        AlertDialog(
            onDismissRequest = { showApprovalDialog = false },
            title = { Text("Approve Application") },
            text = {
                Column {
                    Text("Please assign a TODA number to this driver:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = todaNumber,
                        onValueChange = { todaNumber = it },
                        label = { Text("TODA Number") },
                        placeholder = { Text("e.g., 001, 002, 003") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (todaNumber.isNotBlank()) {
                            onApprove(application, todaNumber)
                            showApprovalDialog = false
                        }
                    },
                    enabled = todaNumber.isNotBlank()
                ) {
                    Text("Approve & Assign")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApprovalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rejection Dialog
    if (showRejectionDialog) {
        AlertDialog(
            onDismissRequest = { showRejectionDialog = false },
            title = { Text("Reject Application") },
            text = {
                Column {
                    Text("Please provide a reason for rejection:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Rejection Reason") },
                        placeholder = { Text("e.g., Incomplete documents, Invalid license") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectionReason.isNotBlank()) {
                            onReject(application, rejectionReason)
                            showRejectionDialog = false
                        }
                    },
                    enabled = rejectionReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DocumentStatusItem(name: String, hasDocument: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (hasDocument) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (hasDocument) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (hasDocument) "Available" else "Missing",
            style = MaterialTheme.typography.bodySmall,
            color = if (hasDocument) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Not specified"
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
