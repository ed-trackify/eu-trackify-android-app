# Trackify EU - Backend Implementation Guide

## Overview
This document outlines the backend API requirements for the Trackify EU Android app. The app is a clone of the MEX delivery app, configured to work with a new backend at `https://eu.trackify.net`.

## App Configuration
- **App Name**: Trackify EU
- **Package ID**: `eu.trackify.net`
- **Base URL**: `https://eu.trackify.net/api`
- **Version**: 1.0 (Build 1)

## Required API Endpoints

All endpoints should be accessible at `https://eu.trackify.net/api/[endpoint]`

### Authentication

#### 1. Login
- **Endpoint**: `/login.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: "" (empty on login)
- **Parameters**:
  - `user`: Username
  - `pass`: Password (plain text - server handles hashing)
- **Response**:
```json
{
  "user": "username",
  "authenticated": "true",
  "user_type": 1,  // 1 = Distributor, 2 = Driver
  "user_id": "123",
  "auth_key": "generated_session_token",
  "error_message": ""  // Only if authentication fails
}
```

#### 2. Change Password
- **Endpoint**: `/change_password.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `old_password`: Current password
  - `new_password`: New password
- **Response**:
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

### Shipments

#### 3. Get Shipments
- **Endpoint**: `/get_shipments_v2.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `shipments_type`: "MyShipments" | "ReconcileShipments" | "Returns"
- **Response**:
```json
[
  {
    "shipment_id": "123",
    "tracking_id": "TRACK123",
    "exchange_tracking_id": "TRACK456",  // Optional
    "client_id": "2785",  // For Wurth filters
    "receiver_name": "John Doe",
    "receiver_phone": "+1234567890",
    "receiver_address": "123 Main St",
    "city": "City Name",
    "cod_amount": "100.00",
    "status": 1,  // 1=Pending, 2=InDelivery, 3=Delivered, etc.
    "notes": "Delivery instructions",
    "latitude": "41.9973",
    "longitude": "21.4280"
  }
]
```

#### 4. Update Shipment Status
- **Endpoint**: `/update_shipment_status.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `shipment_id`: Shipment ID
  - `status`: New status code
  - `user_id`: User ID
  - `latitude`: GPS latitude (optional)
  - `longitude`: GPS longitude (optional)
- **Response**:
```json
{
  "success": true,
  "message": "Status updated"
}
```

### Signature & Media

#### 5. Upload Signature
- **Endpoint**: `/upload_signature_v2.php`
- **Method**: POST (Multipart)
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `shipment_id`: Shipment ID
  - `img_string`: Base64 encoded signature image
  - `full_name`: Receiver's name
  - `pin`: PIN code (optional)
  - `status`: Shipment status
- **Response**:
```json
{
  "success": true,
  "signature_url": "https://eu.trackify.net/uploads/signatures/sig_123.png"
}
```

#### 6. Upload Picture
- **Endpoint**: `/upload_picture.php`
- **Method**: POST (Multipart)
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `shipment_id`: Shipment ID
  - `picture`: Image file
  - `picture_type`: "delivery" | "note" | "issue"
- **Response**:
```json
{
  "success": true,
  "picture_url": "https://eu.trackify.net/uploads/pictures/pic_123.jpg"
}
```

### Notes & Comments

#### 7. Submit Note
- **Endpoint**: `/submit_note.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `shipment_id`: Shipment ID
  - `user_id`: User ID
  - `note_text`: Note content
  - `note_type`: "general" | "delivery" | "issue"
- **Response**:
```json
{
  "success": true,
  "note_id": "456"
}
```

#### 8. Get Notes
- **Endpoint**: `/get_notes.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `shipment_id`: Shipment ID
- **Response**:
```json
[
  {
    "note_id": "456",
    "note_text": "Customer requested morning delivery",
    "note_type": "delivery",
    "created_by": "John Driver",
    "created_at": "2025-01-17 10:30:00"
  }
]
```

### SMS System

#### 9. Send SMS
- **Endpoint**: `/send_sms.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `shipment_id`: Shipment ID
  - `phone`: Recipient phone number
  - `message`: SMS message
  - `user_id`: User ID
- **Response**:
```json
{
  "success": true,
  "message_id": "sms_789"
}
```

#### 10. SMS Reply Handler
- **Endpoint**: `/sms/reply_handler.php`
- **Method**: POST
- **Headers**:
  - `X-API-Key`: System API key (for authentication)
  - `api-key`: User's auth_key
- **Parameters**:
  - `replies`: Array of SMS reply objects
```json
{
  "replies": [
    {
      "phone": "+1234567890",
      "message": "Customer reply text",
      "timestamp": 1642425600000,
      "device_id": "device_identifier"
    }
  ]
}
```
- **Response**:
```json
{
  "success": true,
  "processed": 5,
  "matched_shipments": ["TRACK123", "TRACK456"]
}
```

### Location & Tracking

#### 11. Update Location
- **Endpoint**: `/update_location.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `latitude`: GPS latitude
  - `longitude`: GPS longitude
  - `accuracy`: GPS accuracy in meters
  - `timestamp`: Unix timestamp
- **Response**:
```json
{
  "success": true
}
```

### COD & Financial

