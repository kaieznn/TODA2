package com.example.toda.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.toda.data.Booking
import com.example.toda.data.BookingStatus
import com.example.toda.service.DriverTrackingService
import android.content.Context
import com.example.toda.service.CustomerLocationService
import com.example.toda.data.CustomerLocation

class BookingViewModel {
    var bookings by mutableStateOf<List<Booking>>(emptyList())
        private set

    // Separate lists for better organization
    var bookingHistory by mutableStateOf<List<Booking>>(emptyList())
        private set

    val driverTrackingService = DriverTrackingService()

    fun addBooking(booking: Booking) {
        bookings = bookings + booking
    }

    fun updateBookings(updatedBookings: List<Booking>) {
        // Move completed/cancelled bookings to history
        val (active, completed) = updatedBookings.partition {
            it.status == BookingStatus.PENDING || it.status == BookingStatus.ACCEPTED
        }

        bookings = active

        // Add newly completed bookings to history
        val newHistoryItems = completed.filter { completedBooking ->
            !bookingHistory.any { it.id == completedBooking.id }
        }
        bookingHistory = (bookingHistory + newHistoryItems).sortedByDescending { it.timestamp }
    }

    fun completeBooking(bookingId: String) {
        val booking = bookings.find { it.id == bookingId }
        if (booking != null) {
            val completedBooking = booking.copy(status = BookingStatus.COMPLETED)
            bookings = bookings.filter { it.id != bookingId }
            bookingHistory = (listOf(completedBooking) + bookingHistory).sortedByDescending { it.timestamp }
        }
    }

    fun cancelBooking(bookingId: String) {
        val booking = bookings.find { it.id == bookingId }
        if (booking != null) {
            val cancelledBooking = booking.copy(status = BookingStatus.CANCELLED)
            bookings = bookings.filter { it.id != bookingId }
            bookingHistory = (listOf(cancelledBooking) + bookingHistory).sortedByDescending { it.timestamp }
        }
    }

    fun getBookingById(id: String): Booking? {
        return bookings.find { it.id == id } ?: bookingHistory.find { it.id == id }
    }

    fun getBookingsForCustomer(customerId: String): List<Booking> {
        return bookings.filter { it.customerId == customerId }
    }

    fun getBookingHistoryForCustomer(customerId: String): List<Booking> {
        return bookingHistory.filter { it.customerId == customerId }
    }

    fun getAllBookingHistory(): List<Booking> {
        return bookingHistory
    }

    private var _customerLocationService: CustomerLocationService? = null

    fun initializeCustomerLocationService(context: Context) {
        _customerLocationService = CustomerLocationService(context)
    }

    fun getCustomerLocationService(): CustomerLocationService? {
        return _customerLocationService
    }

    fun startCustomerLocationTracking(customerId: String) {
        _customerLocationService?.startLocationTracking(customerId)
    }

    fun stopCustomerLocationTracking() {
        _customerLocationService?.stopLocationTracking()
    }
}