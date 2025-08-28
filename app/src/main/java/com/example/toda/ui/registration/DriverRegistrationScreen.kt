package com.example.toda.ui.registration

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.toda.data.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRegistrationScreen(
    availableTricycles: List<Tricycle> = emptyList(),
    onRegistrationComplete: (DriverRegistration, String) -> Unit, // registration data + password
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    val maxSteps = 5

    // Basic Information
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }

    // License Information
    var licenseNumber by remember { mutableStateOf("") }
    var licenseExpiry by remember { mutableStateOf<Long?>(null) }
    var yearsOfExperience by remember { mutableStateOf("") }

    // TODA & Tricycle Information
    var selectedTricycleId by remember { mutableStateOf("") }
    var todaNumber by remember { mutableStateOf("") }

    // Documents Checklist
    var hasValidLicense by remember { mutableStateOf(false) }
    var hasBarangayClearance by remember { mutableStateOf(false) }
    var hasPoliceClearance by remember { mutableStateOf(false) }
    var hasMedicalCertificate by remember { mutableStateOf(false) }
    var hasDriverTrainingCertificate by remember { mutableStateOf(false) }

    // Agreement
    var agreesToTerms by remember { mutableStateOf(false) }
    var understandsResponsibilities by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (currentStep > 1) currentStep-- else onBack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODA Driver Registration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Step $currentStep of $maxSteps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicator
        LinearProgressIndicator(
            progress = currentStep.toFloat() / maxSteps,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Step content
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (currentStep) {
                1 -> DriverBasicInfoStep(
                    name = name,
                    onNameChange = { name = it },
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    password = password,
                    onPasswordChange = { password = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it },
                    address = address,
                    onAddressChange = { address = it },
                    emergencyContact = emergencyContact,
                    onEmergencyContactChange = { emergencyContact = it }
                )
                2 -> DriverLicenseStep(
                    licenseNumber = licenseNumber,
                    onLicenseNumberChange = { licenseNumber = it },
                    licenseExpiry = licenseExpiry,
                    onLicenseExpiryChange = { licenseExpiry = it },
                    yearsOfExperience = yearsOfExperience,
                    onYearsOfExperienceChange = { yearsOfExperience = it }
                )
                3 -> TricycleTODAStep(
                    availableTricycles = availableTricycles,
                    selectedTricycleId = selectedTricycleId,
                    onTricycleSelected = { selectedTricycleId = it },
                    todaNumber = todaNumber,
                    onTodaNumberChange = { todaNumber = it }
                )
                4 -> DocumentsChecklistStep(
                    hasValidLicense = hasValidLicense,
                    onValidLicenseChange = { hasValidLicense = it },
                    hasBarangayClearance = hasBarangayClearance,
                    onBarangayClearanceChange = { hasBarangayClearance = it },
                    hasPoliceClearance = hasPoliceClearance,
                    onPoliceClearanceChange = { hasPoliceClearance = it },
                    hasMedicalCertificate = hasMedicalCertificate,
                    onMedicalCertificateChange = { hasMedicalCertificate = it },
                    hasDriverTrainingCertificate = hasDriverTrainingCertificate,
                    onDriverTrainingCertificateChange = { hasDriverTrainingCertificate = it }
                )
                5 -> DriverAgreementStep(
                    agreesToTerms = agreesToTerms,
                    onAgreesToTermsChange = { agreesToTerms = it },
                    understandsResponsibilities = understandsResponsibilities,
                    onUnderstandsResponsibilitiesChange = { understandsResponsibilities = it }
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 1) {
                OutlinedButton(
                    onClick = { currentStep-- }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    errorMessage = null

                    when (currentStep) {
                        1 -> {
                            when {
                                name.isBlank() -> errorMessage = "Name is required"
                                phoneNumber.length < 11 -> errorMessage = "Invalid phone number"
                                password.length < 6 -> errorMessage = "Password must be at least 6 characters"
                                password != confirmPassword -> errorMessage = "Passwords don't match"
                                address.isBlank() -> errorMessage = "Address is required"
                                emergencyContact.length < 11 -> errorMessage = "Invalid emergency contact"
                                else -> currentStep++
                            }
                        }
                        2 -> {
                            when {
                                licenseNumber.isBlank() -> errorMessage = "License number is required"
                                licenseExpiry == null -> errorMessage = "License expiry date is required"
                                yearsOfExperience.isBlank() -> errorMessage = "Years of experience is required"
                                else -> currentStep++
                            }
                        }
                        3 -> {
                            when {
                                selectedTricycleId.isBlank() -> errorMessage = "Please select a tricycle"
                                todaNumber.isBlank() -> errorMessage = "TODA number is required"
                                else -> currentStep++
                            }
                        }
                        4 -> {
                            when {
                                !hasValidLicense -> errorMessage = "Valid driver's license is required"
                                !hasBarangayClearance -> errorMessage = "Barangay clearance is required"
                                !hasPoliceClearance -> errorMessage = "Police clearance is required"
                                !hasMedicalCertificate -> errorMessage = "Medical certificate is required"
                                else -> currentStep++
                            }
                        }
                        5 -> {
                            when {
                                !agreesToTerms -> errorMessage = "You must agree to the terms and conditions"
                                !understandsResponsibilities -> errorMessage = "You must acknowledge understanding of driver responsibilities"
                                else -> {
                                    // Complete registration
                                    val registration = DriverRegistration(
                                        id = UUID.randomUUID().toString(),
                                        applicantName = name,
                                        phoneNumber = phoneNumber,
                                        address = address,
                                        emergencyContact = emergencyContact,
                                        licenseNumber = licenseNumber,
                                        licenseExpiry = licenseExpiry ?: 0,
                                        yearsOfExperience = yearsOfExperience.toIntOrNull() ?: 0,
                                        tricycleId = selectedTricycleId,
                                        todaNumber = todaNumber,
                                        hasValidLicense = hasValidLicense,
                                        hasBarangayClearance = hasBarangayClearance,
                                        hasPoliceClearance = hasPoliceClearance,
                                        hasMedicalCertificate = hasMedicalCertificate,
                                        hasDriverTrainingCertificate = hasDriverTrainingCertificate
                                    )
                                    onRegistrationComplete(registration, password)
                                }
                            }
                        }
                    }
                }
            ) {
                Text(if (currentStep == maxSteps) "Submit Application" else "Next")
                if (currentStep < maxSteps) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DriverBasicInfoStep(
    name: String,
    onNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    emergencyContact: String,
    onEmergencyContactChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Basic Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { if (it.length <= 11) onPhoneNumberChange(it) },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation()
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation()
        )

        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text("Complete Address") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
            minLines = 2
        )

        OutlinedTextField(
            value = emergencyContact,
            onValueChange = { if (it.length <= 11) onEmergencyContactChange(it) },
            label = { Text("Emergency Contact Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null) }
        )
    }
}

@Composable
private fun DriverLicenseStep(
    licenseNumber: String,
    onLicenseNumberChange: (String) -> Unit,
    licenseExpiry: Long?,
    onLicenseExpiryChange: (Long?) -> Unit,
    yearsOfExperience: String,
    onYearsOfExperienceChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Driver's License Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Please provide your valid driver's license information. You must have a professional driver's license to operate a tricycle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = licenseNumber,
            onValueChange = onLicenseNumberChange,
            label = { Text("Driver's License Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
        )

        OutlinedTextField(
            value = yearsOfExperience,
            onValueChange = { if (it.all { char -> char.isDigit() }) onYearsOfExperienceChange(it) },
            label = { Text("Years of Driving Experience") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.DriveEta, contentDescription = null) }
        )

        // Note about license requirements
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Information",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "License Requirements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Must have a valid Professional Driver's License\n" +
                            "• License must not be expired\n" +
                            "• Minimum 2 years driving experience required\n" +
                            "• No major traffic violations in the last 12 months",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TricycleTODAStep(
    availableTricycles: List<Tricycle>,
    selectedTricycleId: String,
    onTricycleSelected: (String) -> Unit,
    todaNumber: String,
    onTodaNumberChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "TODA & Tricycle Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Select the tricycle you want to register for and provide your TODA membership details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = todaNumber,
            onValueChange = onTodaNumberChange,
            label = { Text("TODA Number/Membership ID") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
        )

        Text(
            text = "Available Tricycles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (availableTricycles.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "No tricycles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No tricycles available for registration at this time. Please contact TODA administration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            availableTricycles.forEach { tricycle ->
                TricycleSelectionCard(
                    tricycle = tricycle,
                    isSelected = selectedTricycleId == tricycle.id,
                    onSelect = { onTricycleSelected(tricycle.id) }
                )
            }
        }

        // Information about multiple drivers
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Multiple drivers",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Multiple Driver Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Multiple drivers can register for the same tricycle. This allows family members or partners to share operation of a single tricycle unit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun TricycleSelectionCard(
    tricycle: Tricycle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Plate: ${tricycle.plateNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TODA Number: ${tricycle.todaNumber}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Owner: ${tricycle.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${tricycle.registeredDrivers.size} registered driver(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentsChecklistStep(
    hasValidLicense: Boolean,
    onValidLicenseChange: (Boolean) -> Unit,
    hasBarangayClearance: Boolean,
    onBarangayClearanceChange: (Boolean) -> Unit,
    hasPoliceClearance: Boolean,
    onPoliceClearanceChange: (Boolean) -> Unit,
    hasMedicalCertificate: Boolean,
    onMedicalCertificateChange: (Boolean) -> Unit,
    hasDriverTrainingCertificate: Boolean,
    onDriverTrainingCertificateChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Required Documents",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Please confirm that you have the following required documents. You will need to present these during the application review process.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DocumentCheckItem(
                    title = "Valid Professional Driver's License",
                    description = "Current and not expired",
                    checked = hasValidLicense,
                    onCheckedChange = onValidLicenseChange,
                    required = true
                )

                DocumentCheckItem(
                    title = "Barangay Clearance",
                    description = "From your current address",
                    checked = hasBarangayClearance,
                    onCheckedChange = onBarangayClearanceChange,
                    required = true
                )

                DocumentCheckItem(
                    title = "Police Clearance",
                    description = "NBI or Local Police Clearance",
                    checked = hasPoliceClearance,
                    onCheckedChange = onPoliceClearanceChange,
                    required = true
                )

                DocumentCheckItem(
                    title = "Medical Certificate",
                    description = "Physical and mental fitness to drive",
                    checked = hasMedicalCertificate,
                    onCheckedChange = onMedicalCertificateChange,
                    required = true
                )

                DocumentCheckItem(
                    title = "Driver Training Certificate",
                    description = "TODA-approved driver training course",
                    checked = hasDriverTrainingCertificate,
                    onCheckedChange = onDriverTrainingCertificateChange,
                    required = false
                )
            }
        }
    }
}

@Composable
private fun DocumentCheckItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    required: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (required) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DriverAgreementStep(
    agreesToTerms: Boolean,
    onAgreesToTermsChange: (Boolean) -> Unit,
    understandsResponsibilities: Boolean,
    onUnderstandsResponsibilitiesChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Driver Agreement & Responsibilities",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Driver Responsibilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "As a TODA driver, you agree to:\n\n" +
                            "• Operate tricycles safely and responsibly\n" +
                            "• Follow all traffic rules and regulations\n" +
                            "• Maintain professional conduct with passengers\n" +
                            "• Keep the tricycle clean and well-maintained\n" +
                            "• Accept booking requests fairly and promptly\n" +
                            "• Charge only the agreed-upon fares\n" +
                            "• Attend mandatory TODA meetings and training\n" +
                            "• Report any incidents or safety concerns\n" +
                            "• Maintain valid licenses and documentation",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = understandsResponsibilities,
                        onCheckedChange = onUnderstandsResponsibilitiesChange
                    )
                    Text(
                        text = "I understand and accept these responsibilities",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Terms and Conditions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "By submitting this application, you agree to:\n\n" +
                            "• TODA Barangay 177 membership requirements\n" +
                            "• Background verification process\n" +
                            "• Regular vehicle and document inspections\n" +
                            "• Compliance with local transportation regulations\n" +
                            "• Disciplinary measures for violations\n" +
                            "• Revenue sharing and membership dues payment",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreesToTerms,
                        onCheckedChange = onAgreesToTermsChange
                    )
                    Text(
                        text = "I agree to all terms and conditions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
