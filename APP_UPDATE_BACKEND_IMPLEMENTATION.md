# App Auto-Update - Backend Implementation Guide

## Overview

The Trackify EU Android app checks for updates automatically when the user taps the refresh button. If a newer version is available, a pulsing bell notification icon appears in the toolbar. Tapping the bell downloads and installs the update APK.

## Endpoint Required

### `GET /app/version_check.php`

**URL**: `https://eu.trackify.net/app/version_check.php`

> **Note**: This endpoint is under `/app`, NOT `/api`. The app constructs the URL by replacing `/api` with `/app` in the base URL.

**Method**: GET

**Authentication**: None required (public endpoint)

**Response**: JSON

```json
{
    "latest_version": "2",
    "download_url": "https://eu.trackify.net/app/releases/trackify-eu-latest.apk",
    "same_version_msg": "You already have the latest version"
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `latest_version` | string | The latest `versionCode` (integer as string). The app compares this against its built-in `BuildConfig.VERSION_CODE`. If the server value is higher, an update is available. |
| `download_url` | string | Direct URL to download the latest APK file. Must be a publicly accessible HTTPS URL. |
| `same_version_msg` | string | Optional message to display when the user already has the latest version. Can be null or empty. |

### How the App Uses It

1. User taps refresh button → app calls `GET /app/version_check.php`
2. App parses `latest_version` as integer and compares with `BuildConfig.VERSION_CODE`
3. If `latest_version > VERSION_CODE` → shows pulsing notification bell icon
4. User taps notification bell → app downloads APK from `download_url` with progress indicator
5. After download completes → app triggers Android's package installer to install the APK

### Version Code Reference

The current app `versionCode` is set in `app/build.gradle`:
```gradle
versionCode 1
versionName "1.0"
```

Each new release should increment `versionCode` by 1. The `latest_version` in the API response should match the `versionCode` of the newest available APK.

## Implementation Steps

1. **Create the endpoint file** at `eu.trackify.net/app/version_check.php`
2. **Host the APK file** at the URL specified in `download_url` (e.g., `eu.trackify.net/app/releases/`)
3. **Return the JSON response** with the current latest version info

### Minimal PHP Example

```php
<?php
header('Content-Type: application/json');

echo json_encode([
    'latest_version' => '2',
    'download_url' => 'https://eu.trackify.net/app/releases/trackify-eu-latest.apk',
    'same_version_msg' => 'You already have the latest version'
]);
```

### Database-Driven Example

```php
<?php
header('Content-Type: application/json');

// Optionally read from database or config file
$config = [
    'latest_version' => '2',                    // Increment with each release
    'download_url' => 'https://eu.trackify.net/app/releases/trackify-eu-v2.apk',
    'same_version_msg' => 'You already have the latest version'
];

echo json_encode($config);
```

## APK Hosting Requirements

- The APK must be served over HTTPS
- The server must return proper `Content-Length` header for download progress tracking
- Content-Type should be `application/vnd.android.package-archive`
- The APK file should be signed with the same signing key as the installed version (otherwise Android will reject the update)

## Testing

1. Set `latest_version` to a value higher than the app's current `versionCode` (currently `1`)
2. Upload a valid signed APK to the `download_url` location
3. Open the app, tap refresh → notification bell should appear and pulse
4. Tap the bell → APK should download with progress shown → Android installer should open
