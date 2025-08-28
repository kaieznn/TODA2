package com.example.toda.utils

import com.example.toda.data.*

fun validateBooking(
    userProfile: UserProfile?,
    securityConfig: SecurityConfig,
    currentTime: Long
): BookingValidationResult {

    if (userProfile == null) {
        return BookingValidationResult.PHONE_NOT_VERIFIED
    }

    if (userProfile.isBlocked) {
        return BookingValidationResult.USER_BLOCKED
    }

    if (userProfile.trustScore < securityConfig.minTrustScore) {
        return BookingValidationResult.LOW_TRUST_SCORE
    }

    val timeSinceLastBooking = currentTime - userProfile.lastBookingTime
    if (timeSinceLastBooking < securityConfig.minTimeBetweenBookings) {
        return BookingValidationResult.TOO_SOON_SINCE_LAST_BOOKING
    }

    val cancellationRate = if (userProfile.totalBookings > 0) {
        userProfile.cancelledBookings.toDouble() / userProfile.totalBookings
    } else 0.0

    if (cancellationRate > securityConfig.maxCancellationRate) {
        return BookingValidationResult.HIGH_CANCELLATION_RATE
    }

    return BookingValidationResult.VALID
}