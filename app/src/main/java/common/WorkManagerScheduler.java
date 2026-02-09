package common;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import common.workers.DataSyncWorker;
import common.workers.LocationUpdateWorker;
import common.workers.UploadWorker;

/**
 * Manages all WorkManager scheduling for the EU Trackify app
 * Replaces inefficient background threads with proper Android background processing
 */
public class WorkManagerScheduler {
    
    private static final String TAG = "WorkManagerScheduler";
    
    // Work tags for easy management
    public static final String TAG_LOCATION_UPDATE = "location_update";
    public static final String TAG_DATA_SYNC = "data_sync";
    public static final String TAG_UPLOAD = "file_upload";
    
    // Unique work names for singleton workers
    private static final String WORK_LOCATION_PERIODIC = "location_periodic_work";
    private static final String WORK_DATA_SYNC_PERIODIC = "data_sync_periodic_work";
    
    private static WorkManagerScheduler instance;
    private Context context;
    private WorkManager workManager;
    
    private WorkManagerScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(this.context);
        
        // Initialize WorkManager with custom configuration
        initializeWorkManager();
    }
    
    public static WorkManagerScheduler getInstance(Context context) {
        if (instance == null) {
            synchronized (WorkManagerScheduler.class) {
                if (instance == null) {
                    instance = new WorkManagerScheduler(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize WorkManager with custom configuration
     */
    private void initializeWorkManager() {
        Configuration config = new Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build();
        
        try {
            WorkManager.initialize(context, config);
            Log.d(TAG, "WorkManager initialized successfully");
        } catch (IllegalStateException e) {
            // WorkManager is already initialized
            Log.d(TAG, "WorkManager already initialized");
        }
    }
    
    /**
     * Schedule periodic location updates (replaces GPS thread)
     */
    public void scheduleLocationUpdates(int intervalMinutes) {
        // Create constraints
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Keep updating even on low battery
            .build();
        
        // Create periodic work request (minimum 15 minutes)
        int actualInterval = Math.max(15, intervalMinutes);
        PeriodicWorkRequest locationWork = new PeriodicWorkRequest.Builder(
                LocationUpdateWorker.class,
                actualInterval, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES) // Flex period for battery optimization
            .setConstraints(constraints)
            .addTag(TAG_LOCATION_UPDATE)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES)
            .build();
        
        // Enqueue unique periodic work
        workManager.enqueueUniquePeriodicWork(
            WORK_LOCATION_PERIODIC,
            ExistingPeriodicWorkPolicy.REPLACE,
            locationWork
        );
        
        Log.d(TAG, "Location updates scheduled every " + actualInterval + " minutes");
    }
    
    /**
     * Schedule periodic data sync (replaces sync thread)
     */
    public void scheduleDataSync(int intervalMinutes) {
        // Create constraints - only sync when connected
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build();
        
        // Create periodic work request
        int actualInterval = Math.max(15, intervalMinutes);
        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
                DataSyncWorker.class,
                actualInterval, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(TAG_DATA_SYNC)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                2, TimeUnit.MINUTES)
            .build();
        
        // Enqueue unique periodic work
        workManager.enqueueUniquePeriodicWork(
            WORK_DATA_SYNC_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already running
            syncWork
        );
        
        Log.d(TAG, "Data sync scheduled every " + actualInterval + " minutes");
    }
    
    /**
     * Schedule immediate one-time location update
     */
    public void triggerLocationUpdate() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        OneTimeWorkRequest locationWork = new OneTimeWorkRequest.Builder(LocationUpdateWorker.class)
            .setConstraints(constraints)
            .addTag(TAG_LOCATION_UPDATE)
            .build();
        
        workManager.enqueue(locationWork);
        Log.d(TAG, "Immediate location update triggered");
    }
    
    /**
     * Schedule immediate one-time data sync
     */
    public void triggerDataSync() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(DataSyncWorker.class)
            .setConstraints(constraints)
            .addTag(TAG_DATA_SYNC)
            .build();
        
        workManager.enqueue(syncWork);
        Log.d(TAG, "Immediate data sync triggered");
    }
    
    /**
     * Schedule reliable file upload
     */
    public void scheduleUpload(String filePath, String uploadType, String trackingId, String comments) {
        // Validate file exists
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "Cannot schedule upload, file not found: " + filePath);
            return;
        }
        
        // Create input data
        Data inputData = new Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, filePath)
            .putString(UploadWorker.KEY_UPLOAD_TYPE, uploadType)
            .putString(UploadWorker.KEY_TRACKING_ID, trackingId)
            .putString(UploadWorker.KEY_COMMENTS, comments)
            .putBoolean("delete_after_upload", false)
            .build();
        
        // Create constraints - only upload on unmetered network
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only for large files
            .setRequiresBatteryNotLow(true)
            .build();
        
        // For small files or urgent uploads, use connected network
        if (file.length() < 1024 * 1024 || uploadType.equals(UploadWorker.TYPE_SIGNATURE)) {
            constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        }
        
        // Create work request with exponential backoff
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(UploadWorker.class)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(TAG_UPLOAD)
            .addTag("upload_" + trackingId)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS)
            .build();
        
        // Enqueue with unique name to prevent duplicates
        String uniqueWorkName = "upload_" + file.getName() + "_" + System.currentTimeMillis();
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            uploadWork
        );
        
        Log.d(TAG, "Upload scheduled for: " + filePath);
    }
    
    /**
     * Cancel all periodic work
     */
    public void cancelAllPeriodicWork() {
        workManager.cancelUniqueWork(WORK_LOCATION_PERIODIC);
        workManager.cancelUniqueWork(WORK_DATA_SYNC_PERIODIC);
        Log.d(TAG, "All periodic work cancelled");
    }
    
    /**
     * Cancel work by tag
     */
    public void cancelWorkByTag(String tag) {
        workManager.cancelAllWorkByTag(tag);
        Log.d(TAG, "Cancelled all work with tag: " + tag);
    }
    
    /**
     * Get work status
     */
    public void getWorkStatus(String tag, WorkStatusCallback callback) {
        WorkQuery query = WorkQuery.Builder.fromTags(java.util.Arrays.asList(tag)).build();
        
        workManager.getWorkInfos(query).addListener(() -> {
            try {
                List<WorkInfo> workInfos = workManager.getWorkInfos(query).get();
                if (callback != null) {
                    callback.onWorkStatus(workInfos);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting work status", e);
            }
        }, command -> command.run());
    }
    
    /**
     * Check if any upload is pending for a tracking ID
     */
    public void isPendingUpload(String trackingId, UploadStatusCallback callback) {
        WorkQuery query = WorkQuery.Builder.fromTags(java.util.Arrays.asList("upload_" + trackingId)).build();
        
        workManager.getWorkInfos(query).addListener(() -> {
            try {
                List<WorkInfo> workInfos = workManager.getWorkInfos(query).get();
                boolean hasPending = false;
                for (WorkInfo info : workInfos) {
                    if (info.getState() == WorkInfo.State.ENQUEUED || 
                        info.getState() == WorkInfo.State.RUNNING) {
                        hasPending = true;
                        break;
                    }
                }
                if (callback != null) {
                    callback.onUploadStatus(hasPending);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking upload status", e);
            }
        }, command -> command.run());
    }
    
    /**
     * Start all background work (called on app start)
     */
    public void startAllWork() {
        // Schedule location updates every 30 minutes (will be adjusted to minimum 15)
        scheduleLocationUpdates(30);
        
        // Schedule data sync every 60 minutes
        scheduleDataSync(60);
        
        Log.d(TAG, "All background work started");
    }
    
    /**
     * Stop all background work (called on logout)
     */
    public void stopAllWork() {
        cancelAllPeriodicWork();
        cancelWorkByTag(TAG_UPLOAD); // Cancel pending uploads
        Log.d(TAG, "All background work stopped");
    }
    
    // Callback interfaces
    public interface WorkStatusCallback {
        void onWorkStatus(List<WorkInfo> workInfos);
    }
    
    public interface UploadStatusCallback {
        void onUploadStatus(boolean hasPending);
    }
}