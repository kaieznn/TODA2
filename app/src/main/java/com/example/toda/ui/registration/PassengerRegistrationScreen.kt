package com.example.toda.ui.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.*
import com.example.toda.viewmodel.CustomerRegistrationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerRegistrationScreen(
    onRegistrationComplete: (String) -> Unit, // Pass userId when successful
    onBack: () -> Unit,
    viewModel: CustomerRegistrationViewModel = hiltViewModel()
) {
    var currentStep by remember { mutableStateOf(1) }
    val maxSteps = 4

    // Collect ViewModel states
    val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
    val validationErrors by viewModel.validationErrors.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Basic Information
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Personal Details
    var address by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf<Long?>(null) }
    var gender by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }

    // Emergency Contact
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }

    // Preferences & Terms
    var smsNotifications by remember { mutableStateOf(true) }
    var bookingUpdates by remember { mutableStateOf(true) }
    var promotionalMessages by remember { mutableStateOf(false) }
    var emergencyAlerts by remember { mutableStateOf(true) }
    var agreesToTerms by remember { mutableStateOf(false) }

    // Handle successful registration
    LaunchedEffect(registrationState.isSuccess) {
        if (registrationState.isSuccess && registrationState.userId != null) {
            onRegistrationComplete(registrationState.userId!!)
        }
    }

    // Auto-clear errors when user starts typing
    LaunchedEffect(name, phoneNumber, password, confirmPassword) {
        if (currentStep == 1) viewModel.clearValidationErrors()
    }

    LaunchedEffect(address, dateOfBirth, gender, occupation) {
        if (currentStep == 2) viewModel.clearValidationErrors()
    }

    LaunchedEffect(emergencyContactName, emergencyContact) {
        if (currentStep == 3) viewModel.clearValidationErrors()
    }

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
                    text = "Customer Registration",
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
            progress = { currentStep.toFloat() / maxSteps },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error/Success messages
        if (registrationState.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                        text = registrationState.error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (registrationState.message != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = registrationState.message!!,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Step content
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (currentStep) {
                1 -> BasicInformationStep(
                    name = name,
                    onNameChange = { name = it },
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    password = password,
                    onPasswordChange = { password = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it },
                    validationErrors = validationErrors.toMap(),
                    isCheckingPhone = registrationState.isCheckingPhone
                )
                2 -> PersonalDetailsStep(
                    address = address,
                    onAddressChange = { address = it },
                    dateOfBirth = dateOfBirth,
                    onDateOfBirthChange = { dateOfBirth = it },
                    gender = gender,
                    onGenderChange = { gender = it },
                    occupation = occupation,
                    onOccupationChange = { occupation = it },
                    validationErrors = validationErrors.toMap()
                )
                3 -> EmergencyContactStep(
                    emergencyContactName = emergencyContactName,
                    onEmergencyContactNameChange = { emergencyContactName = it },
                    emergencyContact = emergencyContact,
                    onEmergencyContactChange = { emergencyContact = it },
                    validationErrors = validationErrors.toMap()
                )
                4 -> PreferencesAndTermsStep(
                    smsNotifications = smsNotifications,
                    onSmsNotificationsChange = { smsNotifications = it },
                    bookingUpdates = bookingUpdates,
                    onBookingUpdatesChange = { bookingUpdates = it },
                    promotionalMessages = promotionalMessages,
                    onPromotionalMessagesChange = { promotionalMessages = it },
                    emergencyAlerts = emergencyAlerts,
                    onEmergencyAlertsChange = { emergencyAlerts = it },
                    agreesToTerms = agreesToTerms,
                    onAgreesToTermsChange = { agreesToTerms = it }
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
                    onClick = { currentStep-- },
                    enabled = !registrationState.isRegistering
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
                    coroutineScope.launch {
                        when (currentStep) {
                            1 -> {
                                val isValid = viewModel.validateStep1(name, phoneNumber, password, confirmPassword)
                                if (isValid) {
                                    val phoneAvailable = viewModel.checkPhoneAvailability(phoneNumber)
                                    if (phoneAvailable) {
                                        currentStep++
                                    }
                                }
                            }
                            2 -> {
                                val isValid = viewModel.validateStep2(address, dateOfBirth, gender, occupation)
                                if (isValid) {
                                    currentStep++
                                }
                            }
                            3 -> {
                                val isValid = viewModel.validateStep3(emergencyContactName, emergencyContact)
                                if (isValid) {
                                    currentStep++
                                }
                            }
                            4 -> {
                                viewModel.registerCustomer(
                                    name = name,
                                    phoneNumber = phoneNumber,
                                    password = password,
                                    address = address,
                                    dateOfBirth = dateOfBirth,
                                    gender = gender,
                                    occupation = occupation,
                                    emergencyContactName = emergencyContactName,
                                    emergencyContact = emergencyContact,
                                    notificationPreferences = NotificationPreferences(
                                        smsNotifications = smsNotifications,
                                        bookingUpdates = bookingUpdates,
                                        promotionalMessages = promotionalMessages,
                                        emergencyAlerts = emergencyAlerts
                                    ),
                                    agreesToTerms = agreesToTerms
                                )
                            }
                        }
                    }
                },
                enabled = !registrationState.isRegistering && !registrationState.isCheckingPhone
            ) {
                if (registrationState.isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    when {
                        registrationState.isRegistering -> "Creating Account..."
                        registrationState.isCheckingPhone -> "Checking..."
                        currentStep == maxSteps -> "Complete Registration"
                        else -> "Next"
                    }
                )

                if (currentStep < maxSteps && !registrationState.isRegistering) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun BasicInformationStep(
    name: String,
    onNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    validationErrors: Map<String, String>,
    isCheckingPhone: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Basic Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Let's start with your basic information",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            isError = validationErrors["name"] != null
        )

        validationErrors["name"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { if (it.length <= 11) onPhoneNumberChange(it) },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            placeholder = { Text("09XXXXXXXXX") },
            isError = validationErrors["phoneNumber"] != null
        )

        validationErrors["phoneNumber"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            isError = validationErrors["password"] != null
        )

        validationErrors["password"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            isError = validationErrors["confirmPassword"] != null
        )

        validationErrors["confirmPassword"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Phone availability check
        if (isCheckingPhone) {
            Text(
                text = "Checking phone availability...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDetailsStep(
    address: String,
    onAddressChange: (String) -> Unit,
    dateOfBirth: Long?,
    onDateOfBirthChange: (Long?) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    occupation: String,
    onOccupationChange: (String) -> Unit,
    validationErrors: Map<String, String>
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Personal Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
            minLines = 2,
            isError = validationErrors["address"] != null
        )

        validationErrors["address"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Date of Birth Field
        OutlinedTextField(
            value = dateOfBirth?.let { dateFormatter.format(Date(it)) } ?: "",
            onValueChange = { },
            label = { Text("Date of Birth") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select date")
                }
            },
            isError = validationErrors["dateOfBirth"] != null
        )

        validationErrors["dateOfBirth"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Date Picker Dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dateOfBirth ?: System.currentTimeMillis()
            )

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { selectedDate ->
                                onDateOfBirthChange(selectedDate)
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Gender selection
        Text(
            text = "Gender",
            style = MaterialTheme.typography.titleMedium
        )

        validationErrors["gender"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Male", "Female", "Other").forEach { option ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = gender == option,
                            onClick = { onGenderChange(option) }
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = gender == option,
                        onClick = { onGenderChange(option) }
                    )
                    Text(
                        text = option,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = occupation,
            onValueChange = onOccupationChange,
            label = { Text("Occupation (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) }
        )
    }
}

@Composable
private fun EmergencyContactStep(
    emergencyContactName: String,
    onEmergencyContactNameChange: (String) -> Unit,
    emergencyContact: String,
    onEmergencyContactChange: (String) -> Unit,
    validationErrors: Map<String, String>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Emergency Contact",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Please provide an emergency contact person who can be reached in case of emergency during your trips.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = emergencyContactName,
            onValueChange = onEmergencyContactNameChange,
            label = { Text("Emergency Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            isError = validationErrors["emergencyContactName"] != null
        )

        validationErrors["emergencyContactName"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = emergencyContact,
            onValueChange = { if (it.length <= 11) onEmergencyContactChange(it) },
            label = { Text("Emergency Contact Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            placeholder = { Text("09XXXXXXXXX") },
            isError = validationErrors["emergencyContact"] != null
        )

        validationErrors["emergencyContact"]?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PreferencesAndTermsStep(
    smsNotifications: Boolean,
    onSmsNotificationsChange: (Boolean) -> Unit,
    bookingUpdates: Boolean,
    onBookingUpdatesChange: (Boolean) -> Unit,
    promotionalMessages: Boolean,
    onPromotionalMessagesChange: (Boolean) -> Unit,
    emergencyAlerts: Boolean,
    onEmergencyAlertsChange: (Boolean) -> Unit,
    agreesToTerms: Boolean,
    onAgreesToTermsChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preferences & Terms",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Notification Preferences
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Notification Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                NotificationPreferenceItem(
                    title = "SMS Notifications",
                    description = "Receive SMS updates about your bookings",
                    checked = smsNotifications,
                    onCheckedChange = onSmsNotificationsChange
                )

                NotificationPreferenceItem(
                    title = "Booking Updates",
                    description = "Get notified about booking status changes",
                    checked = bookingUpdates,
                    onCheckedChange = onBookingUpdatesChange
                )

                NotificationPreferenceItem(
                    title = "Emergency Alerts",
                    description = "Receive emergency and safety alerts",
                    checked = emergencyAlerts,
                    onCheckedChange = onEmergencyAlertsChange
                )

                NotificationPreferenceItem(
                    title = "Promotional Messages",
                    description = "Receive updates about promotions and offers",
                    checked = promotionalMessages,
                    onCheckedChange = onPromotionalMessagesChange
                )
            }
        }

        // Terms and Conditions
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
                    text = "By registering, you agree to:\n\n" +
                            "• Use the service responsibly and respectfully\n" +
                            "• Provide accurate information\n" +
                            "• Follow TODA Barangay 177 guidelines\n" +
                            "• Pay agreed fares for completed trips\n" +
                            "• Respect drivers and their vehicles",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreesToTerms,
                        onCheckedChange = onAgreesToTermsChange
                    )
                    Text(
                        text = "I agree to the terms and conditions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPreferenceItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
