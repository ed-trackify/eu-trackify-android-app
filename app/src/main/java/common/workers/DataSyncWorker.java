package common.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import common.App;
import common.Communicator;
import common.ErrorHandler;
import common.AppModel;

import eu.trackify.net.R;

/**
 * WorkManager Worker for syncing shipment data
 * Runs as foreground service for long-running sync operations
 */
public class DataSyncWorker extends Worker {
    
    private static final String TAG = "DataSyncWorker";
    private static final String CHANNEL_ID = "data_sync_channel";
    private static final int NOTIFICATION_ID = 2001;
    
    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            // Check network availability
            if (!ErrorHandler.isNetworkAvailable(getApplicationContext())) {
                Log.w(TAG, "No network connection, will retry later");
                return Result.retry();
            }
            
            // Check if user is logged in
            if (App.CurrentUser == null) {
                Log.d(TAG, "No logged in user, skipping data sync");
                return Result.success();
            }
            
            // Show foreground notification for long-running sync
            setForegroundAsync(createForegroundInfo("Syncing shipments..."));
            
            // Track sync results
            int successCount = 0;
            int failureCount = 0;
            
            // Sync My Shipments
            if (syncMyShipments()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            // Update notification
            setForegroundAsync(createForegroundInfo("Syncing reconciled shipments..."));
            
            // Sync Reconcile Shipments
            if (syncReconcileShipments()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            // Update notification
            setForegroundAsync(createForegroundInfo("Syncing return shipments..."));
            
            // Sync Return Shipments
            if (syncReturnShipments()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            // Prepare output data
            Data outputData = new Data.Builder()
                .putInt("success_count", successCount)
                .putInt("failure_count", failureCount)
                .putLong("sync_timestamp", System.currentTimeMillis())
                .build();
            
            if (failureCount > 0 && successCount == 0) {
                // All syncs failed, retry
                Log.w(TAG, "All sync operations failed, will retry");
                return Result.retry();
            } else if (failureCount > 0) {
                // Partial success
                Log.w(TAG, String.format("Partial sync: %d success, %d failed", successCount, failureCount));
                return Result.success(outputData);
            } else {
                // Complete success
                Log.d(TAG, "All sync operations successful");
                return Result.success(outputData);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in data sync worker", e);
            AppModel.ApplicationError(e, "DataSyncWorker::doWork");
            
            // Retry up to 3 times
            if (getRunAttemptCount() < 3) {
                return Result.retry();
            }
            
            return Result.failure();
        }
    }
    
    /**
     * Create foreground notification for long-running sync
     */
    @NonNull
    private ForegroundInfo createForegroundInfo(String message) {
        createNotificationChannel();
        
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
            .setContentTitle("EU Trackify")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        return new ForegroundInfo(NOTIFICATION_ID, notification.build());
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background data synchronization");
            
            NotificationManager manager = getApplicationContext().getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Sync My Shipments data
     */
    private boolean syncMyShipments() {
        final boolean[] result = {false};
        final Object lock = new Object();
        
        try {
            // Call the sync method
            if (App.Object != null && App.Object.userDistributorMyShipmentsFragment != null) {
                App.Object.runOnUiThread(() -> {
                    try {
                        App.Object.userDistributorMyShipmentsFragment.Load();
                        synchronized (lock) {
                            result[0] = true;
                            lock.notify();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error syncing my shipments", e);
                        synchronized (lock) {
                            result[0] = false;
                            lock.notify();
                        }
                    }
                });
                
                // Wait for completion with timeout
                synchronized (lock) {
                    lock.wait(60000); // 60 second timeout
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync my shipments", e);
        }
        
        return result[0];
    }
    
    /**
     * Sync Reconcile Shipments data
     */
    private boolean syncReconcileShipments() {
        final boolean[] result = {false};
        final Object lock = new Object();
        
        try {
            if (App.Object != null && App.Object.userDistributorReconcileShipmentsFragment != null) {
                App.Object.runOnUiThread(() -> {
                    try {
                        App.Object.userDistributorReconcileShipmentsFragment.Load();
                        synchronized (lock) {
                            result[0] = true;
                            lock.notify();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error syncing reconcile shipments", e);
                        synchronized (lock) {
                            result[0] = false;
                            lock.notify();
                        }
                    }
                });
                
                synchronized (lock) {
                    lock.wait(60000);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync reconcile shipments", e);
        }
        
        return result[0];
    }
    
    /**
     * Sync Return Shipments data
     */
    private boolean syncReturnShipments() {
        final boolean[] result = {false};
        final Object lock = new Object();
        
        try {
            if (App.Object != null && App.Object.userDistributorReturnShipmentsFragment != null) {
                App.Object.runOnUiThread(() -> {
                    try {
                        App.Object.userDistributorReturnShipmentsFragment.Load();
                        synchronized (lock) {
                            result[0] = true;
                            lock.notify();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error syncing return shipments", e);
                        synchronized (lock) {
                            result[0] = false;
                            lock.notify();
                        }
                    }
                });
                
                synchronized (lock) {
                    lock.wait(60000);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync return shipments", e);
        }
        
        return result[0];
    }
}