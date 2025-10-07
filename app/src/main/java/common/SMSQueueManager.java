package common;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SMSQueueManager {
    private static SMSQueueManager instance;
    private final Context context;
    private final Queue<SMSHelper.SMSData> smsQueue;
    private final ExecutorService executor;
    private final AtomicBoolean isProcessing;
    private final AtomicInteger sentCount;
    private final AtomicInteger failedCount;
    private long lastResetTime;
    
    private static final String PREFS_NAME = "sms_queue_prefs";
    private static final String QUEUE_KEY = "pending_sms_queue";
    private static final String SENT_COUNT_KEY = "sent_count";
    private static final String LAST_RESET_KEY = "last_reset_time";
    
    private static int MAX_SMS_PER_30_MIN = 30; // Will be updated based on device status
    private static final int BURST_LIMIT = 10;
    private static final long RESET_INTERVAL = 30 * 60 * 1000; // 30 minutes
    private static final long INITIAL_DELAY = 0; // No initial delay - start immediately
    private static final long NORMAL_DELAY = 500; // 500ms between messages (faster)
    private static final long THROTTLE_DELAY = 2000; // 2 seconds when approaching limit
    private static final long RETRY_DELAY = 5000; // 5 seconds for retry
    private static final long NO_DELAY = 0; // No delay when we have full capacity after reset
    
    private SMSQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.smsQueue = new LinkedList<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.isProcessing = new AtomicBoolean(false);
        this.sentCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
        
        // Check if device has increased SMS limit
        checkSMSLimit();
        
        loadQueueFromStorage();
        loadCounters();
        startProcessing();
    }
    
    private void checkSMSLimit() {
        try {
            // Check if SMS limit was increased to 100
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            os.writeBytes("settings get global sms_outgoing_check_max_count\n");
            os.writeBytes("exit\n");
            os.flush();
            
            String line = reader.readLine();
            if (line != null && line.trim().length() > 0) {
                try {
                    int limit = Integer.parseInt(line.trim());
                    if (limit > 30) {
                        MAX_SMS_PER_30_MIN = limit;
                        AppModel.ApplicationError(null, "SMS Queue: Device has increased limit of " + limit + " messages");
                    }
                } catch (NumberFormatException e) {
                    // Keep default 30
                }
            }
            
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            // Device not rooted or can't check - use default 30
            AppModel.ApplicationError(null, "SMS Queue: Using default limit of 30 messages (device not rooted or check failed)");
        }
    }
    
    public static synchronized SMSQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new SMSQueueManager(context);
        }
        return instance;
    }
    
    public void addToQueue(SMSHelper.SMSData smsData) {
        if (smsData == null || smsData.trackingId == null || smsData.trackingId.trim().isEmpty()) {
            AppModel.ApplicationError(null, "SMS Queue: Invalid SMS data - tracking ID is null or empty");
            return;
        }

        // First check if we have capacity for immediate send
        checkAndResetCounters();
        if (sentCount.get() < MAX_SMS_PER_30_MIN) {
            AppModel.ApplicationError(null, "SMS Queue: Have capacity (" + sentCount.get() + "/" + MAX_SMS_PER_30_MIN + "), trying immediate send for " + smsData.trackingId);

            // Try immediate send since we have capacity
            final boolean[] immediateSuccess = {false};
            final Object lock = new Object();

            SMSHelper.sendSMSImmediate(context, smsData, new SMSHelper.SMSCallback() {
                @Override
                public void onResult(boolean success) {
                    synchronized (lock) {
                        immediateSuccess[0] = success;
                        lock.notify();
                    }
                }
            });

            // Wait for immediate result
            synchronized (lock) {
                try {
                    lock.wait(2000); // 2 second timeout
                } catch (InterruptedException e) {
                    // Timeout
                }
            }

            if (immediateSuccess[0]) {
                sentCount.incrementAndGet();
                saveCounters();
                AppModel.ApplicationError(null, "SMS Queue: Immediate send successful for " + smsData.trackingId + " (bypassed queue)");
                return; // Don't add to queue if immediate send worked
            }

            AppModel.ApplicationError(null, "SMS Queue: Immediate send failed for " + smsData.trackingId + ", adding to queue");
        }

        // Add to queue if immediate send failed or no capacity
        synchronized (smsQueue) {
            boolean alreadyQueued = false;
            for (SMSHelper.SMSData existing : smsQueue) {
                if (existing.trackingId.equalsIgnoreCase(smsData.trackingId)) {
                    alreadyQueued = true;
                    break;
                }
            }

            if (!alreadyQueued) {
                smsQueue.offer(smsData);
                AppModel.ApplicationError(null, "SMS Queue: Added " + smsData.trackingId + " to queue. Queue size: " + smsQueue.size());
                saveQueueToStorage();

                if (!isProcessing.get()) {
                    startProcessing();
                }
            } else {
                AppModel.ApplicationError(null, "SMS Queue: " + smsData.trackingId + " already in queue, skipping duplicate");
            }
        }
    }
    
    private void startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            AppModel.ApplicationError(null, "SMS Queue: Starting queue processor");
            executor.execute(new QueueProcessor());
        }
    }
    
    private class QueueProcessor implements Runnable {
        @Override
        public void run() {
            AppModel.ApplicationError(null, "SMS Queue: Processor started with " + smsQueue.size() + " messages");

            try {
                if (INITIAL_DELAY > 0) {
                    Thread.sleep(INITIAL_DELAY);
                }

                int cleanupCounter = 0;
                boolean windowJustReset = false;

                while (!smsQueue.isEmpty()) {
                    windowJustReset = checkAndResetCounters();
                    
                    // Periodic cleanup every 10 processed messages
                    if (++cleanupCounter % 10 == 0) {
                        cleanupDeliveredShipments();
                    }
                    
                    if (sentCount.get() >= MAX_SMS_PER_30_MIN) {
                        long waitTime = RESET_INTERVAL - (System.currentTimeMillis() - lastResetTime);
                        if (waitTime > 0) {
                            AppModel.ApplicationError(null, "SMS Queue: Rate limit reached. Waiting " + (waitTime/1000) + " seconds");
                            Thread.sleep(Math.min(waitTime, 60000)); // Wait max 1 minute then recheck
                            continue;
                        }
                    }
                    
                    SMSHelper.SMSData smsData = null;
                    synchronized (smsQueue) {
                        smsData = smsQueue.poll();
                        if (smsData != null) {
                            saveQueueToStorage();
                        }
                    }
                    
                    if (smsData != null) {
                        AppModel.ApplicationError(null, "SMS Queue: Processing " + smsData.trackingId + 
                            " (" + (sentCount.get() + 1) + "/" + MAX_SMS_PER_30_MIN + " in window)");

                        boolean success = false;

                        // After window reset or when we have lots of capacity, try immediate send first
                        if (windowJustReset || sentCount.get() == 0) {
                            AppModel.ApplicationError(null, "SMS Queue: Attempting immediate send (window reset/fresh start) for " + smsData.trackingId);
                            success = sendSMSImmediate(smsData);

                            if (success) {
                                AppModel.ApplicationError(null, "SMS Queue: Immediate send successful for " + smsData.trackingId);
                            } else {
                                AppModel.ApplicationError(null, "SMS Queue: Immediate send failed, using normal queue send for " + smsData.trackingId);
                            }
                        }

                        // If immediate send wasn't attempted or failed, use normal queue send
                        if (!success) {
                            success = sendSMSWithResult(smsData);
                        }

                        if (success) {
                            sentCount.incrementAndGet();
                            saveCounters();
                            AppModel.ApplicationError(null, "SMS Queue: Successfully sent " + smsData.trackingId);
                            windowJustReset = false; // Clear the flag after first successful send
                        } else {
                            failedCount.incrementAndGet();
                            AppModel.ApplicationError(null, "SMS Queue: Failed to send " + smsData.trackingId + ", will retry");

                            synchronized (smsQueue) {
                                smsQueue.offer(smsData);
                                saveQueueToStorage();
                            }
                            Thread.sleep(RETRY_DELAY);
                            continue; // Skip delay calculation on failure
                        }

                        // Calculate delay based on current capacity
                        long delay = calculateDelay(windowJustReset);
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                }
                
                AppModel.ApplicationError(null, "SMS Queue: All messages processed. Sent: " + sentCount.get() + ", Failed: " + failedCount.get());
                
            } catch (InterruptedException e) {
                AppModel.ApplicationError(e, "SMS Queue: Processor interrupted");
            } finally {
                isProcessing.set(false);
                AppModel.ApplicationError(null, "SMS Queue: Processor stopped");
            }
        }
        
        private long calculateDelay(boolean windowJustReset) {
            int sent = sentCount.get();

            // No delay right after window reset when we have full capacity
            if (windowJustReset || sent == 0) {
                return NO_DELAY;
            }

            // Use minimal delay for the first burst of messages
            if (sent < BURST_LIMIT) {
                return NORMAL_DELAY / 2; // Even faster for initial burst
            } else if (sent < MAX_SMS_PER_30_MIN - 10) {
                return NORMAL_DELAY;
            } else if (sent < MAX_SMS_PER_30_MIN - 5) {
                return NORMAL_DELAY * 2;
            } else {
                return THROTTLE_DELAY;
            }
        }

        private boolean sendSMSImmediate(SMSHelper.SMSData smsData) {
            try {
                // Check if shipment is still "In Delivery" before sending SMS
                if (!shouldSendSMS(smsData)) {
                    AppModel.ApplicationError(null, "SMS Queue: Skipping immediate " + smsData.trackingId + " - already delivered or status changed");
                    return true; // Return true to remove from queue without retry
                }

                // Try to send immediately using SMSHelper's immediate method
                final boolean[] result = {false};
                final Object lock = new Object();

                SMSHelper.sendSMSImmediate(context, smsData, new SMSHelper.SMSCallback() {
                    @Override
                    public void onResult(boolean success) {
                        synchronized (lock) {
                            result[0] = success;
                            lock.notify();
                        }
                    }
                });

                // Wait for callback with timeout
                synchronized (lock) {
                    try {
                        lock.wait(5000); // 5 second timeout
                    } catch (InterruptedException e) {
                        // Timeout or interrupted
                    }
                }

                return result[0];
            } catch (Exception e) {
                AppModel.ApplicationError(e, "SMS Queue: Error in immediate send for " + smsData.trackingId);
                return false;
            }
        }
        
        private boolean sendSMSWithResult(SMSHelper.SMSData smsData) {
            try {
                // Check if shipment is still "In Delivery" before sending SMS
                if (!shouldSendSMS(smsData)) {
                    AppModel.ApplicationError(null, "SMS Queue: Skipping " + smsData.trackingId + " - already delivered or status changed");
                    return true; // Return true to remove from queue without retry
                }
                
                SMSHelper.sendSMS(context, smsData);
                return true;
            } catch (Exception e) {
                AppModel.ApplicationError(e, "SMS Queue: Error sending SMS for " + smsData.trackingId);
                return false;
            }
        }
        
        private boolean shouldSendSMS(SMSHelper.SMSData smsData) {
            try {
                // Check current shipment status from cache or server
                String cachedData = AppModel.Object.GetVariable(AppModel.MY_SHIPMENTS_CACHE_KEY);
                if (!AppModel.IsNullOrEmpty(cachedData)) {
                    ShipmentResponse cached = new Gson().fromJson(cachedData, ShipmentResponse.class);
                    if (cached != null && cached.shipments != null) {
                        for (ShipmentWithDetail s : cached.shipments) {
                            if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(smsData.trackingId)) {
                                // Check if status is still "In Delivery" (status_id = 1)
                                // Status 2 = Delivered, Status 3 = Rejected, etc.
                                if (s.status_id != 1) {
                                    AppModel.ApplicationError(null, "SMS Queue: Shipment " + smsData.trackingId + 
                                        " has status " + s.status_id + " (not in delivery), skipping SMS");
                                    return false;
                                }
                                break;
                            }
                        }
                    }
                }
                
                // Default to sending if we can't verify status (to avoid blocking)
                return true;
                
            } catch (Exception e) {
                AppModel.ApplicationError(e, "SMS Queue: Error checking shipment status for " + smsData.trackingId);
                return true; // Default to sending on error
            }
        }
    }
    
    private boolean checkAndResetCounters() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime > RESET_INTERVAL) {
            sentCount.set(0);
            failedCount.set(0);
            lastResetTime = currentTime;
            saveCounters();
            AppModel.ApplicationError(null, "SMS Queue: Counters reset. New window started. Switching to immediate send mode.");
            return true; // Window was reset
        }
        return false; // Window not reset
    }
    
    private void saveQueueToStorage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            List<SMSHelper.SMSData> queueList = new ArrayList<>(smsQueue);
            String json = new Gson().toJson(queueList);
            prefs.edit().putString(QUEUE_KEY, json).apply();
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Queue: Error saving queue to storage");
        }
    }
    
    private void loadQueueFromStorage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(QUEUE_KEY, "");
            if (!json.isEmpty()) {
                Type listType = new TypeToken<ArrayList<SMSHelper.SMSData>>(){}.getType();
                List<SMSHelper.SMSData> queueList = new Gson().fromJson(json, listType);
                if (queueList != null) {
                    smsQueue.addAll(queueList);
                    AppModel.ApplicationError(null, "SMS Queue: Loaded " + smsQueue.size() + " messages from storage");
                }
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Queue: Error loading queue from storage");
        }
    }
    
    private void saveCounters() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putInt(SENT_COUNT_KEY, sentCount.get())
                .putLong(LAST_RESET_KEY, lastResetTime)
                .apply();
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Queue: Error saving counters");
        }
    }
    
    private void loadCounters() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sentCount.set(prefs.getInt(SENT_COUNT_KEY, 0));
            lastResetTime = prefs.getLong(LAST_RESET_KEY, System.currentTimeMillis());
            checkAndResetCounters();
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Queue: Error loading counters");
        }
    }
    
    public int getQueueSize() {
        synchronized (smsQueue) {
            return smsQueue.size();
        }
    }
    
    public int getSentCount() {
        return sentCount.get();
    }
    
    public int getRemainingCapacity() {
        return Math.max(0, MAX_SMS_PER_30_MIN - sentCount.get());
    }
    
    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    // Debug method to check queue persistence
    // Remove a specific shipment from queue (e.g., when delivered)
    public void removeFromQueue(String trackingId) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return;
        }
        
        synchronized (smsQueue) {
            boolean removed = false;
            SMSHelper.SMSData toRemove = null;
            
            for (SMSHelper.SMSData sms : smsQueue) {
                if (sms.trackingId != null && sms.trackingId.equalsIgnoreCase(trackingId)) {
                    toRemove = sms;
                    break;
                }
            }
            
            if (toRemove != null) {
                smsQueue.remove(toRemove);
                removed = true;
                saveQueueToStorage();
                AppModel.ApplicationError(null, "SMS Queue: Removed " + trackingId + " from queue (delivered/cancelled)");
            }
            
            if (!removed) {
                AppModel.ApplicationError(null, "SMS Queue: " + trackingId + " not found in queue");
            }
        }
    }
    
    // Clean up queue by removing delivered/cancelled shipments
    public void cleanupDeliveredShipments() {
        try {
            String cachedData = AppModel.Object.GetVariable(AppModel.MY_SHIPMENTS_CACHE_KEY);
            if (AppModel.IsNullOrEmpty(cachedData)) {
                return;
            }
            
            ShipmentResponse cached = new Gson().fromJson(cachedData, ShipmentResponse.class);
            if (cached == null || cached.shipments == null) {
                return;
            }
            
            synchronized (smsQueue) {
                List<SMSHelper.SMSData> toRemove = new ArrayList<>();
                
                for (SMSHelper.SMSData smsData : smsQueue) {
                    for (ShipmentWithDetail shipment : cached.shipments) {
                        if (shipment.tracking_id != null && 
                            shipment.tracking_id.equalsIgnoreCase(smsData.trackingId)) {
                            // Remove if not "In Delivery" (status_id != 1)
                            if (shipment.status_id != 1) {
                                toRemove.add(smsData);
                                AppModel.ApplicationError(null, "SMS Queue Cleanup: Removing " + 
                                    smsData.trackingId + " (status: " + shipment.status_id + ")");
                            }
                            break;
                        }
                    }
                }
                
                if (!toRemove.isEmpty()) {
                    smsQueue.removeAll(toRemove);
                    saveQueueToStorage();
                    AppModel.ApplicationError(null, "SMS Queue Cleanup: Removed " + 
                        toRemove.size() + " delivered/cancelled shipments");
                }
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Queue: Error during cleanup");
        }
    }
    
    public String getQueueStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== SMS Queue Status ===\n");
        status.append("Queue Size: ").append(getQueueSize()).append("\n");
        status.append("Sent Count: ").append(getSentCount()).append("/").append(MAX_SMS_PER_30_MIN).append("\n");
        status.append("Remaining Capacity: ").append(getRemainingCapacity()).append("\n");
        
        long timeUntilReset = RESET_INTERVAL - (System.currentTimeMillis() - lastResetTime);
        if (timeUntilReset > 0) {
            status.append("Time until reset: ").append(timeUntilReset / 60000).append(" minutes\n");
        } else {
            status.append("Ready to reset counters\n");
        }
        
        status.append("Processing: ").append(isProcessing.get() ? "Yes" : "No").append("\n");
        
        // Show first few items in queue
        synchronized (smsQueue) {
            if (!smsQueue.isEmpty()) {
                status.append("\nPending SMS:\n");
                int count = 0;
                for (SMSHelper.SMSData sms : smsQueue) {
                    status.append("  - ").append(sms.trackingId).append(" -> ").append(sms.receiverPhone).append("\n");
                    if (++count >= 5) {
                        status.append("  ... and ").append(smsQueue.size() - 5).append(" more\n");
                        break;
                    }
                }
            }
        }
        
        return status.toString();
    }
}