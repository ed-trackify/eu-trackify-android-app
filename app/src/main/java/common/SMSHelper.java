package common;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SMSHelper {

    private static final int SMS_PERMISSION_REQUEST_CODE = 101;
    private static final String SENT = "SMS_SENT";
    private static final String DELIVERED = "SMS_DELIVERED";

    public interface SMSCallback {
        void onResult(boolean success);
    }

    public static class SMSData {
        public String shipmentId;
        public String trackingId;
        public String receiverPhone;
        public String receiverName;
        public String receiverAddress;
        public String driverName;
        public String senderName;
        public double receiverCod;
        public String smsText; // Custom SMS text from shipment data
        public int statusId;
        public String countryId; // Country ID for localized messages

        public SMSData() {
            receiverCod = 0;
            statusId = 1; // Default to In Delivery
        }
    }
    
    // Check if SMS was already sent today for this shipment
    private static boolean wasSMSSentToday(Context context, String trackingId) {
        SharedPreferences prefs = context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE);
        String lastSentDate = prefs.getString("sms_" + trackingId, "");
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate = dateFormat.format(new Date());
        
        return todayDate.equals(lastSentDate);
    }
    
    // Mark SMS as sent today for this shipment
    private static void markSMSSentToday(Context context, String trackingId) {
        SharedPreferences prefs = context.getSharedPreferences("sms_tracking", Context.MODE_PRIVATE);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate = dateFormat.format(new Date());

        prefs.edit().putString("sms_" + trackingId, todayDate).apply();
    }

    // Method to send SMS immediately without queuing - for fresh scans
    public static void sendSMSImmediate(final Context context, final SMSData smsData, final SMSCallback callback) {
        // Don't use background thread for immediate send - send directly
        try {
            // Validate tracking ID
            if (smsData.trackingId == null || smsData.trackingId.trim().isEmpty()) {
                AppModel.ApplicationError(null, "SMS IMMEDIATE: Tracking ID is null or empty");
                if (callback != null) callback.onResult(false);
                return;
            }

            AppModel.ApplicationError(null, "SMS IMMEDIATE: Attempting for tracking ID: " + smsData.trackingId);

            // Check if SMS is enabled for this status
            if (!SMSConfig.shouldSendSMS(smsData.statusId)) {
                if (callback != null) callback.onResult(false);
                return;
            }

            // For "In Delivery" status, check if SMS was already sent today
            if (smsData.statusId == 1 && wasSMSSentToday(context, smsData.trackingId)) {
                AppModel.ApplicationError(null, "SMS IMMEDIATE: Already sent today for: " + smsData.trackingId);
                if (callback != null) callback.onResult(true); // Consider it success
                return;
            }

            // Clean phone number
            String cleanedPhone = cleanPhoneNumber(smsData.receiverPhone);
            if (cleanedPhone == null || cleanedPhone.isEmpty()) {
                if (callback != null) callback.onResult(false);
                return;
            }

            // Check SMS permission
            if (!checkSMSPermission(context)) {
                if (callback != null) callback.onResult(false);
                return;
            }

            // Build the SMS message
            String message = buildSMSMessage(smsData);
            if (message == null || message.isEmpty()) {
                if (callback != null) callback.onResult(false);
                return;
            }

            // Try to send immediately
            try {
                SmsManager smsManager = SmsManager.getDefault();

                // Send without pending intents for speed
                if (message.length() > 160) {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    smsManager.sendMultipartTextMessage(cleanedPhone, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(cleanedPhone, null, message, null, null);
                }

                // Mark as sent immediately
                markSMSSentToday(context, smsData.trackingId);

                // Log success
                AppModel.ApplicationError(null, "SMS IMMEDIATE: Sent successfully for: " + smsData.trackingId);
                logSMSToBackend(smsData, message, cleanedPhone);

                if (callback != null) callback.onResult(true);

            } catch (Exception e) {
                // SMS sending failed - likely due to rate limit
                AppModel.ApplicationError(e, "SMS IMMEDIATE: Send failed for " + smsData.trackingId);
                if (callback != null) callback.onResult(false);
            }

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS IMMEDIATE: Exception");
            if (callback != null) callback.onResult(false);
        }
    }

    // Main method to send SMS - runs in background thread (used by queue)
    public static void sendSMS(final Context context, final SMSData smsData) {
        // Run entire SMS process in background thread to avoid blocking UI/scanning
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Validate tracking ID is not null or empty
                    if (smsData.trackingId == null || smsData.trackingId.trim().isEmpty()) {
                        AppModel.ApplicationError(null, "SMS ERROR: Tracking ID is null or empty");
                        return;
                    }
                    
                    // Log the tracking ID being used for SMS
                    AppModel.ApplicationError(null, "SMS: Processing for tracking ID: " + smsData.trackingId);
                    
                    // Check if SMS is enabled for this status
                    if (!SMSConfig.shouldSendSMS(smsData.statusId)) {
                        return;
                    }
                    
                    // For "In Delivery" status (statusId = 1), check if SMS was already sent today
                    if (smsData.statusId == 1 && wasSMSSentToday(context, smsData.trackingId)) {
                        AppModel.ApplicationError(null, "SMS already sent today for: " + smsData.trackingId);
                        return; // Skip if SMS was already sent today for this shipment
                    }
                    
                    // Clean phone number
                    String cleanedPhone = cleanPhoneNumber(smsData.receiverPhone);
                    if (cleanedPhone == null || cleanedPhone.isEmpty()) {
                        return; // Skip if no valid phone number
                    }
                    
                    // Check SMS permission
                    if (!checkSMSPermission(context)) {
                        // Permission request must be on UI thread
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    requestSMSPermission(context);
                                }
                            });
                        }
                        return;
                    }
                    
                    // Build the SMS message
                    String message = buildSMSMessage(smsData);
                    if (message == null || message.isEmpty()) {
                        return;
                    }
                    
                    // Send the SMS (already handles its own threading for callbacks)
                    sendSMSMessage(context, cleanedPhone, message, smsData);
                    
                } catch (Exception e) {
                    AppModel.ApplicationError(e, "SMSHelper::sendSMS");
                    showToast(context, "Failed to send SMS: " + e.getMessage());
                }
            }
        }).start();
    }
    
    // Build the complete SMS message with placeholders replaced
    private static String buildSMSMessage(SMSData smsData) {
        // CRITICAL: Ensure tracking ID is never null or empty
        if (smsData.trackingId == null || smsData.trackingId.trim().isEmpty()) {
            AppModel.ApplicationError(null, "SMS BUILD ERROR: Tracking ID is null or empty!");
            return null; // Don't build message without tracking ID
        }

        // Log which tracking ID is being used in the message
        AppModel.ApplicationError(null, "SMS: Building message for tracking ID: " + smsData.trackingId);

        // Get the base message template
        // For pickup status (4), use country-based template
        String message;
        if (smsData.statusId == 4) {
            // Use country-based pickup message
            message = SMSConfig.getPickupMessageByCountry(smsData.countryId);
            AppModel.ApplicationError(null, "SMS: Using country-based pickup message for country: " + smsData.countryId);
        } else {
            // Note: smsData.smsText is only populated when SMS is sent from detail screen
            // For scan operations, smsText will be null and template will be used
            message = SMSConfig.getMessageTemplate(
                smsData.statusId,
                smsData.smsText != null && !smsData.smsText.isEmpty(),
                smsData.smsText
            );
        }
        
        // Replace placeholders - ensure we use the exact tracking ID passed
        // NEVER use any other source for tracking ID
        message = message.replace("{tracking_id}", smsData.trackingId);
        message = message.replace("{receiver_name}", smsData.receiverName != null ? smsData.receiverName : "");
        message = message.replace("{receiver_address}", smsData.receiverAddress != null ? smsData.receiverAddress : "");
        message = message.replace("{driver_name}", smsData.driverName != null ? smsData.driverName : "");
        message = message.replace("{sender_name}", smsData.senderName != null ? smsData.senderName : "");
        message = message.replace("{company}", App.Object.getString(eu.trackify.net.R.string.app_name));
        
        // Add COD message if applicable (only for In Delivery status)
        if (smsData.statusId == 1) {
            message = SMSConfig.buildCompleteMessage(message, smsData.receiverCod > 0, smsData.receiverCod);
        }
        
        // Replace COD placeholder if it exists in custom messages
        if (smsData.receiverCod > 0) {
            message = message.replace("{receiver_cod}", String.format("%.0f €", smsData.receiverCod));
        }
        
        return message;
    }
    
    // Send the actual SMS - already running in background thread
    private static void sendSMSMessage(final Context context, final String phoneNumber, final String message, final SMSData smsData) {
        try {
            // Log the exact message being sent with COD details
            AppModel.ApplicationError(null, "SMS FINAL: Sending to " + phoneNumber);
            AppModel.ApplicationError(null, "SMS FINAL: Tracking ID: " + smsData.trackingId);
            AppModel.ApplicationError(null, "SMS FINAL: COD Amount: " + smsData.receiverCod);
            AppModel.ApplicationError(null, "SMS FINAL: Message: " + message);

            SmsManager smsManager = SmsManager.getDefault();
            
            // Generate unique IDs for this SMS to avoid conflicts during multi-scan
            int requestCode = (int) System.currentTimeMillis() & 0xfffffff;
            
            // Create pending intents for sent and delivered status with unique request codes
            PendingIntent sentPI = PendingIntent.getBroadcast(context, requestCode, new Intent(SENT + "_" + requestCode), PendingIntent.FLAG_IMMUTABLE);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(context, requestCode + 1, new Intent(DELIVERED + "_" + requestCode), PendingIntent.FLAG_IMMUTABLE);
            
            // Register broadcast receivers for SMS status with unique action names
            registerSMSReceivers(context, smsData, message, phoneNumber, requestCode);
            
            // Check if message needs to be split
            if (message.length() > 160) {
                // Split and send multipart message
                ArrayList<String> parts = smsManager.divideMessage(message);
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();
                
                for (int i = 0; i < parts.size(); i++) {
                    sentIntents.add(sentPI);
                    deliveryIntents.add(deliveredPI);
                }
                
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveryIntents);
            } else {
                // Send single message
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
            }
            
            // Log that SMS was attempted with the specific tracking ID
            AppModel.ApplicationError(null, "SMS QUEUE: Phone=" + phoneNumber + ", TrackingID=" + smsData.trackingId);
            
            // Log the EXACT message being sent to verify correct tracking ID
            AppModel.ApplicationError(null, "SMS FULL MESSAGE: " + message);
            
            // Extract and verify the tracking ID in the message
            if (message != null && !message.contains(smsData.trackingId)) {
                AppModel.ApplicationError(null, "SMS ERROR: Message does NOT contain expected tracking ID: " + smsData.trackingId);
            }
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMSHelper::sendSMSMessage");
            showToast(context, "SMS send failed");
        }
    }
    
    // Register broadcast receivers for SMS status with unique action names
    private static void registerSMSReceivers(final Context context, final SMSData smsData, final String message, final String phoneNumber, final int requestCode) {
        final String sentAction = SENT + "_" + requestCode;
        final String deliveredAction = DELIVERED + "_" + requestCode;
        
        // Register SENT receiver
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                String resultText = "";
                boolean success = false;
                
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        resultText = "SMS sent: " + smsData.trackingId;
                        success = true;
                        // Mark SMS as sent today for this specific tracking ID
                        markSMSSentToday(context, smsData.trackingId);
                        // Log success with tracking ID (don't show toast to avoid interrupting scanning)
                        AppModel.ApplicationError(null, "SMS sent successfully for tracking ID: " + smsData.trackingId);
                        // Log to backend
                        logSMSToBackend(smsData, message, phoneNumber);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        resultText = "SMS failed (Generic): " + smsData.trackingId;
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        resultText = "SMS failed (No service): " + smsData.trackingId;
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        resultText = "SMS failed (Null PDU): " + smsData.trackingId;
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        resultText = "SMS failed (Radio off): " + smsData.trackingId;
                        break;
                }
                
                // Only show toast for failures to avoid interrupting scanning
                if (!success) {
                    showToast(context, resultText);
                }
                
                try {
                    context.unregisterReceiver(this);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }, new IntentFilter(sentAction));
        
        // Register DELIVERED receiver (optional, less intrusive)
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                // Don't show toast for delivery to avoid interruptions
                // Just log the status
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        AppModel.ApplicationError(null, "SMS delivered: " + smsData.trackingId);
                        break;
                    case Activity.RESULT_CANCELED:
                        AppModel.ApplicationError(null, "SMS not delivered: " + smsData.trackingId);
                        break;
                }
                try {
                    context.unregisterReceiver(this);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }, new IntentFilter(deliveredAction));
    }
    
    // Log SMS to backend
    private static void logSMSToBackend(SMSData smsData, String message, String phoneNumber) {
        Communicator.LogSmsToBackend(
            smsData.shipmentId != null ? smsData.shipmentId : smsData.trackingId,
            message,
            phoneNumber,
            smsData.statusId,
            smsData.receiverCod > 0,
            smsData.receiverCod
        );
    }
    
    // Clean phone number - remove spaces, dashes, and special characters
    public static String cleanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }
        
        // Remove all non-numeric characters except +
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");
        
        // Validate minimum length
        if (cleaned.length() < 6) {
            return null;
        }
        
        return cleaned;
    }
    
    // Check if app has SMS permission
    public static boolean checkSMSPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    // Request SMS permission
    public static void requestSMSPermission(Context context) {
        if (context instanceof Activity) {
            ActivityCompat.requestPermissions(
                (Activity) context,
                new String[]{Manifest.permission.SEND_SMS},
                SMS_PERMISSION_REQUEST_CODE
            );
        }
    }
    
    // Show toast message
    private static void showToast(final Context context, final String message) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}