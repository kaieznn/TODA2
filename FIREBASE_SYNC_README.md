# Firebase Real-time Sync Documentation

## Overview
This system automatically syncs your Firebase Realtime Database data to local JSON files in real-time, allowing you to monitor database changes locally.

## Directory Structure
The sync service creates JSON files in two locations:
1. **App Internal Storage**: `/data/data/com.example.toda/files/firebase_sync/`
2. **Project Directory**: `C:\Users\kenai\AndroidStudioProjects\TODA-MASTER-LATEST\firebase_data/`

## Files Created

### 1. `bookings.json`
Contains all booking records with metadata:
```json
{
  "lastUpdated": "2025-08-28 15:30:45",
  "totalBookings": 5,
  "bookings": {
    "abc123xyz789": {
      "customerId": "user123",
      "customerName": "John Doe",
      "pickupLocation": "Barangay 177 Hall",
      "destination": "SM North EDSA", 
      "status": "PENDING",
      "actualFare": 25.50,
      "distance": 12.75,
      "timestamp": 1693123456789
    }
  }
}
```

### 2. `active_bookings.json`
Contains the active bookings index:
```json
{
  "lastUpdated": "2025-08-28 15:30:45",
  "totalActiveBookings": 3,
  "activeBookings": {
    "abc123xyz789": true,
    "def456uvw012": true
  }
}
```

### 3. `users.json`
Contains all user records:
```json
{
  "lastUpdated": "2025-08-28 15:30:45", 
  "totalUsers": 10,
  "users": {
    "user123": {
      "name": "John Doe",
      "phoneNumber": "09123456789",
      "userType": "PASSENGER"
    }
  }
}
```

### 4. `driver_queue.json`
Contains hardware driver queue:
```json
{
  "lastUpdated": "2025-08-28 15:30:45",
  "queueLength": 2,
  "driverQueue": {
    "queue1": {
      "driverId": "driver001",
      "queuePosition": 1
    }
  }
}
```

### 5. `database_summary.json`
Contains database overview:
```json
{
  "lastUpdated": "2025-08-28 15:30:45",
  "totalBookings": 5,
  "totalUsers": 10,
  "activeBookings": 3,
  "driversInQueue": 2,
  "availableDrivers": 4,
  "databaseStructure": ["bookings", "users", "activeBookings", "driverQueue"]
}
```

## How It Works

1. **Real-time Listeners**: The sync service attaches Firebase listeners to monitor changes
2. **Automatic Updates**: When data changes in Firebase, the JSON files update immediately  
3. **Formatted Output**: All JSON files are pretty-printed for easy reading
4. **Timestamp Tracking**: Each file includes when it was last updated

## Setup Instructions

1. **Create Directory**: Create the folder `firebase_data` in your project root:
   ```
   C:\Users\kenai\AndroidStudioProjects\TODA-MASTER-LATEST\firebase_data\
   ```

2. **Run Your App**: The sync service starts automatically when the app launches

3. **Monitor Changes**: 
   - Create a booking → Check `bookings.json`
   - Accept a booking → See status change in real-time
   - View summary → Check `database_summary.json`

## Benefits

- **Real-time Monitoring**: See database changes as they happen
- **Debugging**: Easy to track what's happening in your database
- **Data Backup**: Local copies of your Firebase data
- **Development**: Perfect for testing and validation
- **Hardware Integration**: Monitor driver queue and hardware interactions

## File Locations

**Internal Storage** (Always works):
- Path: `/data/data/com.example.toda/files/firebase_sync/`
- Access: Via Android Studio Device File Explorer

**Project Directory** (If permissions allow):
- Path: `C:\Users\kenai\AndroidStudioProjects\TODA-MASTER-LATEST\firebase_data/`
- Access: Direct file system access

## Usage Examples

### Monitor Booking Flow
1. Customer creates booking → `bookings.json` shows new PENDING booking
2. Operator accepts → `bookings.json` status changes to ACCEPTED  
3. Hardware assigns driver → `assignedDriverId` field updates

### Track Database Activity
- Watch `database_summary.json` for overall statistics
- Monitor `active_bookings.json` for queue management
- Check `users.json` for user registrations

The sync service runs continuously while your app is active and stops when the app is closed.
