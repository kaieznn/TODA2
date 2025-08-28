# TODA Project Java Setup Guide

## Issue: JAVA_HOME is not set

You're getting this error because the Gradle build system can't find Java. Here's how to fix it:

## Quick Fix - Set JAVA_HOME for this session:

### Option 1: Use Android Studio's Embedded JDK (Recommended)
Open PowerShell in your project directory and run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./gradlew build
```

### Option 2: If the above path doesn't work, try:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./gradlew build
```

## Permanent Fix - Set JAVA_HOME permanently:

1. **Open System Environment Variables:**
   - Press `Win + R`, type `sysdm.cpl`, press Enter
   - Click "Environment Variables..." button

2. **Add JAVA_HOME:**
   - In "System Variables" section, click "New..."
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Android\Android Studio\jbr`
   - Click OK

3. **Update PATH:**
   - Find "Path" in System Variables, select it, click "Edit..."
   - Click "New" and add: `%JAVA_HOME%\bin`
   - Click OK on all dialogs

4. **Restart PowerShell** and try building again

## Verify Java Installation:
```powershell
java -version
javac -version
```

## Alternative: Use Gradle Wrapper with Java Detection
If you're still having issues, you can also specify the Java path directly:
```powershell
./gradlew -Dorg.gradle.java.home="C:\Program Files\Android\Android Studio\jbr" build
```
