# New Laptop Setup Guide for TODA Project

## What's NOT included in the repository that you need to setup manually:

### 1. **SHA-1 Fingerprint Configuration (CRITICAL)**
- Your `google-services.json` is tied to the original laptop's debug keystore
- **Action Required:**
  1. Run `check_sha1.bat` to get your new laptop's SHA-1 fingerprint
  2. Go to [Firebase Console](https://console.firebase.google.com/project/toda-contribution-system/settings/general)
  3. Add the new SHA-1 fingerprint to your Android app configuration
  4. Download the updated `google-services.json` and replace the current one

### 2. **Local Properties File**
- `local.properties` is excluded from git (contains local SDK path)
- **Current content should be:**
```
sdk.dir=C\\:\\Users\\[YOUR_USERNAME]\\AppData\\Local\\Android\\Sdk
```
- Replace `[YOUR_USERNAME]` with your actual username on the new laptop

### 3. **Android SDK Installation**
- Install Android Studio on the new laptop
- Install required SDK platforms:
  - Android API 36 (compileSdk)
  - Android API 24+ (minSdk)
- Install build tools and platform tools

### 4. **Java Development Kit (JDK)**
- Install JDK 11 (required by the project)
- Verify with: `java -version` and `javac -version`

### 5. **Gradle Dependencies**
- First build will download all dependencies
- Ensure stable internet connection for initial sync

### 6. **Firebase Project Access**
- Ensure you have access to the Firebase project: `toda-contribution-system`
- Verify permissions for Realtime Database and Authentication

## Steps to Setup on New Laptop:

### Step 1: Install Prerequisites
1. Install Android Studio
2. Install JDK 11
3. Configure Android SDK (API 36, 24+)

### Step 2: Configure Firebase
1. Run `check_sha1.bat`
2. Copy the SHA-1 fingerprint
3. Add it to Firebase Console
4. Download new `google-services.json`
5. Replace the existing file

### Step 3: Update Local Configuration
1. Update `local.properties` with correct SDK path
2. Sync project in Android Studio

### Step 4: Build and Test
1. Clean project: `./gradlew clean`
2. Build project: `./gradlew build`
3. Run on device/emulator

## Common Issues and Solutions:

### Issue: "google-services plugin could not detect any version"
- **Solution:** Update `google-services.json` with new SHA-1 fingerprint

### Issue: "PERMISSION_DENIED: Missing or insufficient permissions"
- **Solution:** Check Firebase Security Rules and ensure proper authentication

### Issue: "SDK location not found"
- **Solution:** Update `local.properties` with correct SDK path

### Issue: Build fails with dependency errors
- **Solution:** Clear gradle cache: `./gradlew clean` and sync project

## Verification Checklist:
- [ ] Android Studio installed and configured
- [ ] JDK 11 installed
- [ ] SHA-1 fingerprint added to Firebase
- [ ] Updated `google-services.json` downloaded
- [ ] `local.properties` configured correctly
- [ ] Project builds successfully
- [ ] App runs and can connect to Firebase
