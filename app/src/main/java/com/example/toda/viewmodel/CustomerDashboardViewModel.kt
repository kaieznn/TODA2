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
class CustomerDashboardViewModel @Inject constructor(
    private val repository: TODARepository
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(CustomerDashboardState())
    val dashboardState = _dashboardState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _recentBookings = MutableStateFlow<List<Booking>>(emptyList())
    val recentBookings = _recentBookings.asStateFlow()

    fun loadUserData(userId: String) {
        viewModelScope.launch {
            try {
                _dashboardState.value = _dashboardState.value.copy(isLoading = true)

                // Load user profile
                repository.getUserProfile(userId).collect { profile ->
                    _userProfile.value = profile
                }

                // Load recent bookings (this would need to be implemented in repository)
                // For now, we'll use empty list
                _recentBookings.value = emptyList()

                _dashboardState.value = _dashboardState.value.copy(
                    isLoading = false,
                    userId = userId
                )
            } catch (e: Exception) {
                _dashboardState.value = _dashboardState.value.copy(
                    isLoading = false,
                    error = "Failed to load user data: ${e.message}"
                )
            }
        }
    }

    fun requestEmergencyAssistance() {
        viewModelScope.launch {
            try {
                val profile = _userProfile.value
                if (profile != null) {
                    repository.createEmergencyAlert(
                        userId = _dashboardState.value.userId ?: "",
                        userName = profile.name,
                        bookingId = null,
                        latitude = 14.74800540601891, // Default to Barangay 177 center
                        longitude = 121.0499004,
                        message = "Emergency assistance requested from customer app"
                    ).fold(
                        onSuccess = { alertId ->
                            _dashboardState.value = _dashboardState.value.copy(
                                message = "Emergency alert sent! Help is on the way. Alert ID: $alertId"
                            )
                        },
                        onFailure = { error ->
                            _dashboardState.value = _dashboardState.value.copy(
                                error = "Failed to send emergency alert: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _dashboardState.value = _dashboardState.value.copy(
                    error = "Emergency request failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _dashboardState.value = _dashboardState.value.copy(error = null)
    }

    fun clearMessage() {
        _dashboardState.value = _dashboardState.value.copy(message = null)
    }
}

data class CustomerDashboardState(
    val isLoading: Boolean = false,
    val userId: String? = null,
    val error: String? = null,
    val message: String? = null
)
