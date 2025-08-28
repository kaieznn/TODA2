package com.example.toda

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TODAApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable offline persistence for Firebase Realtime Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Set cache size to 10MB for better performance
        FirebaseDatabase.getInstance().setPersistenceCacheSizeBytes(10 * 1024 * 1024)
    }
}
