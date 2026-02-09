# Return Received - Server Implementation

This document describes the server-side implementation required for the "Return Received" feature in the Trackify EU Android app.

## Overview

The "Return Received" feature allows distributors to scan a courier tracking barcode (not the internal tracking ID) to mark a shipment as having the return received. This is similar to the Status Check feature but automatically marks the shipment with status 10 (Return Received).

## New Endpoint

### `POST /api/return_received.php`

Mark a shipment as "Return Received" by scanning its courier tracking code.

#### Request Headers

| Header | Value | Description |
|--------|-------|-------------|
| `app-name` | `Trackify EU` | Application identifier |
| `api-key` | `<auth_key>` | User's authentication token |

#### Request Parameters (POST)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tracking_id` | string | Yes | Internal tracking ID of the shipment |
| `courier_tracking` | string | Yes | The scanned courier tracking barcode |
| `user_id` | string | Yes | ID of the user performing the action |
| `user` | string | Yes | Username of the user performing the action |

#### Response

**Success Response:**
```json
{
    "success": true,
    "response_txt": "Return received successfully",
    "shipment_id": "12345",
    "tracking_id": "TRK-001",
    "new_status_id": 10,
    "new_status_name": "Return Received"
}
```

**Error Response:**
```json
{
    "success": false,
    "error": true,
    "response_txt": "Error message describing the issue"
}
```

#### Possible Error Messages

- `"Shipment not found"` - The courier tracking code doesn't match any shipment
- `"Shipment is not in a return state"` - The shipment status doesn't allow return received
- `"Failed to update status"` - Database error during update
- `"User not authorized"` - User doesn't have permission for this shipment

## Implementation Details

### 1. Database Changes

Add status ID 10 if not exists:

```sql
-- Check if status 10 exists, if not insert it
INSERT IGNORE INTO statuses (status_id, status_name, bg_color, txt_color)
VALUES (10, 'Return Received', '#e74c3c', '#ffffff');
```

### 2. PHP Implementation

```php
<?php
// /api/return_received.php

require_once 'common/init.php';
require_once 'common/auth.php';

// Verify authentication
$auth = authenticate_request();
if (!$auth['success']) {
    json_error($auth['message']);
}

// Get POST parameters
$tracking_id = $_POST['tracking_id'] ?? null;
$courier_tracking = $_POST['courier_tracking'] ?? null;
$user_id = $_POST['user_id'] ?? null;
$user = $_POST['user'] ?? null;

// Validate required parameters
if (empty($tracking_id) || empty($courier_tracking) || empty($user_id)) {
    json_error('Missing required parameters');
}

try {
    $db = get_db_connection();

    // Find shipment by tracking_id (already looked up by courier_tracking in app)
    $stmt = $db->prepare("
        SELECT s.*, st.status_name
        FROM shipments s
        LEFT JOIN statuses st ON s.status_id = st.status_id
        WHERE s.tracking_id = ?
    ");
    $stmt->execute([$tracking_id]);
    $shipment = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$shipment) {
        json_error('Shipment not found');
    }

    // Verify user has access to this shipment
    if ($shipment['driver_id'] != $user_id && $shipment['distributor_id'] != $user_id) {
        json_error('User not authorized');
    }

    // Optional: Check if shipment is in a valid state for return received
    // Valid previous statuses might be: 7 (Returned to Sender), etc.
    // Uncomment and adjust based on your business logic:
    // $valid_statuses = [7]; // Returned to Sender
    // if (!in_array($shipment['status_id'], $valid_statuses)) {
    //     json_error('Shipment is not in a return state');
    // }

    // Update shipment status to 10 (Return Received)
    $new_status_id = 10;
    $stmt = $db->prepare("
        UPDATE shipments
        SET status_id = ?,
            updated_at = NOW(),
            updated_by = ?
        WHERE tracking_id = ?
    ");
    $result = $stmt->execute([$new_status_id, $user, $tracking_id]);

    if (!$result) {
        json_error('Failed to update status');
    }

    // Log the status change
    $stmt = $db->prepare("
        INSERT INTO shipment_status_log
        (shipment_id, old_status_id, new_status_id, changed_by, changed_at, courier_tracking_scanned)
        VALUES (?, ?, ?, ?, NOW(), ?)
    ");
    $stmt->execute([
        $shipment['shipment_id'],
        $shipment['status_id'],
        $new_status_id,
        $user,
        $courier_tracking
    ]);

    // Return success response
    json_response([
        'success' => true,
        'response_txt' => 'Return received successfully',
        'shipment_id' => $shipment['shipment_id'],
        'tracking_id' => $tracking_id,
        'new_status_id' => $new_status_id,
        'new_status_name' => 'Return Received'
    ]);

} catch (Exception $e) {
    error_log('return_received.php error: ' . $e->getMessage());
    json_error('Something went wrong, please try again');
}

// Helper functions
function json_response($data) {
    header('Content-Type: application/json');
    echo json_encode($data);
    exit;
}

function json_error($message) {
    json_response([
        'success' => false,
        'error' => true,
        'response_txt' => $message
    ]);
}
?>
```