#### 12. Get COD Summary
- **Endpoint**: `/get_cod_summary.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `date_from`: Start date (YYYY-MM-DD)
  - `date_to`: End date (YYYY-MM-DD)
- **Response**:
```json
{
  "total_cod": "5000.00",
  "total_delivered": 45,
  "breakdown": [
    {
      "date": "2025-01-17",
      "amount": "1200.00",
      "count": 12
    }
  ]
}
```

### Profile & Settings

#### 13. Update Profile Picture
- **Endpoint**: `/update_profile_picture.php`
- **Method**: POST (Multipart)
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
  - `profile_picture`: Image file
- **Response**:
```json
{
  "success": true,
  "picture_url": "https://eu.trackify.net/uploads/profiles/user_123.jpg"
}
```

#### 14. Get User Profile
- **Endpoint**: `/get_user_profile.php`
- **Method**: POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key
- **Parameters**:
  - `user_id`: User ID
- **Response**:
```json
{
  "user_id": "123",
  "username": "john_driver",
  "full_name": "John Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "profile_picture": "https://eu.trackify.net/uploads/profiles/user_123.jpg",
  "user_type": 2
}
```

### App Updates

#### 15. Check for Updates
- **Endpoint**: `/check_updates.php`
- **Method**: GET or POST
- **Headers**:
  - `app-name`: "Trackify EU"
  - `api-key`: User's auth_key (optional)
- **Parameters**:
  - `current_version`: Current app version code
- **Response**:
```json
{
  "update_available": true,
  "latest_version": "1.1",
  "version_code": 2,
  "download_url": "https://eu.trackify.net/downloads/trackify-eu-1.1.apk",
  "changelog": "Bug fixes and improvements",
  "force_update": false
}
```

## Important Notes

### Authentication
- All authenticated endpoints require the `api-key` header with the user's auth token
- The app sends `app-name: "Trackify EU"` with every request
- Auth tokens should be validated on each request
- Tokens should expire after a reasonable period (e.g., 30 days)

### Client Filtering (Wurth)
The app has special filtering for Wurth client (client_id = 2785):
- **Wurth/1**: Shipments where `client_id = "2785"` AND `tracking_id` ends with "-01"
- **Wurth/N**: Shipments where `client_id = "2785"` AND `tracking_id` does NOT end with "-01"
- **Others**: All other clients

Ensure the `client_id` field is included in all shipment responses.

### SMS Reply Monitoring
- The app monitors incoming SMS replies from customers
- Replies are batched and sent to the backend every 2 minutes
- The system matches replies to shipments based on phone numbers
- Duplicate prevention is handled on the device side

### Background Workers
The app uses Android WorkManager for:
- Location updates (periodic GPS tracking)
- Data synchronization (syncing offline changes)
- SMS reply monitoring (checking for new SMS)

### Offline Support
The app caches data locally and queues requests when offline. The backend should:
- Accept batch updates when connection is restored
- Handle idempotent operations (deduplicate submissions)
- Return appropriate error codes for validation

### Security Considerations
1. **HTTPS Required**: All API calls use HTTPS
2. **Password Security**: Passwords should be hashed server-side (app sends plain text over HTTPS)
3. **API Key Validation**: Validate auth_key on every request
4. **Rate Limiting**: Implement rate limiting to prevent abuse
5. **Input Validation**: Validate all inputs on the server side

### Testing Checklist
- [ ] Login with valid credentials returns auth_key
- [ ] Login with invalid credentials returns error
- [ ] All authenticated endpoints reject invalid api-key
- [ ] Shipments endpoint returns client_id for Wurth filtering
- [ ] SMS reply handler processes and matches shipments
- [ ] Upload endpoints accept and store files correctly
- [ ] Location updates are saved with timestamp
- [ ] COD summary calculates totals correctly
- [ ] App update check returns correct version info
- [ ] Offline changes sync when connection restored

## Database Schema Suggestions

### Users Table
```sql
CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    email VARCHAR(200),
    phone VARCHAR(20),
    user_type TINYINT, -- 1=Distributor, 2=Driver
    profile_picture VARCHAR(500),
    auth_key VARCHAR(255),
    auth_key_expires DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Shipments Table
```sql
CREATE TABLE shipments (
    shipment_id INT PRIMARY KEY AUTO_INCREMENT,
    tracking_id VARCHAR(100) UNIQUE NOT NULL,
    exchange_tracking_id VARCHAR(100),
    client_id VARCHAR(50),
    receiver_name VARCHAR(200),
    receiver_phone VARCHAR(20),
    receiver_address TEXT,
    city VARCHAR(100),
    cod_amount DECIMAL(10,2),
    status TINYINT,
    notes TEXT,
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    assigned_to INT, -- user_id
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### SMS Replies Table
```sql
CREATE TABLE sms_replies (
    reply_id INT PRIMARY KEY AUTO_INCREMENT,
    shipment_id INT,
    phone VARCHAR(20),
    message TEXT,
    timestamp BIGINT,
    device_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipment_id) REFERENCES shipments(shipment_id)
);
```

## Support
For questions about the app implementation or API requirements, contact the development team.

**App Version**: 1.0
**Last Updated**: 2025-01-17
