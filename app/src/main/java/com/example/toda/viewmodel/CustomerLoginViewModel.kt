package com.example.toda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toda.data.*
import com.example.toda.repository.TODARepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerLoginViewModel @Inject constructor(
    private val repository: TODARepository
) : ViewModel() {

    private val _loginState = MutableStateFlow(CustomerLoginState())
    val loginState = _loginState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    fun login(phoneNumber: String, password: String) {
        if (phoneNumber.isBlank() || password.isBlank()) {
            _loginState.value = _loginState.value.copy(
                error = "Please enter both phone number and password"
            )
            return
        }

        // Validate phone number format
        if (!isValidPhoneNumber(phoneNumber)) {
            _loginState.value = _loginState.value.copy(
                error = "Please enter a valid Philippine phone number (e.g., 09XXXXXXXXX)"
            )
            return
        }

        viewModelScope.launch {
            try {
                _loginState.value = _loginState.value.copy(
                    isLoading = true,
                    error = null
                )

                println("=== CUSTOMER LOGIN ATTEMPT ===")
                println("Phone: $phoneNumber")

                repository.loginUser(phoneNumber, password).fold(
                    onSuccess = { user ->
                        println("Login successful for user: ${user.name} (${user.userType})")

                        if (user.userType == "PASSENGER") {
                            _currentUser.value = user
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                isSuccess = true,
                                userId = user.id
                            )
                            println("Customer login successful")
                        } else {
                            _loginState.value = _loginState.value.copy(
                                isLoading = false,
                                error = "This account is registered as ${user.userType}. Please use the appropriate app."
                            )
                            println("Wrong user type: ${user.userType}")
                        }
                    },
                    onFailure = { error ->
                        println("Login failed: ${error.message}")

                        // Provide more specific error messages
                        val errorMessage = when {
                            error.message?.contains("authentication failed", ignoreCase = true) == true ->
                                "Invalid phone number or password. Please check your credentials."
                            error.message?.contains("phone number already registered", ignoreCase = true) == true ->
                                "Phone number not found. Please register first or check your number."
                            error.message?.contains("network", ignoreCase = true) == true ->
                                "Network error. Please check your internet connection and try again."
                            else -> "Login failed: ${error.message}"
                        }

                        _loginState.value = _loginState.value.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
                )
            } catch (e: Exception) {
                println("Login exception: ${e.message}")
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message ?: "Unknown error occurred"}"
                )
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Philippine phone number validation
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        return when {
            cleanNumber.startsWith("+639") && cleanNumber.length == 13 -> true
            cleanNumber.startsWith("09") && cleanNumber.length == 11 -> true
            cleanNumber.startsWith("639") && cleanNumber.length == 12 -> true
            else -> false
        }
    }

    fun clearError() {
        _loginState.value = _loginState.value.copy(error = null)
    }

    fun logout() {
        _currentUser.value = null
        _loginState.value = CustomerLoginState()
    }
}

data class CustomerLoginState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val userId: String? = null,
    val error: String? = null
)
