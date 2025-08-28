package com.example.toda.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toda.data.ChatMessage
import com.example.toda.data.ChatRoom
import com.example.toda.data.User
import com.example.toda.viewmodel.EnhancedBookingViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingChatScreen(
    user: User,
    bookingId: String,
    onBack: () -> Unit,
    viewModel: EnhancedBookingViewModel = hiltViewModel()
) {
    val chatMessages by viewModel.getChatMessages(bookingId).collectAsStateWithLifecycle(initialValue = emptyList())
    val chatRoom by viewModel.getChatRoom(bookingId).collectAsStateWithLifecycle(initialValue = null)

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Chat Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = chatRoom?.let { room ->
                            when {
                                user.id == room.customerId -> "Chat with ${room.driverName}"
                                else -> "Chat with ${room.customerName}"
                            }
                        } ?: "Loading...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Booking Chat",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(chatMessages) { message ->
                ChatMessageBubble(
                    message = message,
                    isOwnMessage = message.senderId == user.id,
                    isSystemMessage = message.messageType == "SYSTEM"
                )
            }
        }

        // Message Input
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val currentChatRoom = chatRoom
                        if (messageText.isNotBlank() && currentChatRoom != null) {
                            val receiverId = if (user.id == currentChatRoom.customerId) currentChatRoom.driverId else currentChatRoom.customerId

                            // Send message using viewModel
                            viewModel.sendMessage(
                                bookingId = bookingId,
                                senderId = user.id,
                                senderName = user.name,
                                receiverId = receiverId,
                                message = messageText.trim()
                            )
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    isSystemMessage: Boolean
) {
    val backgroundColor = when {
        isSystemMessage -> MaterialTheme.colorScheme.surfaceVariant
        isOwnMessage -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }

    val textColor = when {
        isSystemMessage -> MaterialTheme.colorScheme.onSurfaceVariant
        isOwnMessage -> Color.White
        else -> Color.Black
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isSystemMessage -> Arrangement.Center
            isOwnMessage -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(
                    start = if (isOwnMessage || isSystemMessage) 32.dp else 0.dp,
                    end = if (!isOwnMessage || isSystemMessage) 32.dp else 0.dp
                ),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                bottomEnd = if (isOwnMessage) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (!isOwnMessage && !isSystemMessage) {
                    Text(
                        text = message.senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = message.message,
                    fontSize = if (isSystemMessage) 12.sp else 14.sp,
                    color = textColor,
                    fontWeight = if (isSystemMessage) FontWeight.Normal else FontWeight.Normal
                )

                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// Compact chat component for embedding in booking screens
@Composable
fun InlineChatComponent(
    user: User,
    bookingId: String,
    onOpenFullChat: () -> Unit,
    viewModel: EnhancedBookingViewModel = hiltViewModel()
) {
    val chatMessages by viewModel.getChatMessages(bookingId).collectAsStateWithLifecycle(initialValue = emptyList())
    val chatRoom by viewModel.getChatRoom(bookingId).collectAsStateWithLifecycle(initialValue = null)

    var messageText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFullChat() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            chatRoom == null -> "Chat"
                            user.id == chatRoom?.customerId -> "Chat with ${chatRoom?.driverName ?: "Driver"}"
                            else -> "Chat with ${chatRoom?.customerName ?: "Customer"}"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (chatMessages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${chatMessages.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Latest message preview
            if (chatMessages.isNotEmpty()) {
                val latestMessage = chatMessages.last()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${latestMessage.senderName}: ${latestMessage.message}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to start chatting",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Quick send input
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Quick message...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val currentChatRoom = chatRoom
                        if (messageText.isNotBlank() && currentChatRoom != null) {
                            val receiverId = if (user.id == currentChatRoom.customerId) currentChatRoom.driverId else currentChatRoom.customerId

                            viewModel.sendMessage(
                                bookingId = bookingId,
                                senderId = user.id,
                                senderName = user.name,
                                receiverId = receiverId,
                                message = messageText.trim()
                            )
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
