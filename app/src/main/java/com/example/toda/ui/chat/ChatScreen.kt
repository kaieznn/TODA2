package com.example.toda.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toda.data.*
import com.example.toda.service.ChatService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    bookingId: String,
    currentUser: User,
    chatService: ChatService,
    onBack: () -> Unit,
    onLocationShare: () -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    val chatState by chatService.getChatStateFlow(bookingId)?.collectAsState()
        ?: remember { mutableStateOf(ChatState()) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    // Mark messages as read when screen is opened
    LaunchedEffect(bookingId) {
        chatService.markMessagesAsRead(bookingId, currentUser.id)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Chat Header
        ChatHeader(
            bookingId = bookingId,
            chatService = chatService,
            onBack = onBack
        )

        // Messages List
        Box(
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatState.messages) { message ->
                    MessageBubble(
                        message = message,
                        isOwn = message.senderId == currentUser.id,
                        currentUserType = currentUser.userType
                    )
                }
            }

            // Typing indicator
            if (chatState.isTyping && chatState.typingUser != currentUser.name) {
                TypingIndicator(
                    userName = chatState.typingUser ?: "",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }

        // Message Input
        MessageInput(
            messageText = messageText,
            onMessageTextChange = {
                messageText = it
                // Handle typing status
                val newIsTyping = it.isNotEmpty()
                if (newIsTyping != isTyping) {
                    isTyping = newIsTyping
                    chatService.setTypingStatus(bookingId, currentUser.id, newIsTyping)
                }
            },
            onSendMessage = {
                if (messageText.isNotBlank()) {
                    chatService.sendMessage(
                        ChatMessage(
                            bookingId = bookingId,
                            senderId = currentUser.id,
                            senderName = currentUser.name,
                            receiverId = "all",
                            message = messageText.trim()
                        )
                    )
                    messageText = ""
                    isTyping = false
                    chatService.setTypingStatus(bookingId, currentUser.id, false)
                }
            },
            onLocationShare = {
                onLocationShare()
                chatService.sendLocationUpdate(
                    bookingId = bookingId,
                    senderId = currentUser.id,
                    senderName = currentUser.name,
                    latitude = 0.0, // This should be actual location coordinates
                    longitude = 0.0
                )
            },
            enabled = chatState.isConnected
        )
    }
}

@Composable
private fun ChatHeader(
    bookingId: String,
    chatService: ChatService,
    onBack: () -> Unit
) {
    val activeChat = chatService.getActiveChat(bookingId)

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trip Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Participants",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${activeChat?.participants?.size ?: 0} participants",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Online status indicator
                    val onlineCount = activeChat?.participants?.count { it.isOnline } ?: 0
                    if (onlineCount > 0) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = "Online",
                            modifier = Modifier.size(8.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$onlineCount online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Connection status
            Icon(
                if (activeChat?.isActive == true) Icons.Default.WifiTethering else Icons.Default.WifiTetheringOff,
                contentDescription = "Connection Status",
                tint = if (activeChat?.isActive == true) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    currentUserType: UserType
) {
    val bubbleColor = when {
        message.messageType == "SYSTEM" -> MaterialTheme.colorScheme.surfaceVariant
        isOwn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = when {
        message.messageType == "SYSTEM" -> MaterialTheme.colorScheme.onSurfaceVariant
        isOwn -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val alignment = if (message.messageType == "SYSTEM") {
        Alignment.Center
    } else if (isOwn) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Sender name (if not own message and not system)
                if (!isOwn && message.messageType != "SYSTEM") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Since we don't have senderType in ChatMessage, we'll determine it from context
                        val senderType = if (message.senderName.contains("Driver") || message.senderId.startsWith("driver")) {
                            UserType.DRIVER
                        } else {
                            UserType.PASSENGER
                        }
                        UserTypeIcon(userType = senderType)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message content
                if (message.messageType == "LOCATION") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.size(16.dp),
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                } else {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                // Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    // Read status for own messages
                    if (isOwn && message.messageType != "SYSTEM") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            modifier = Modifier.size(12.dp),
                            tint = if (message.isRead) Color(0xFF4CAF50) else textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTypeIcon(userType: UserType) {
    val (icon, color) = when (userType) {
        UserType.PASSENGER -> Icons.Default.Person to Color(0xFF2196F3)
        UserType.DRIVER -> Icons.Default.DriveEta to Color(0xFF4CAF50)
        UserType.OPERATOR -> Icons.Default.Business to Color(0xFFFF9800)
        UserType.TODA_ADMIN -> Icons.Default.AdminPanelSettings to Color(0xFF9C27B0)
    }

    Icon(
        icon,
        contentDescription = userType.name,
        modifier = Modifier.size(12.dp),
        tint = color
    )
}

@Composable
private fun MessageInput(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onLocationShare: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Location share button
            IconButton(
                onClick = onLocationShare,
                enabled = enabled
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Share Location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Message input field
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = enabled,
                maxLines = 3,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
