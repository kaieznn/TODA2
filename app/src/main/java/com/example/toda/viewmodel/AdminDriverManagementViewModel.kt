package com.example.toda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toda.data.DriverInfo
import com.example.toda.repository.TODARepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDriverManagementUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AdminDriverManagementViewModel @Inject constructor(
    private val repository: TODARepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDriverManagementUiState())
    val uiState: StateFlow<AdminDriverManagementUiState> = _uiState.asStateFlow()

    private val _allDrivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val allDrivers: StateFlow<List<DriverInfo>> = _allDrivers.asStateFlow()

    fun loadAllDrivers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                println("=== LOADING ALL DRIVERS ===")
                val result = repository.getAllDrivers()

                result.fold(
                    onSuccess = { drivers ->
                        println("Received ${drivers.size} drivers")
                        drivers.forEach { driver ->
                            println("Driver: ${driver.driverId} - ${driver.driverName} - RFID: ${driver.rfidUID} - Needs RFID: ${driver.needsRfidAssignment}")
                        }
                        _allDrivers.value = drivers
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    },
                    onFailure = { exception ->
                        println("Error loading drivers: ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load drivers: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                println("Error loading drivers: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load drivers: ${e.message}"
                )
            }
        }
    }

    fun assignRfidToDriver(driverId: String, rfidUID: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val result = repository.assignRfidToDriver(driverId, rfidUID)

                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "RFID $rfidUID assigned successfully to driver!"
                        )
                        loadAllDrivers() // Refresh the list
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to assign RFID: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "An unexpected error occurred: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
