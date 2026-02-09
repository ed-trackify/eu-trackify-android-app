# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trackify EU is a native Android delivery driver and distributor management application. It's a clone of the original delivery app, configured to work with the Trackify EU backend at `https://eu.trackify.net/api`.

**Package ID**: `eu.trackify.net`
**Min SDK**: 21 (Android 5.0)
**Target SDK**: 33 (Android 13)
**Language**: Java (not Kotlin)
**Architecture**: Activity-based with custom view controllers

## Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (requires signing configuration in app/build.gradle)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## Code Architecture

### Main Application Structure

The app uses a **single Activity** pattern with custom view controllers rather than Fragments for the main UI:

- **App.java**: Single FragmentActivity that hosts all controllers
- **Controllers**: Custom ViewGroups that handle specific UI sections (LoginCtrl, UserDistributorTabCtrl, etc.)
- **Fragments**: Used only for tabbed content within controllers (shipment lists, notes, pictures, SMS history)

### Core Components

**Communicator.java** (`app/src/main/java/common/Communicator.java:38`)
- Central networking class containing all API endpoint methods
- Base URL: `https://eu.trackify.net/api`
- All requests add headers: `app-name` (app name) and `api-key` (auth token)
- Each method creates a background thread and uses callbacks (`IServerResponse`)
- Supports offline mode by queuing requests to `PendingRequestsLoader`

**Rest.java** (`app/src/main/java/common/Rest.java`)
- Low-level HTTP client using Apache HttpClient
- Handles GET, POST, file uploads, and JSON requests
- Automatically adds authentication headers via `GetStaticHeaders()`
- Uses `Rest_HttpsFix` for HTTPS/TLS support

**AppModel.java** (`app/src/main/java/common/AppModel.java`)
- Application state manager and utility class
- Handles network availability checking
- Manages SharedPreferences storage
- Error logging and exception handling
- Database instance management

**DatabaseManager.java** (`app/src/main/java/common/DatabaseManager.java`)
- SQLite database for offline data and pending requests
- Tables: `PendingRequests`, `PostParams`
- Database file copied from assets on first run
- Version management with auto-deletion on version change

### User Types and Controllers

The app supports two active user types (others are commented out):

1. **Distributor** (`UserType.Distributor`)
   - Main controller: `UserDistributorTabCtrl` with tabs for My Shipments, Reconcile, Returns
   - Fragment: `Draggable_UserDistributorShipmentsFragment` (supports drag-and-drop reordering)
   - Fragment: `UserDistributorShipmentsFragment` (standard list)

2. **Driver** (`UserType.Driver`)
   - Controller: `UserDistributorDriverCtrl`
   - Simplified interface without refresh button

### Shipment Management

**ShipmentResponse.java** and **ShipmentWithDetail.java**
- API returns shipments with embedded detail data
- Three shipment types: MyShipments, ReconcileShipments, Returns
- Endpoints:
  - `get_driver_shipments_v2.php` - My Shipments
  - `get_driver_non_reconciled_v2.php` - Reconcile
  - `get_driver_returns.php` - Returns

**Wurth Client Filtering**
The app has special logic for client_id `2785` (Wurth):
- **Wurth/1**: tracking_id ends with "-01"
- **Wurth/N**: tracking_id does NOT end with "-01"
- **Others**: All other clients

This filtering is hardcoded in shipment list adapters.

### Offline Mode

When network is unavailable:
- API calls save to `PendingRequestsLoader` database queue
- User sees message: "Update saved locally. Will synchronize with server when you regain internet connection"
- Background workers periodically retry pending requests
- Implemented in: `Communicator.SYNC_MSG` and `AppModel.Pendings.InsertGet/InsertPost`

### Background Workers

Uses **Android WorkManager** for reliable background tasks:

**LocationUpdateWorker** (`app/src/main/java/common/workers/LocationUpdateWorker.java`)
- Periodic GPS location updates to backend
- Endpoint: `gps_update.php`

**DataSyncWorker** (`app/src/main/java/common/workers/DataSyncWorker.java`)
- Syncs offline changes when connection restored
- Processes pending requests queue

**SMSReplyWorker** (`app/src/main/java/common/workers/SMSReplyWorker.java`)
- Monitors incoming SMS replies from customers
- Batches and sends to: `sms/reply_handler.php`
- Uses `SMSReplyMonitor` to detect and match replies to shipments

Workers scheduled in `WorkManagerScheduler.java`.

### SMS System

**SMS Templates** (`SMSTemplates.java` and `SMSConfig.java`)
- Country-specific templates (North Macedonia, Kosovo, etc.)
- Auto-detection based on phone number prefix
- Status-based messages (pickup, delivery, attempt, etc.)
- COD amount inclusion in templates

**SMS Sending** (`SMSHelper.java`)
- Queue-based sending via `SMSQueueManager`
- Rate limiting to avoid Android SMS limits
- Logs all sent SMS to backend: `sms/app_auto_sms.php`

**SMS Reply Monitoring** (`SMSReplyMonitor.java`)
- Listens for incoming SMS via BroadcastReceiver
- Matches replies to shipments by phone number
- Batches submissions every 2 minutes
- Endpoint: `sms/reply_handler.php` with header `X-API-Key: osafu2379jsaf`

### Route Optimization

**RouteCalculator.java** (`app/src/main/java/common/route/RouteCalculator.java`)
- Uses external routing API to calculate optimal delivery routes
- Displays route on Google Maps via `RoutingCtrl`
- Toggle route view with location icon in toolbar

### Signature Capture

**SignatureCtrl.java** and **SignatureView.java**
- Custom canvas-based signature capture
- Converts to base64 PNG
- Uploads to: `upload_signature_v2.php`
- Supports PIN entry for secure delivery

