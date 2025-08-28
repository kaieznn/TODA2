package com.example.toda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toda.data.DriverRegistration
import com.example.toda.repository.TODARepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverRegistrationUiState(
    val isLoading: Boolean = false,
    val isRegistrationSuccessful: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DriverRegistrationViewModel @Inject constructor(
    private val repository: TODARepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverRegistrationUiState())
    val uiState: StateFlow<DriverRegistrationUiState> = _uiState.asStateFlow()

    fun submitRegistration(registration: DriverRegistration) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                val result = repository.submitDriverApplication(registration)

                result.fold(
                    onSuccess = { applicationId ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRegistrationSuccessful = true,
                            errorMessage = null
                        )
                        println("Driver application submitted successfully with ID: $applicationId")
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRegistrationSuccessful = false,
                            errorMessage = exception.message ?: "Registration failed. Please try again."
                        )
                        println("Driver application failed: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRegistrationSuccessful = false,
                    errorMessage = "An unexpected error occurred. Please try again."
                )
                println("Unexpected error during driver registration: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetState() {
        _uiState.value = DriverRegistrationUiState()
    }
}
