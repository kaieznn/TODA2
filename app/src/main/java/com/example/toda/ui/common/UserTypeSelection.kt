package com.example.toda.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UserTypeSelection(
    modifier: Modifier = Modifier,
    onUserTypeSelected: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to TODA Booking",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { onUserTypeSelected("customer") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("I'm a Customer")
        }

        Button(
            onClick = { onUserTypeSelected("driver_login") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Driver Login")
        }

        Button(
            onClick = { onUserTypeSelected("registration") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Registration Portal")
        }

        Button(
            onClick = { onUserTypeSelected("admin") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Admin Dashboard")
        }
    }
}