### Image Handling

**ImageUploadCtrl.java** and **AttachmentManager.java**
- Camera capture via `ActivityResultLauncher`
- Gallery selection support
- Image resizing via `imageResizer` module (imported project)
- Upload endpoint: `upload_image.php`

**Profile Pictures**
- Uploaded via: `profile/upload_picture.php`
- Base64 encoded JPG format
- Managed in `SettingsCtrl`

### Permissions

Dynamic permission handling based on Android version:

**Android 13+ (SDK 33)**:
- `READ_MEDIA_IMAGES` instead of `READ_EXTERNAL_STORAGE`
- No `WRITE_EXTERNAL_STORAGE`

**Android 12+ (SDK 31)**:
- `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`
- Legacy `BLUETOOTH` only for SDK < 31

All permissions requested in `App.onCreate()` using Dexter library.

### Multi-Language Support

**Supported Languages**:
- English (default)
- Macedonian (`values-mk`)
- Albanian (`values-sq`)

Language selection in `SettingsCtrl.java` via `applyLanguage()`.
Locale applied in `App.attachBaseContext()` before activity creation.

## Backend API Integration

All API documentation is in `BACKEND_IMPLEMENTATION.md`. Key points:

**Authentication**:
- Login: `app_login_auth.php` (not `/login.php` as documented)
- Returns `UserRef` object with `auth_key`
- Plain text passwords over HTTPS (server handles hashing)

**Headers on All Requests**:
```
app-name: Trackify EU
api-key: <user's auth_key>
```

**Common Endpoints**:
- Shipments: `get_driver_shipments_v2.php`, `get_driver_non_reconciled_v2.php`, `get_driver_returns.php`
- Status update: `update_status.php?key=<tracking_id>&user=<username>&status=<status>&status_id=<id>`
- Signature: `upload_signature_v2.php` (POST with base64 image)
- Pictures: `upload_image.php` (multipart upload)
- Comments: `add_shipment_comment_v2.php` (POST with note_type)
- GPS: `gps_update.php` (POST with lat/long)
- SMS: `sms/app_auto_sms.php` (log sent SMS), `sms/reply_handler.php` (receive replies)
- App updates: `/app/version_check.php` (note: `/app` not `/api`)

## Important Configuration Files

**API Base URL**: `Communicator.java:38`
```java
public final static String URL = "https://eu.trackify.net/api";
```

**App Name**: `app/src/main/res/values/strings.xml`
```xml
<string name="app_name">Trackify EU</string>
```

**Package ID**: `app/build.gradle:16`
```gradle
applicationId "eu.trackify.net"
```

**Version**: `app/build.gradle:19-20`
```gradle
versionCode 1
versionName "1.0"
```

## Development Notes

### Barcode Scanning
- Uses ZXing via `journeyapps:zxing-android-embedded:4.3.0`
- Scanner launched via `App.ShowScanner()`
- Results handled in `barcodeLauncher` ActivityResultLauncher
- Supports both 1D and 2D barcodes

### Printer Integration
**Currently DISABLED** (see `App.java:236-270`)
- PrinterManager initialization commented out
- Printer icon hidden: `iv_ConnectPrinter.setVisibility(View.GONE)`
- Uses `androidprintsdk.jar` for Bluetooth thermal printers
- Can be re-enabled by uncommenting and enabling in SettingsCtrl

### Crash Logging
All uncaught exceptions logged to file:
```java
Thread.setDefaultUncaughtExceptionHandler()
AppModel.WriteLog(AppModel.LOG_FILE_NAME, source, stack)
```

### Network Retry Logic
`NetworkRetryManager.java` handles automatic retry with exponential backoff for failed requests.

### Loading States
Modal loading overlay with branded messages:
- `App.SetProcessing()` - "Updating your deliveries..."
- `App.SetUploading()` - "Syncing shipment data..."
- `App.SetDownloading()` - "Loading latest updates..."
- `App.SetLoading()` - "Preparing your dashboard..."

## Testing Notes

When testing locally:
1. Update `Communicator.URL` to point to local backend
2. Ensure backend implements all endpoints in `BACKEND_IMPLEMENTATION.md`
3. Test Wurth filtering with client_id = 2785 and tracking IDs ending in "-01"
4. Test offline mode by disabling network mid-operation
5. Check pending requests database after offline actions
6. Test permission flows on Android 13+ and Android 12+ devices
7. Verify SMS sending stays under Android rate limits (30 per 30 minutes by default)

## Common Patterns

### Adding a New API Endpoint

1. Add method to `Communicator.java`:
```java
public static void YourMethod(final String param, final IServerResponse callback) {
    if (!AppModel.Object.IsNetworkAvailable(false)) {
        callback.onCompleted(false, "Internet connection not available");
        return;
    }

    (new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                String url = URL + "/your_endpoint.php";
                HashMap<String, Object> reqParams = new HashMap<>();
                reqParams.put("param", param);

                String json = Rest.Post(url, reqParams);
                YourResponse obj = new Gson().fromJson(json, YourResponse.class);
                callback.onCompleted(true, null, obj);
            } catch (Exception ex) {
                callback.onCompleted(false, "Something went wrong, Please try again");
                AppModel.ApplicationError(ex, "Communicator::YourMethod");
            }
        }
    })).start();
}
```

2. Create response model class if needed (extends from existing patterns)

3. Call from UI thread with callback handling

### Working with Controllers

Controllers are custom ViewGroups in layout files:
```java
public class YourCtrl extends LinearLayout {
    public YourCtrl(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Initialize views here
    }

    public void SetVisibility(boolean show) {
        setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
```

Register in `App.java` and add to layout XML with full package path.
