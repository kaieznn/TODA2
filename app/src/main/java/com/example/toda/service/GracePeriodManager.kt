package com.example.toda.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

data class GracePeriodInfo(
    val startTime: Long = 0L,
    val gracePeriodMinutes: Int = 5,
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L
)

class GracePeriodManager {
    private val _gracePeriodState = MutableStateFlow<GracePeriodInfo?>(null)
    val gracePeriodState: Flow<GracePeriodInfo?> = _gracePeriodState.asStateFlow()

    private var isRunning = false

    suspend fun startGracePeriod(gracePeriodMinutes: Int = 5) {
        if (isRunning) return

        isRunning = true
        val startTime = System.currentTimeMillis()
        val totalSeconds = gracePeriodMinutes * 60L

        for (seconds in totalSeconds downTo 0) {
            _gracePeriodState.value = GracePeriodInfo(
                startTime = startTime,
                gracePeriodMinutes = gracePeriodMinutes,
                isActive = true,
                remainingSeconds = seconds
            )
            delay(1000)
        }

        // Grace period expired
        _gracePeriodState.value = GracePeriodInfo(
            startTime = startTime,
            gracePeriodMinutes = gracePeriodMinutes,
            isActive = false,
            remainingSeconds = 0L
        )
        isRunning = false
    }

    fun stopGracePeriod() {
        isRunning = false
        _gracePeriodState.value = null
    }

    fun getCurrentState(): GracePeriodInfo? {
        return _gracePeriodState.value
    }

    fun isGracePeriodExpired(): Boolean {
        val current = _gracePeriodState.value
        return current != null && !current.isActive && current.remainingSeconds == 0L
    }
}