# Release APK Build Instructions

This guide will help you build a signed release APK of PeeReminder that can be installed on your dad's phone (or any Android device) for testing.

## Prerequisites

- Java JDK installed (required for `keytool`)
- Android SDK configured
- Gradle wrapper available (included in project)

## Step 1: Create a Keystore

A keystore is required to sign your release APK. You only need to create it once.

### Option A: Using the Provided Script (Recommended)

1. Make the script executable:
   ```bash
   chmod +x create-keystore.sh
   ```

2. Run the script:
   ```bash
   ./create-keystore.sh
   ```

3. Follow the prompts:
   - Enter a strong password for the keystore (remember this!)
   - Enter your name and organization details when prompted
   - Enter the same password again for the key password

### Option B: Manual Creation

If you prefer to create the keystore manually:

```bash
keytool -genkey -v \
    -keystore app/pe reminder-release.jks \
    -alias "pe reminder" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 9125 \
    -storetype JKS
```

You'll be prompted for:
- Keystore password (remember this!)
- Your name, organization, city, state, country code
- Key password (use the same as keystore password)

## Step 2: Configure Keystore Properties

1. Copy the template file:
   ```bash
   cp keystore.properties.template keystore.properties
   ```

2. Edit `keystore.properties` and fill in your actual values:
   ```properties
   storeFile=app/pe reminder-release.jks
   storePassword=your_actual_store_password
   keyAlias=pe reminder
   keyPassword=your_actual_key_password
   ```

   **Important:** Replace the placeholder values with:
   - The actual password you used when creating the keystore
   - The correct path to your keystore file (if different)

## Step 3: Build the Release APK

### Method 1: Using Gradle Command Line (Recommended)

1. Open a terminal in the project root directory

2. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

   On Windows:
   ```bash
   gradlew.bat assembleRelease
   ```

3. Wait for the build to complete. You should see "BUILD SUCCESSFUL" at the end.

4. Find your APK at:
   ```
   app/build/outputs/apk/release/app-release.apk
   ```

### Method 2: Using Android Studio

1. Open the project in Android Studio

2. Go to **Build** → **Generate Signed Bundle / APK**

3. Select **APK** and click **Next**

4. Choose your keystore:
   - Click **Choose existing...** and select `app/pe reminder-release.jks`
   - Enter your keystore password
   - Select the key alias: `pe reminder`
   - Enter your key password
   - Click **Next**

5. Select **release** build variant and click **Finish**

6. Android Studio will build the APK and show a notification when done. Click **locate** to find the APK file.

## Step 4: Install on Your Dad's Phone

### Option A: USB Transfer

1. Connect the phone to your computer via USB
2. Enable **File Transfer** mode on the phone (when prompted)
3. Copy `app-release.apk` to the phone's storage (Downloads folder works well)
4. On the phone, open **Files** app and navigate to the APK location
5. Tap the APK file to install
6. If prompted, allow installation from "Unknown sources" (Settings → Security → Install unknown apps)

### Option B: Email or Cloud Storage

1. Email the APK to yourself or upload to cloud storage (Google Drive, Dropbox, etc.)
2. On the phone, open the email or cloud storage app
3. Download the APK file
4. Tap the downloaded APK to install
5. Allow installation from unknown sources if prompted

### Option C: ADB Install (Advanced)

If USB debugging is enabled on the phone:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## Troubleshooting

### Build Fails: "Keystore file not found"

- Make sure `keystore.properties` exists and has the correct path to your keystore file
- Verify the keystore file exists at the specified location

### Build Fails: "Password was incorrect"

- Double-check the passwords in `keystore.properties` match what you used when creating the keystore
- Make sure there are no extra spaces or special characters

### Build Fails: "Signing config not found"

- Ensure `keystore.properties` file exists and is properly formatted
- Check that all four properties (storeFile, storePassword, keyAlias, keyPassword) are set

### Installation Fails: "App not installed"

- Make sure you're installing a release APK, not a debug APK
- Check that the phone allows installation from unknown sources
- Verify the APK is not corrupted (try downloading/transferring again)
- Ensure the phone meets the minimum SDK requirement (Android 7.0 / API 24)

### "Parse Error" when installing

- The APK might be corrupted. Try building again
- Make sure you're transferring the complete file

## Security Notes

⚠️ **IMPORTANT:**

- **Never commit** `keystore.properties` or `.jks` files to version control
- Keep a secure backup of your keystore file and passwords
- If you lose the keystore, you won't be able to update the app on Google Play (if you publish it)
- Store the keystore and passwords in a secure password manager

## Next Steps

After testing on your dad's phone:
- If you find bugs, fix them and rebuild
- Increment `versionCode` and `versionName` in `app/build.gradle.kts` for new releases
- Consider enabling ProGuard/R8 for smaller APK size (currently disabled)

## Build Output Location

The release APK will always be located at:
```
app/build/outputs/apk/release/app-release.apk
```

You can rename this file before sharing (e.g., `PeeReminder-v1.0.apk`).
