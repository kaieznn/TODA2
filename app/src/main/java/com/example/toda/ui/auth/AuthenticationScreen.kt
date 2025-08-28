package com.example.toda.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toda.data.*
import com.example.toda.service.RegistrationService
import com.example.toda.service.RegistrationResult
import com.example.toda.ui.registration.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    onAuthSuccess: (User) -> Unit,
    onBack: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("main") } // "main", "registration_type", "passenger_reg", "driver_reg", "login"
    var selectedUserType by remember { mutableStateOf<UserType?>(null) }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf<String?>(null) }
    var showVerification by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingUser by remember { mutableStateOf<User?>(null) }

    val registrationService = remember { RegistrationService() }
    val coroutineScope = rememberCoroutineScope()

    // Mock user database (in production, use proper database)
    var users by remember { mutableStateOf(listOf<User>()) }

    fun authenticateUser(phone: String, pass: String): User? {
        return users.find { it.phoneNumber == phone && it.password == pass }
    }

    fun registerUser(user: User): Boolean {
        return if (users.none { it.phoneNumber == user.phoneNumber }) {
            users = users + user
            true
        } else false
    }

    when (currentScreen) {
        "main" -> {
            MainAuthScreen(
                onLoginSelected = { currentScreen = "login" },
                onRegisterSelected = { currentScreen = "registration_type" },
                onBack = onBack
            )
        }
        "registration_type" -> {
            RegistrationTypeScreen(
                onUserTypeSelected = { userType ->
                    selectedUserType = userType
                    currentScreen = when (userType) {
                        UserType.PASSENGER -> "passenger_reg"
                        UserType.DRIVER -> "driver_reg"
                        UserType.OPERATOR -> "operator_reg"
                        else -> "main"
                    }
                },
                onBack = { currentScreen = "main" }
            )
        }
        "passenger_reg" -> {
            PassengerRegistrationScreen(
                onRegistrationComplete = { userId ->
                    // Registration successful - navigate to customer dashboard or login
                    currentScreen = "login"
                    // You could also navigate directly to the customer app here
                },
                onBack = { currentScreen = "registration_type" }
            )
        }
        "driver_reg" -> {
            DriverRegistrationScreen(
                availableTricycles = registrationService.getAvailableTricycles(),
                onRegistrationComplete = { registration, pass ->
                    coroutineScope.launch {
                        when (val result = registrationService.registerDriver(registration, pass)) {
                            is RegistrationResult.Pending -> {
                                users = users + result.user
                                errorMessage = null
                                // Show success message and go back to main
                                currentScreen = "registration_success"
                            }
                            is RegistrationResult.Error -> {
                                errorMessage = result.message
                            }
                            else -> {}
                        }
                    }
                },
                onBack = { currentScreen = "registration_type" }
            )
        }
        "login" -> {
            LoginScreen(
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                password = password,
                onPasswordChange = { password = it },
                errorMessage = errorMessage,
                onLogin = {
                    errorMessage = null
                    val user = authenticateUser(phoneNumber, password)
                    if (user != null) {
                        if (user.isVerified) {
                            onAuthSuccess(user)
                        } else {
                            // Need to verify phone
                            val code = (1000..9999).random().toString()
                            sentCode = code
                            pendingUser = user
                            showVerification = true
                        }
                    } else {
                        errorMessage = "Invalid phone number or password"
                    }
                },
                onBack = { currentScreen = "main" },
                onForgotPassword = { /* TODO: Implement forgot password */ }
            )
        }
        "registration_success" -> {
            RegistrationSuccessScreen(
                onContinue = { currentScreen = "main" }
            )
        }
    }

    // Verification overlay
    if (showVerification) {
        VerificationScreen(
            phoneNumber = phoneNumber,
            sentCode = sentCode ?: "",
            verificationCode = verificationCode,
            onVerificationCodeChange = { verificationCode = it },
            errorMessage = errorMessage,
            onVerify = {
                coroutineScope.launch {
                    if (registrationService.verifyPhoneNumber(phoneNumber, verificationCode)) {
                        pendingUser?.let { user ->
                            val verifiedUser = user.copy(isVerified = true)
                            users = users.map { if (it.id == user.id) verifiedUser else it }
                            onAuthSuccess(verifiedUser)
                        }
                    } else {
                        errorMessage = "Invalid verification code"
                    }
                }
            },
            onBack = {
                showVerification = false
                verificationCode = ""
                errorMessage = null
                pendingUser = null
            }
        )
    }
}

@Composable
private fun MainAuthScreen(
    onLoginSelected: () -> Unit,
    onRegisterSelected: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
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
                text = "TODA Barangay 177",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Choose how you want to access the TODA system",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Login to Existing Account")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRegisterSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Create New Account")
        }
    }
}

@Composable
private fun LoginScreen(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit
) {
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
                text = "Login",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

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

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("09XXXXXXXXX") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = phoneNumber.isNotEmpty() && password.isNotEmpty()
        ) {
            Text("Login")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forgot Password?")
        }
    }
}

@Composable
private fun VerificationScreen(
    phoneNumber: String,
    sentCode: String,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    errorMessage: String?,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
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
                text = "Verify Phone Number",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "We've sent a verification code to:",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = phoneNumber,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "For demo purposes, the code is: $sentCode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
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

        OutlinedTextField(
            value = verificationCode,
            onValueChange = { if (it.length <= 4) onVerificationCodeChange(it) },
            label = { Text("Verification Code") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter 4-digit code") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = verificationCode.length == 4
        ) {
            Text("Verify")
        }
    }
}

@Composable
private fun RegistrationSuccessScreen(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✅",
                    style = MaterialTheme.typography.displayLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Application Submitted!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your driver application has been submitted successfully. You will be notified within 3-5 business days about the approval status.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }
            }
        }
    }
}