# Status Check Feature - Backend Implementation Guide

## Overview

The Status Check feature allows drivers to scan a shipment barcode (using the `courier_tracking` field) and view shipment details **without automatically changing the status**. After reviewing the details, they can manually update the status to either:

- **Picked Up** (status_id = 4)
- **Returned to Sender** (status_id = 7)

## New Backend Endpoint Required

### `POST /api/lookup_shipment.php`

This endpoint searches for a shipment by the scanned barcode and returns full shipment details.

#### Request

**URL:** `https://eu.trackify.net/api/lookup_shipment.php`

**Method:** `POST`

**Headers:**
```
Content-Type: application/x-www-form-urlencoded
app-name: Trackify EU
api-key: <user's auth_key>
```

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `courier_tracking` | string | Yes | The scanned barcode value. This should be searched across multiple fields to find the matching shipment. |
| `user_id` | integer | Yes | The logged-in user's ID |

#### Search Logic

The `courier_tracking` parameter should be searched against multiple fields to find the shipment. The search should check (in order of priority):

1. `tracking_id` - Exact match (case-insensitive)
2. `courier_tracking` - Exact match (case-insensitive)
3. `exchange_tracking_id` - Exact match (case-insensitive)
4. `barcode` - Exact match (case-insensitive)
5. `shipment_id` - Exact match

**Note:** The endpoint should return the first match found. If no match is found, return an error response.

#### Success Response

**HTTP Status:** 200 OK

**Content-Type:** `application/json`

```json
{
    "shipment_id": "12345",
    "status_id": 1,
    "status_name": "In Delivery",
    "description": "Package Description",
    "description_title": "Electronics",
    "tracking_id": "TRK-001234",
    "exchange_tracking_id": "EX-001234",
    "courier_tracking": "SCAN-12345",
    "client_id": "2785",
    "receiver_name": "John Doe",
    "receiver_phone": "+389 70 123 456",
    "receiver_address": "123 Main Street",
    "receiver_city": "Skopje",
    "receiver_country_id": "1",
    "receiver_cod": "150.00",
    "sender_name": "ABC Company",
    "sender_phone": "+389 2 123 456",
    "sender_address": "Business Park 5",
    "instructions": "Call before delivery",
    "lat": 41.9981,
    "lon": 21.4254,
    "sms_text": "Your package is ready for delivery",
    "is_urgent": 0,
    "pin_verification": 0,
    "bg_color": "#42d4f4",
    "txt_color": "#000000",
    "notes": [],
    "images": []
}
```

#### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `shipment_id` | string | Unique shipment identifier |
| `status_id` | integer | Current status ID (see Status IDs below) |
| `status_name` | string | Human-readable status name |
| `tracking_id` | string | Primary tracking ID |
| `exchange_tracking_id` | string | Exchange/alternate tracking ID (optional) |
| `courier_tracking` | string | Courier-specific tracking code |
| `client_id` | string | Client identifier |
| `receiver_name` | string | Receiver's full name |
| `receiver_phone` | string | Receiver's phone number |
| `receiver_address` | string | Delivery address |
| `receiver_city` | string | Delivery city |
| `receiver_country_id` | string | Country ID for SMS localization |
| `receiver_cod` | string | Cash on delivery amount (with decimals) |
| `sender_name` | string | Sender's name/company |
| `sender_phone` | string | Sender's phone number |
| `sender_address` | string | Sender's address |
| `instructions` | string | Special delivery instructions (optional) |
| `lat` | double | Delivery latitude (optional) |
| `lon` | double | Delivery longitude (optional) |
| `sms_text` | string | Pre-configured SMS text (optional) |
| `is_urgent` | integer | Urgency flag (0 or 1) |
| `pin_verification` | integer | Whether PIN verification is required (0 or 1) |
| `bg_color` | string | Background color for status badge (hex) |
| `txt_color` | string | Text color for status badge (hex) |
| `notes` | array | Array of note objects (optional) |
| `images` | array | Array of image objects (optional) |

#### Error Responses

**Shipment Not Found:**
```json
{
    "success": false,
    "error": true,
    "response_txt": "Shipment not found"
}
```

**Invalid/Missing Parameters:**
```json
{
    "success": false,
    "error": true,
    "response_txt": "Missing required parameter: courier_tracking"
}
```

**Unauthorized:**
```json
{
    "success": false,
    "error": true,
    "response_txt": "Unauthorized access"
}
```

---

## Status Update (Existing Endpoint)

After viewing the shipment details, the user can update the status using the existing `update_status.php` endpoint.

### Status IDs for Status Check Feature

| Status ID | Status Name | Description |
|-----------|-------------|-------------|
| 4 | Picked Up | Driver has picked up the shipment from sender |
| 7 | Returned to Sender | Shipment is being returned to the sender |

### Request Example

```
GET /api/update_status.php?key=TRK-001234&user=driver01&status=prezemena&status_id=4
```

---

## Database Considerations

### Required Fields in Shipments Table

Ensure the shipments table has the following fields available for searching:

```sql
-- Searchable fields for courier_tracking lookup
tracking_id VARCHAR(100) -- Primary tracking ID
courier_tracking VARCHAR(100) -- Courier-specific barcode/tracking
exchange_tracking_id VARCHAR(100) -- Exchange tracking ID
barcode VARCHAR(100) -- Additional barcode field
```

