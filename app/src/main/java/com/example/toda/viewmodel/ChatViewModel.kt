package com.example.toda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toda.data.*
import com.example.toda.service.ChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    // Made public
    val chatService: ChatService
) : ViewModel() {

    private val _currentBookingId = MutableStateFlow<String?>(null)
    val currentBookingId = _currentBookingId.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _newMessageNotification = MutableStateFlow<ChatMessage?>(null)
    val newMessageNotification = _newMessageNotification.asStateFlow()

    // Get chat state for current booking
    val chatState: StateFlow<ChatState> = currentBookingId
        .filterNotNull()
        .flatMapLatest { bookingId ->
            chatService.getChatStateFlow(bookingId) ?: flowOf(ChatState())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatState()
        )

    // Get active chats
    val activeChats = chatService.activeChatsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun initializeChat(booking: Booking, customer: User, driver: User?, operator: User?) {
        viewModelScope.launch {
            val chatId = chatService.initializeChatForBooking(booking, customer, driver, operator)
            _currentBookingId.value = chatId
        }
    }

    fun setCurrentUser(user: User) {
        _currentUser.value = user

        // Update online status for all active chats
        activeChats.value.forEach { chat ->
            if (chatService.isParticipant(chat.bookingId, user.id)) {
                chatService.updateParticipantStatus(chat.bookingId, user.id, true)
            }
        }
    }

    fun setCurrentBooking(bookingId: String) {
        _currentBookingId.value = bookingId
    }

    fun sendMessage(message: String) {
        val user = _currentUser.value ?: return
        val bookingId = _currentBookingId.value ?: return

        viewModelScope.launch {
            chatService.sendMessage(
                ChatMessage(
                    bookingId = bookingId,
                    senderId = user.id,
                    senderName = user.name,
                    receiverId = "all",
                    message = message
                )
            )
        }
    }

    fun sendQuickMessage(message: String) {
        sendMessage(message)
    }

    fun sendLocationUpdate(location: String) {
        val user = _currentUser.value ?: return
        val bookingId = _currentBookingId.value ?: return

        viewModelScope.launch {
            // Parse location string to get lat/lng if needed
            val parts = location.split(",")
            if (parts.size >= 2) {
                val latitude = parts[0].trim().toDoubleOrNull() ?: 0.0
                val longitude = parts[1].trim().toDoubleOrNull() ?: 0.0
                chatService.sendLocationUpdate(bookingId, user.id, user.name, latitude, longitude)
            }
        }
    }

    fun sendDriverArrivalNotification() {
        val user = _currentUser.value ?: return
        val bookingId = _currentBookingId.value ?: return

        if (user.userType == UserType.DRIVER) {
            viewModelScope.launch {
                chatService.sendMessage(
                    ChatMessage(
                        bookingId = bookingId,
                        senderId = user.id,
                        senderName = user.name,
                        receiverId = "all",
                        message = "I have arrived at the pickup location",
                        messageType = MessageType.SYSTEM.name
                    )
                )
            }
        }
    }

    fun markMessagesAsRead() {
        val user = _currentUser.value ?: return
        val bookingId = _currentBookingId.value ?: return

        viewModelScope.launch {
            chatService.markMessagesAsRead(bookingId, user.id)
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        val user = _currentUser.value ?: return
        val bookingId = _currentBookingId.value ?: return

        viewModelScope.launch {
            chatService.setTypingStatus(bookingId, user.id, isTyping)
        }
    }

    fun endChat() {
        val bookingId = _currentBookingId.value ?: return

        viewModelScope.launch {
            chatService.endChat(bookingId)
            _currentBookingId.value = null
        }
    }

    fun getUnreadCount(bookingId: String): Int {
        return chatService.getUnreadCount(bookingId)
    }

    fun showNewMessageNotification(message: ChatMessage) {
        _newMessageNotification.value = message
    }

    fun dismissNewMessageNotification() {
        _newMessageNotification.value = null
    }

    fun updateParticipantOnlineStatus(isOnline: Boolean) {
        val user = _currentUser.value ?: return

        viewModelScope.launch {
            activeChats.value.forEach { chat ->
                if (chatService.isParticipant(chat.bookingId, user.id)) {
                    chatService.updateParticipantStatus(chat.bookingId, user.id, isOnline)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Mark user as offline when ViewModel is cleared
        updateParticipantOnlineStatus(false)
    }
}
