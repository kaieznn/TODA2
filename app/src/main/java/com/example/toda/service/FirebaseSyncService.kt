package com.example.toda.service

import android.content.Context
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncService @Inject constructor() {

    private val database: DatabaseReference = Firebase.database.reference
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Create a directory for Firebase sync files
    private fun getFirebaseDataDir(context: Context): File {
        val firebaseDir = File(context.filesDir, "firebase_sync")
        if (!firebaseDir.exists()) {
            firebaseDir.mkdirs()
        }
        return firebaseDir
    }

    // Also create files in the project directory (if accessible)
    private fun getProjectDataDir(): File? {
        return try {
            // Try to create in project root directory
            val projectDir = File("C:\\Users\\kenai\\AndroidStudioProjects\\TODA-MASTER-LATEST\\firebase_data")
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }
            projectDir
        } catch (e: Exception) {
            null // Fall back to internal storage only
        }
    }

    fun startSyncing(context: Context) {
        val internalDataDir = getFirebaseDataDir(context)
        val projectDataDir = getProjectDataDir()

        // Create a startup log file to confirm the service is running
        val startupLog = mapOf<String, String>(
            "syncServiceStarted" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            "internalDataPath" to internalDataDir.absolutePath,
            "projectDataPath" to (projectDataDir?.absolutePath ?: "Not accessible"),
            "status" to "Firebase sync service started successfully"
        )

        val startupJson = gson.toJson(startupLog)
        writeToFile(File(internalDataDir, "sync_status.json"), startupJson)
        projectDataDir?.let {
            writeToFile(File(it, "sync_status.json"), startupJson)
        }

        // Sync bookings table
        syncBookings(internalDataDir, projectDataDir)

        // Sync users table
        syncUsers(internalDataDir, projectDataDir)

        // Sync active bookings index
        syncActiveBookings(internalDataDir, projectDataDir)

        // Sync driver queue (for hardware integration)
        syncDriverQueue(internalDataDir, projectDataDir)

        // Create a summary file with all important data
        syncSummary(internalDataDir, projectDataDir)
    }

    private fun syncBookings(internalDir: File, projectDir: File?) {
        database.child("bookings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val bookingsData = mutableMapOf<String, Any?>()

                    snapshot.children.forEach { child ->
                        bookingsData[child.key ?: "unknown"] = child.value
                    }

                    val jsonData = mapOf(
                        "lastUpdated" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "totalBookings" to bookingsData.size,
                        "bookings" to bookingsData
                    )

                    val jsonString = gson.toJson(jsonData)

                    // Write to internal storage
                    writeToFile(File(internalDir, "bookings.json"), jsonString)

                    // Write to project directory if accessible
                    projectDir?.let {
                        writeToFile(File(it, "bookings.json"), jsonString)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun syncUsers(internalDir: File, projectDir: File?) {
        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val usersData = mutableMapOf<String, Any?>()

                    snapshot.children.forEach { child ->
                        usersData[child.key ?: "unknown"] = child.value
                    }

                    val jsonData = mapOf(
                        "lastUpdated" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "totalUsers" to usersData.size,
                        "users" to usersData
                    )

                    val jsonString = gson.toJson(jsonData)

                    // Write to internal storage
                    writeToFile(File(internalDir, "users.json"), jsonString)

                    // Write to project directory if accessible
                    projectDir?.let {
                        writeToFile(File(it, "users.json"), jsonString)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun syncActiveBookings(internalDir: File, projectDir: File?) {
        database.child("activeBookings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val activeBookingsData = mutableMapOf<String, Any?>()

                    snapshot.children.forEach { child ->
                        activeBookingsData[child.key ?: "unknown"] = child.value
                    }

                    val jsonData = mapOf(
                        "lastUpdated" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "totalActiveBookings" to activeBookingsData.size,
                        "activeBookings" to activeBookingsData
                    )

                    val jsonString = gson.toJson(jsonData)

                    // Write to internal storage
                    writeToFile(File(internalDir, "active_bookings.json"), jsonString)

                    // Write to project directory if accessible
                    projectDir?.let {
                        writeToFile(File(it, "active_bookings.json"), jsonString)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun syncDriverQueue(internalDir: File, projectDir: File?) {
        database.child("driverQueue").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val queueData = mutableMapOf<String, Any?>()

                    snapshot.children.forEach { child ->
                        queueData[child.key ?: "unknown"] = child.value
                    }

                    val jsonData = mapOf(
                        "lastUpdated" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        "queueLength" to queueData.size,
                        "driverQueue" to queueData
                    )

                    val jsonString = gson.toJson(jsonData)

                    // Write to internal storage
                    writeToFile(File(internalDir, "driver_queue.json"), jsonString)

                    // Write to project directory if accessible
                    projectDir?.let {
                        writeToFile(File(it, "driver_queue.json"), jsonString)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun syncSummary(internalDir: File, projectDir: File?) {
        // Listen to root database changes for summary
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val summary = mutableMapOf<String, Any>()

                    // Count different types of data
                    snapshot.children.forEach { child ->
                        when (child.key) {
                            "bookings" -> summary["totalBookings"] = child.childrenCount
                            "users" -> summary["totalUsers"] = child.childrenCount
                            "activeBookings" -> summary["activeBookings"] = child.childrenCount
                            "driverQueue" -> summary["driversInQueue"] = child.childrenCount
                            "availableDrivers" -> summary["availableDrivers"] = child.childrenCount
                        }
                    }

                    summary["lastUpdated"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    summary["databaseStructure"] = snapshot.children.map { it.key }.toList()

                    val jsonString = gson.toJson(summary)

                    // Write to internal storage
                    writeToFile(File(internalDir, "database_summary.json"), jsonString)

                    // Write to project directory if accessible
                    projectDir?.let {
                        writeToFile(File(it, "database_summary.json"), jsonString)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun writeToFile(file: File, content: String) {
        try {
            FileWriter(file).use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            // Handle write error
        }
    }

    fun stopSyncing() {
        scope.cancel()
    }
}
