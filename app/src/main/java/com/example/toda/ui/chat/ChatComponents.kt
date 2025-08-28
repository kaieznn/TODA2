package com.example.toda.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun ChatFloatingButton(
    bookingId: String,
    chatService: ChatService,
    currentUser: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadCount = chatService.getUnreadCount(bookingId)
    val isActive = chatService.getActiveChat(bookingId)?.isActive ?: false

    if (isActive) {
        Box(modifier = modifier) {
            FloatingActionButton(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Open Chat"
                )
            }

            // Unread message badge
            if (unreadCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ChatCard(
    booking: Booking,
    chatService: ChatService,
    currentUser: User,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeChat = chatService.getActiveChat(booking.id)
    val unreadCount = chatService.getUnreadCount(booking.id)

    if (activeChat?.isActive == true) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            onClick = onOpenChat
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Chat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trip Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val lastMessage = activeChat.lastMessage
                    if (lastMessage != null) {
                        Text(
                            text = "${lastMessage.senderName}: ${lastMessage.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = "Start chatting with your driver and operator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Unread count badge
                    if (unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun QuickChatActions(
    bookingId: String,
    chatService: ChatService,
    currentUser: User,
    modifier: Modifier = Modifier
) {
    val quickMessages = when (currentUser.userType) {
        UserType.PASSENGER -> listOf(
            "I'm ready for pickup",
            "Where are you?",
            "Thank you!",
            "I'm running late"
        )
        UserType.DRIVER -> listOf(
            "On my way",
            "Arrived at pickup",
            "5 minutes away",
            "Traffic delay"
        )
        UserType.OPERATOR -> listOf(
            "Driver assigned",
            "Checking status",
            "How can I help?",
            "Trip confirmed"
        )
        else -> emptyList()
    }

    if (quickMessages.isNotEmpty()) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickMessages) { message ->
                QuickMessageChip(
                    message = message,
                    onClick = {
                        chatService.sendMessage(
                            ChatMessage(
                                bookingId = bookingId,
                                senderId = currentUser.id,
                                senderName = currentUser.name,
                                receiverId = "all",
                                message = message
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickMessageChip(
    message: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Send,
                contentDescription = "Send",
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
fun ChatNotificationBanner(
    newMessage: ChatMessage?,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    newMessage?.let { message ->
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "New Message",
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New message from ${message.senderName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2
                    )
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }

                    TextButton(onClick = onOpenChat) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onLocationShare: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = enabled,
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(onClick = onLocationShare) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Share Location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(48.dp),
                containerColor = if (messageText.isNotBlank())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = if (messageText.isNotBlank())
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TypingIndicator(
    userName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$userName is typing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Animated typing dots
        var dotCount by remember { mutableStateOf(1) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(500)
                dotCount = if (dotCount >= 3) 1 else dotCount + 1
            }
        }

        Text(
            text = ".".repeat(dotCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    currentUserType: UserType,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth(),
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
                if (!isOwn && message.messageType != "SYSTEM") {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                when (message.messageType) {
                    "LOCATION" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    }
                    "SYSTEM" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = "System Update",
                                modifier = Modifier.size(16.dp),
                                tint = textColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )

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

private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "now" // Less than 1 minute
        diff < 3600000 -> "${diff / 60000}m" // Less than 1 hour
        diff < 86400000 -> {
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            formatter.format(java.util.Date(timestamp))
        }
        else -> {
            val formatter = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            formatter.format(java.util.Date(timestamp))
        }
    }
}