### 3. Status Log Table (Optional)

If you want to track when courier tracking barcodes were scanned:

```sql
-- Add column to existing status log table if it doesn't have it
ALTER TABLE shipment_status_log
ADD COLUMN courier_tracking_scanned VARCHAR(100) NULL
COMMENT 'The courier tracking barcode that was scanned to trigger this status change';
```

## Existing Endpoint Used

The feature also uses the existing `lookup_shipment.php` endpoint:

### `POST /api/lookup_shipment.php`

This endpoint is already implemented for the Status Check feature. It searches for a shipment by courier tracking code.

#### Request Parameters (POST)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `courier_tracking` | string | Yes | The courier tracking barcode to search for |
| `user_id` | string | Yes | ID of the user performing the lookup |

#### Response

Returns the full `ShipmentWithDetail` object if found, or an error if not found.

**Success Response:**
```json
{
    "shipment_id": "12345",
    "tracking_id": "TRK-001",
    "courier_tracking": "COURIER-123",
    "status_id": 7,
    "status_name": "Returned to Sender",
    "sender_name": "Sender Name",
    "sender_phone": "+123456789",
    "sender_address": "Sender Address",
    "receiver_name": "Receiver Name",
    "receiver_phone": "+987654321",
    "receiver_address": "Receiver Address",
    "receiver_city": "City",
    "receiver_cod": "0.00",
    "instructions": "Special instructions if any",
    "bg_color": "#FFFFFF",
    "txt_color": "#000000"
}
```

## Flow Diagram

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  User Scans     │     │   App calls     │     │  Server looks   │
│  Courier        │ ──▶ │  lookup_        │ ──▶ │  up shipment    │
│  Tracking       │     │  shipment.php   │     │  by courier_    │
│  Barcode        │     │                 │     │  tracking       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                         │
                                                         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  App shows      │ ◀── │  Server returns │ ◀── │  Shipment       │
│  shipment       │     │  ShipmentWith   │     │  found          │
│  details        │     │  Detail         │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  User taps      │     │  App calls      │     │  Server updates │
│  "Confirm       │ ──▶ │  return_        │ ──▶ │  status to 10   │
│  Return         │     │  received.php   │     │  (Return        │
│  Received"      │     │                 │     │  Received)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                         │
                                                         ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  App shows      │ ◀── │  Server returns │ ◀── │  Status updated │
│  success        │     │  success        │     │  successfully   │
│  message        │     │  response       │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Android App Changes

The following changes were made to the Android app:

1. **Button Change**: "In Delivery" button replaced with "Return Received" button (red color)
2. **New Controller**: `ReturnReceivedCtrl.java` handles the return received flow
3. **New Layout**: `ctrl_return_received.xml` provides the UI
4. **Scan Logic**: Uses `courier_tracking` lookup (like Status Check) instead of internal tracking ID
5. **New API Method**: `Communicator.MarkReturnReceived()` calls the new endpoint

## Testing Checklist

- [ ] Verify `lookup_shipment.php` works with courier tracking codes
- [ ] Create the `return_received.php` endpoint
- [ ] Add status 10 "Return Received" to the database
- [ ] Test scanning a valid courier tracking barcode
- [ ] Test scanning an invalid/unknown barcode
- [ ] Verify status updates correctly in database
- [ ] Verify shipment lists refresh after status update
- [ ] Test back button behavior from Return Received screen
- [ ] Test error handling when network is unavailable
