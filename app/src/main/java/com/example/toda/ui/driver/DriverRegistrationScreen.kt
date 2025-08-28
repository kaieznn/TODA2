package com.example.toda.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.DriverRegistration
import com.example.toda.viewmodel.DriverRegistrationViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRegistrationScreen(
    onBack: () -> Unit,
    onRegistrationComplete: () -> Unit,
    viewModel: DriverRegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Form state
    var applicantName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var licenseExpiry by remember { mutableStateOf("") }
    var yearsOfExperience by remember { mutableStateOf("") }
    var tricycleId by remember { mutableStateOf("") }

    // Document checkboxes
    var hasValidLicense by remember { mutableStateOf(false) }
    var hasBarangayClearance by remember { mutableStateOf(false) }
    var hasPoliceClearance by remember { mutableStateOf(false) }
    var hasMedicalCertificate by remember { mutableStateOf(false) }
    var hasDriverTrainingCertificate by remember { mutableStateOf(false) }

    // Handle registration success
    LaunchedEffect(uiState.isRegistrationSuccessful) {
        if (uiState.isRegistrationSuccessful) {
            onRegistrationComplete()
        }
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
                text = "Driver Registration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicator
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Error message
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
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Registration Form
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Applicant Name
                OutlinedTextField(
                    value = applicantName,
                    onValueChange = { applicantName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Phone Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Complete Address") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Emergency Contact
                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text("Emergency Contact Number") },
                    leadingIcon = { Icon(Icons.Default.Emergency, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "License Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // License Number
                OutlinedTextField(
                    value = licenseNumber,
                    onValueChange = { licenseNumber = it },
                    label = { Text("Driver's License Number") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // License Expiry
                OutlinedTextField(
                    value = licenseExpiry,
                    onValueChange = { licenseExpiry = it },
                    label = { Text("License Expiry Date (MM/dd/yyyy)") },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("12/31/2025") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Years of Experience
                OutlinedTextField(
                    value = yearsOfExperience,
                    onValueChange = { yearsOfExperience = it },
                    label = { Text("Years of Driving Experience") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Vehicle Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tricycle ID (if they have one)
                OutlinedTextField(
                    value = tricycleId,
                    onValueChange = { tricycleId = it },
                    label = { Text("Tricycle ID (if you own one)") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Leave blank if you don't own a tricycle") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Required Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Please check the documents you have ready for submission:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Document Checkboxes
                DocumentCheckbox(
                    checked = hasValidLicense,
                    onCheckedChange = { hasValidLicense = it },
                    text = "Valid Driver's License"
                )

                DocumentCheckbox(
                    checked = hasBarangayClearance,
                    onCheckedChange = { hasBarangayClearance = it },
                    text = "Barangay Clearance"
                )

                DocumentCheckbox(
                    checked = hasPoliceClearance,
                    onCheckedChange = { hasPoliceClearance = it },
                    text = "Police Clearance"
                )

                DocumentCheckbox(
                    checked = hasMedicalCertificate,
                    onCheckedChange = { hasMedicalCertificate = it },
                    text = "Medical Certificate"
                )

                DocumentCheckbox(
                    checked = hasDriverTrainingCertificate,
                    onCheckedChange = { hasDriverTrainingCertificate = it },
                    text = "Driver Training Certificate"
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Submit Button
                Button(
                    onClick = {
                        val registration = DriverRegistration(
                            applicantName = applicantName,
                            phoneNumber = phoneNumber,
                            address = address,
                            emergencyContact = emergencyContact,
                            licenseNumber = licenseNumber,
                            licenseExpiry = parseDateString(licenseExpiry),
                            yearsOfExperience = yearsOfExperience.toIntOrNull() ?: 0,
                            tricycleId = tricycleId,
                            hasValidLicense = hasValidLicense,
                            hasBarangayClearance = hasBarangayClearance,
                            hasPoliceClearance = hasPoliceClearance,
                            hasMedicalCertificate = hasMedicalCertificate,
                            hasDriverTrainingCertificate = hasDriverTrainingCertificate
                        )
                        viewModel.submitRegistration(registration)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && isFormValid(
                        applicantName, phoneNumber, address, emergencyContact,
                        licenseNumber, licenseExpiry, yearsOfExperience
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Submit Application")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Note: Your application will be reviewed by TODA administrators. " +
                            "You will be notified of the approval status via SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

private fun isFormValid(
    name: String,
    phone: String,
    address: String,
    emergency: String,
    license: String,
    expiry: String,
    experience: String
): Boolean {
    return name.isNotBlank() &&
            phone.isNotBlank() &&
            address.isNotBlank() &&
            emergency.isNotBlank() &&
            license.isNotBlank() &&
            expiry.isNotBlank() &&
            experience.isNotBlank() &&
            isValidDate(expiry) &&
            experience.toIntOrNull() != null
}

private fun isValidDate(dateString: String): Boolean {
    return try {
        val format = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        format.isLenient = false // Strict parsing
        val date = format.parse(dateString)
        val currentDate = Date()
        date != null && date.after(currentDate) // Must be a future date
    } catch (e: Exception) {
        false
    }
}

private fun parseDateString(dateString: String): Long {
    return try {
        val format = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        format.isLenient = false // Strict parsing
        val date = format.parse(dateString)
        if (date != null && date.after(Date())) {
            date.time
        } else {
            throw Exception("Invalid or past date")
        }
    } catch (e: Exception) {
        println("Date parsing error for '$dateString': ${e.message}")
        0L
    }
}
