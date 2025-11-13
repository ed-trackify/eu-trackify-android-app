# Trackify EU Android App

## Overview
Trackify EU is a delivery driver and distributor management Android application. This is a clone of the MEX delivery app, configured to work with the Trackify EU backend infrastructure.

## App Details
- **App Name**: Trackify EU
- **Package ID**: `eu.trackify.net`
- **Base URL**: `https://eu.trackify.net/api`
- **Version**: 1.0 (Build 1)
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 33 (Android 13)
- **Supports**: Android 5.0 through Android 15+

## Features
- User authentication with role-based access (Distributor/Driver)
- Shipment management and tracking
- Real-time GPS location tracking
- Digital signature capture
- Photo and document uploads
- SMS notifications and reply monitoring
- COD (Cash on Delivery) tracking
- Client-specific filtering (Wurth integration)
- Offline mode with data synchronization
- Background workers for location updates and data sync
- Barcode scanning for shipment processing
- Route optimization
- Profile management with photo upload
- Multi-language support (English, Macedonian, Albanian)

## Project Structure
```
eu.trackify.net-android-app/
├── app/                          # Main application module
│   ├── src/main/
│   │   ├── java/common/         # Application code
│   │   ├── res/                 # Resources (layouts, drawables, strings)
│   │   └── AndroidManifest.xml  # App manifest
│   ├── build.gradle             # App-level build config
│   └── libs/                    # External libraries
├── dragNDropList/               # Drag and drop list module
├── imageResizer/                # Image resizing module
├── gradle/                      # Gradle wrapper
├── build.gradle                 # Project-level build config
├── settings.gradle              # Project settings
├── BACKEND_IMPLEMENTATION.md    # Backend API documentation
├── .gitignore                   # Git ignore rules
└── README.md                    # This file
```

## Backend Integration
The app communicates with the backend at `https://eu.trackify.net/api`. All API endpoints and requirements are documented in **[BACKEND_IMPLEMENTATION.md](./BACKEND_IMPLEMENTATION.md)**.

### Key Backend Endpoints
- `/login.php` - User authentication
- `/get_shipments_v2.php` - Fetch shipments
- `/update_shipment_status.php` - Update delivery status
- `/upload_signature_v2.php` - Submit delivery signatures
- `/upload_picture.php` - Upload photos
- `/sms/reply_handler.php` - Process SMS replies
- `/check_updates.php` - App version check

See [BACKEND_IMPLEMENTATION.md](./BACKEND_IMPLEMENTATION.md) for complete API documentation.

## Building the App

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 8 or higher
- Android SDK with API 33
- Gradle 7.4+

### Build Commands
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (requires signing configuration)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Signing Configuration
For release builds, add your signing configuration to `app/build.gradle`:
```gradle
android {
    signingConfigs {
        release {
            storeFile file("path/to/your/keystore.jks")
            storePassword "your-store-password"
            keyAlias "your-key-alias"
            keyPassword "your-key-password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'
        }
    }
}
```

## Configuration

### API URL
The base API URL is configured in:
- **File**: `app/src/main/java/common/Communicator.java`
- **Line**: 38
- **Current**: `https://eu.trackify.net/api`

### App Name
To change the app name, update:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-mk/strings.xml` (Macedonian)
- `app/src/main/res/values-sq/strings.xml` (Albanian)

### Package ID
To change the package identifier:
1. Update `namespace` and `applicationId` in `app/build.gradle`
2. Update FileProvider authority in `app/src/main/AndroidManifest.xml`
3. Update all R imports in Java files

## Permissions
The app requires the following permissions:
- **Storage**: READ_MEDIA_IMAGES (Android 13+) or READ/WRITE_EXTERNAL_STORAGE
- **Phone & SMS**: CALL_PHONE, SEND_SMS, READ_SMS, RECEIVE_SMS, READ_PHONE_STATE
- **Location**: ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
- **Camera**: CAMERA
- **Audio**: RECORD_AUDIO
- **Bluetooth**: BLUETOOTH_CONNECT, BLUETOOTH_SCAN (Android 12+)

## Android Version Compatibility

### Android 13+ (SDK 33)
- Uses READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE no longer used

### Android 12+ (SDK 31)
- Uses BLUETOOTH_CONNECT and BLUETOOTH_SCAN
- Legacy BLUETOOTH permissions only for Android 11 and below

### Android 15 (SDK 35)
- Fully compatible with stricter permission requirements
- Dynamic permission requests based on OS version

## Git Repository

### Initial Setup
```bash
# Repository is already initialized with first commit
cd eu.trackify.net-android-app

# To push to a remote repository:
git remote add origin https://github.com/yourusername/trackify-eu-android.git
git branch -M main
git push -u origin main
```

### Git Ignore
The `.gitignore` file excludes:
- Build artifacts (APK, AAR, DEX)
- Gradle cache and build folders
- IDE files (.idea, *.iml)
- Local configuration (local.properties)
- Keystore files
- Generated files

## Development Notes

### Debug vs Release
- **Debug builds**: Use for development and testing
- **Release builds**: Require signing configuration for distribution

### Testing Backend Integration
1. Ensure backend is running at `https://eu.trackify.net`
2. Test login endpoint first
3. Use provided test credentials (if available)
4. Monitor app logs for API responses

### Known Issues
- Printer functionality is currently disabled (commented out in `App.java`)
- Build requires `local.properties` with SDK path
- SMS limit modifier requires root access

## Support & Documentation
- Backend API: See [BACKEND_IMPLEMENTATION.md](./BACKEND_IMPLEMENTATION.md)
- Original app: Based on MEX Delivery App
- Issues: Report to development team

## Version History
- **v1.0 (Build 1)** - Initial release
  - Cloned from MEX delivery app
  - Updated to Trackify EU branding
  - Configured for https://eu.trackify.net backend
  - Android 13+ and Android 15 compatibility

## License
Proprietary - All rights reserved

---

**Generated**: 2025-01-17
**Platform**: Android
**Framework**: Native (Java)
