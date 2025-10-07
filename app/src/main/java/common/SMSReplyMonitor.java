package common;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent SMS Reply Monitor - monitors inbox for customer replies
 * This is completely separate from the SMS sending functionality
 */
public class SMSReplyMonitor {
    private static final String TAG = "SMSReplyMonitor";
    private static final String PREFS_NAME = "sms_reply_monitor_prefs";
    private static final String ENABLED_KEY = "sms_reply_monitoring_enabled";
    private static final String LAST_CHECK_TIME_KEY = "last_check_time";
    private static final String PROCESSED_SMS_IDS_KEY = "processed_sms_ids";

    // Maximum time window to look back for replies (7 days)
    private static final long MAX_REPLY_WINDOW = 7L * 24L * 60L * 60L * 1000L;

    // Check frequency - 5 minutes
    private static final long CHECK_INTERVAL_MS = 5L * 60L * 1000L;

    private final Context context;
    private final SharedPreferences prefs;
    private static SMSReplyMonitor instance;

    private SMSReplyMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SMSReplyMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new SMSReplyMonitor(context);
        }
        return instance;
    }

    /**
     * Check if SMS reply monitoring is enabled
     */
    public boolean isEnabled() {
        return prefs.getBoolean(ENABLED_KEY, false);
    }

    /**
     * Enable or disable SMS reply monitoring
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(ENABLED_KEY, enabled).apply();

        if (enabled) {
            AppModel.ApplicationError(null, "SMS Reply Monitoring: ENABLED");
            // Start monitoring
            startMonitoring();
        } else {
            AppModel.ApplicationError(null, "SMS Reply Monitoring: DISABLED");
            // Stop monitoring
            stopMonitoring();
        }
    }

    /**
     * Start monitoring for SMS replies
     */
    private void startMonitoring() {
        try {
            // Schedule the worker if enabled
            if (isEnabled()) {
                SMSReplyWorker.schedulePeriodicCheck(context);
                AppModel.ApplicationError(null, "SMS Reply Monitoring: Started periodic checks");
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Monitoring: Failed to start");
        }
    }

    /**
     * Stop monitoring for SMS replies
     */
    private void stopMonitoring() {
        try {
            SMSReplyWorker.cancelPeriodicCheck(context);
            AppModel.ApplicationError(null, "SMS Reply Monitoring: Stopped periodic checks");
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Monitoring: Failed to stop");
        }
    }

    /**
     * Check for new SMS replies from customers
     * This is called by the background worker
     */
    public synchronized List<SMSReply> checkForNewReplies() {
        List<SMSReply> newReplies = new ArrayList<>();

        // Check if monitoring is enabled
        if (!isEnabled()) {
            Log.d(TAG, "SMS reply monitoring is disabled");
            return newReplies;
        }

        try {
            // Check if we have permission to read SMS
            if (!hasReadSMSPermission()) {
                Log.d(TAG, "No READ_SMS permission, skipping check");
                return newReplies;
            }

            // Get last check time
            long lastCheckTime = prefs.getLong(LAST_CHECK_TIME_KEY,
                System.currentTimeMillis() - (24L * 60L * 60L * 1000L)); // Default to 24 hours ago
            long currentTime = System.currentTimeMillis();

            // Get already processed SMS IDs
            Set<String> processedIds = getProcessedSmsIds();

            ContentResolver contentResolver = context.getContentResolver();
            Uri smsInboxUri = Uri.parse("content://sms/inbox");

            // Query for incoming SMS messages since last check
            String[] projection = new String[]{
                "_id",
                "address",  // Sender phone number
                "body",     // Message content
                "date"      // Timestamp
            };

            String selection = "date > ?";
            String[] selectionArgs = new String[]{String.valueOf(lastCheckTime)};
            String sortOrder = "date DESC";

            Cursor cursor = contentResolver.query(
                smsInboxUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            );

            if (cursor != null) {
                try {
                    int foundCount = 0;
                    while (cursor.moveToNext() && foundCount < 50) { // Limit to 50 messages per check
                        String smsId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));

                        // Skip if already processed
                        if (processedIds.contains(smsId)) {
                            continue;
                        }

                        String senderPhone = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                        String messageBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                        // Clean the phone number
                        senderPhone = cleanPhoneNumber(senderPhone);

                        // Create SMS reply object
                        SMSReply reply = new SMSReply();
                        reply.smsId = smsId;
                        reply.phoneFrom = senderPhone;
                        reply.message = messageBody;
                        reply.receivedTimestamp = timestamp;

                        newReplies.add(reply);
                        foundCount++;

                        // Mark as processed
                        processedIds.add(smsId);

                        AppModel.ApplicationError(null, "SMS Reply Found: From " + senderPhone);
                    }
                } finally {
                    cursor.close();
                }
            }

            // Update last check time
            prefs.edit().putLong(LAST_CHECK_TIME_KEY, currentTime).apply();

            // Save processed IDs
            saveProcessedSmsIds(processedIds);

            // Send replies to server if any found
            if (!newReplies.isEmpty()) {
                sendRepliesToServer(newReplies);
            }

            AppModel.ApplicationError(null, "SMS Reply Check: Found " + newReplies.size() + " new replies");

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Monitor: Error checking for replies");
        }

        return newReplies;
    }

    /**
     * Send collected SMS replies to the server
     */
    private void sendRepliesToServer(List<SMSReply> replies) {
        try {
            for (SMSReply reply : replies) {
                Communicator.SendSMSReply(reply, new Communicator.IServerResponse() {
                    @Override
                    public void onCompleted(boolean success, String message, Object... objs) {
                        if (success) {
                            AppModel.ApplicationError(null, "SMS Reply sent to server: " + reply.phoneFrom);
                        } else {
                            AppModel.ApplicationError(null, "SMS Reply failed to send: " + reply.phoneFrom);
                        }
                    }
                });
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "Failed to send SMS replies to server");
        }
    }

    /**
     * Check if we have permission to read SMS
     */
    private boolean hasReadSMSPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get the set of already processed SMS IDs
     */
    private Set<String> getProcessedSmsIds() {
        try {
            Set<String> ids = prefs.getStringSet(PROCESSED_SMS_IDS_KEY, null);
            if (ids != null) {
                return new HashSet<>(ids);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading processed SMS IDs", e);
        }
        return new HashSet<>();
    }

    /**
     * Save the set of processed SMS IDs
     */
    private void saveProcessedSmsIds(Set<String> ids) {
        try {
            // Keep only the last 1000 IDs to prevent unbounded growth
            if (ids.size() > 1000) {
                Set<String> trimmedIds = new HashSet<>();
                int count = 0;
                for (String id : ids) {
                    if (count++ >= 500) break;
                    trimmedIds.add(id);
                }
                ids = trimmedIds;
            }

            prefs.edit().putStringSet(PROCESSED_SMS_IDS_KEY, ids).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving processed SMS IDs", e);
        }
    }

    /**
     * Clean and normalize phone number
     */
    private String cleanPhoneNumber(String phone) {
        if (phone == null) return null;

        // Remove all non-digit characters
        phone = phone.replaceAll("[^0-9+]", "");

        // Remove leading zeros
        while (phone.startsWith("0")) {
            phone = phone.substring(1);
        }

        // Add country code if missing (389 for Macedonia)
        if (!phone.startsWith("+")) {
            if (!phone.startsWith("389")) {
                phone = "389" + phone;
            }
            phone = "+" + phone;
        }

        return phone;
    }

    /**
     * Clear all tracking data (for testing)
     */
    public void clearAllData() {
        prefs.edit().clear().apply();
        AppModel.ApplicationError(null, "SMS Reply Monitor: Cleared all data");
    }

    /**
     * Get status information for debugging
     */
    public String getStatus() {
        boolean enabled = isEnabled();
        long lastCheck = prefs.getLong(LAST_CHECK_TIME_KEY, 0);
        int processedCount = getProcessedSmsIds().size();

        return String.format("SMS Reply Monitor Status:\nEnabled: %s\nLast Check: %s\nProcessed Messages: %d",
            enabled ? "YES" : "NO",
            lastCheck > 0 ? new java.util.Date(lastCheck).toString() : "Never",
            processedCount);
    }

    /**
     * SMS Reply data class
     */
    public static class SMSReply {
        public String smsId;
        public String phoneFrom;
        public String phoneTo;
        public String message;
        public long receivedTimestamp;
        public Long shipmentId;
    }
}