### Recommended Index

Create an index to optimize the lookup query:

```sql
CREATE INDEX idx_shipment_lookup ON shipments (
    tracking_id,
    courier_tracking,
    exchange_tracking_id
);
```

---

## PHP Implementation Example

```php
<?php
// api/lookup_shipment.php

header('Content-Type: application/json');

// Get headers
$headers = getallheaders();
$api_key = isset($headers['api-key']) ? $headers['api-key'] : '';

// Validate authentication
if (empty($api_key)) {
    echo json_encode([
        'success' => false,
        'error' => true,
        'response_txt' => 'Unauthorized access'
    ]);
    exit;
}

// Get parameters
$courier_tracking = isset($_POST['courier_tracking']) ? trim($_POST['courier_tracking']) : '';
$user_id = isset($_POST['user_id']) ? intval($_POST['user_id']) : 0;

// Validate required parameters
if (empty($courier_tracking)) {
    echo json_encode([
        'success' => false,
        'error' => true,
        'response_txt' => 'Missing required parameter: courier_tracking'
    ]);
    exit;
}

// Database connection (adjust as needed)
require_once('db_connection.php');

// Search for shipment across multiple fields
$search_value = mysqli_real_escape_string($conn, $courier_tracking);

$sql = "SELECT
            s.shipment_id,
            s.status_id,
            st.status_name,
            s.description,
            s.description_title,
            s.tracking_id,
            s.exchange_tracking_id,
            s.courier_tracking,
            s.client_id,
            s.receiver_name,
            s.receiver_phone,
            s.receiver_address,
            s.receiver_city,
            s.receiver_country_id,
            s.receiver_cod,
            s.sender_name,
            s.sender_phone,
            s.sender_address,
            s.instructions,
            s.lat,
            s.lon,
            s.sms_text,
            s.is_urgent,
            s.pin_verification,
            st.bg_color,
            st.txt_color
        FROM shipments s
        LEFT JOIN statuses st ON s.status_id = st.status_id
        WHERE
            LOWER(s.tracking_id) = LOWER('$search_value')
            OR LOWER(s.courier_tracking) = LOWER('$search_value')
            OR LOWER(s.exchange_tracking_id) = LOWER('$search_value')
            OR LOWER(s.barcode) = LOWER('$search_value')
            OR s.shipment_id = '$search_value'
        LIMIT 1";

$result = mysqli_query($conn, $sql);

if ($result && mysqli_num_rows($result) > 0) {
    $shipment = mysqli_fetch_assoc($result);

    // Convert numeric fields
    $shipment['status_id'] = intval($shipment['status_id']);
    $shipment['is_urgent'] = intval($shipment['is_urgent']);
    $shipment['pin_verification'] = intval($shipment['pin_verification']);
    $shipment['lat'] = $shipment['lat'] ? floatval($shipment['lat']) : null;
    $shipment['lon'] = $shipment['lon'] ? floatval($shipment['lon']) : null;

    // Default colors if not set
    if (empty($shipment['bg_color'])) $shipment['bg_color'] = '#FFFFFF';
    if (empty($shipment['txt_color'])) $shipment['txt_color'] = '#000000';

    // Add empty notes and images arrays
    $shipment['notes'] = [];
    $shipment['images'] = [];

    echo json_encode($shipment);
} else {
    echo json_encode([
        'success' => false,
        'error' => true,
        'response_txt' => 'Shipment not found'
    ]);
}

mysqli_close($conn);
?>
```

---

## Testing the Feature

### Test Cases

1. **Valid Barcode Scan**
   - Scan a valid tracking ID
   - Expected: Shipment details displayed correctly

2. **Invalid Barcode**
   - Scan a non-existent barcode
   - Expected: "Shipment not found" error displayed

3. **Status Update - Picked Up**
   - View shipment details and tap "Picked Up"
   - Expected: Status changes to 4, list refreshes

4. **Status Update - Returned to Sender**
   - View shipment details and tap "Returned to Sender"
   - Expected: Status changes to 7, list refreshes

5. **Network Offline**
   - Attempt lookup without network
   - Expected: "Internet connection not available" message

### Sample Test Barcodes

Create test shipments with these tracking IDs for testing:
- `TEST-STATUS-CHECK-001`
- `TEST-STATUS-CHECK-002`
- `TEST-STATUS-CHECK-003`

---

## Security Considerations

1. **Authentication:** Always validate the `api-key` header against the user session
2. **Input Validation:** Sanitize the `courier_tracking` parameter to prevent SQL injection
3. **Authorization:** Verify the user has permission to view the requested shipment (e.g., it's assigned to their route)
4. **Rate Limiting:** Consider implementing rate limiting to prevent abuse

---

## Questions for Backend Developer

1. Does the `courier_tracking` field already exist in the database, or does it need to be added?
2. Are there any additional fields that should be searchable for barcode lookups?
3. Should the endpoint restrict results to only shipments assigned to the requesting driver?
4. What is the preferred authentication method for this endpoint?

---

## Contact

For questions about the Android app implementation, refer to the codebase:
- `StatusCheckCtrl.java` - Main controller for the feature
- `Communicator.java` - API communication (`LookupShipmentByCourierTracking` method)
- `ctrl_status_check.xml` - UI layout
