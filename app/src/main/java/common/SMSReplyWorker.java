package common;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;

import java.util.concurrent.TimeUnit;

/**
 * Background worker for SMS reply monitoring
 * Runs independently and can be toggled on/off
 */
public class SMSReplyWorker extends Worker {
    private static final String TAG = "SMSReplyWorker";
    private static final String WORK_NAME = "sms_reply_check";

    public SMSReplyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SMSReplyMonitor monitor = SMSReplyMonitor.getInstance(getApplicationContext());

            // Check if monitoring is enabled
            if (!monitor.isEnabled()) {
                AppModel.ApplicationError(null, "SMS Reply Worker: Monitoring is disabled, skipping check");
                return Result.success();
            }

            AppModel.ApplicationError(null, "SMS Reply Worker: Starting check for new replies");

            // Check for new replies
            monitor.checkForNewReplies();

            AppModel.ApplicationError(null, "SMS Reply Worker: Check completed");
            return Result.success();

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Worker: Error during work");
            // Return success anyway to keep the periodic work running
            return Result.success();
        }
    }

    /**
     * Schedule periodic SMS reply checks
     */
    public static void schedulePeriodicCheck(Context context) {
        try {
            // Create constraints - only run when network is available
            Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

            // Create periodic work request - check every 5 minutes
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    SMSReplyWorker.class,
                    15, TimeUnit.MINUTES) // Minimum is 15 minutes for periodic work
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build();

            // Enqueue the work
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            );

            AppModel.ApplicationError(null, "SMS Reply Worker: Scheduled periodic checks every 15 minutes");

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Worker: Failed to schedule periodic work");
        }
    }

    /**
     * Cancel periodic SMS reply checks
     */
    public static void cancelPeriodicCheck(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            AppModel.ApplicationError(null, "SMS Reply Worker: Cancelled periodic checks");
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Worker: Failed to cancel periodic work");
        }
    }

    /**
     * Trigger an immediate check (for testing)
     */
    public static void triggerImmediateCheck(Context context) {
        try {
            SMSReplyMonitor monitor = SMSReplyMonitor.getInstance(context);
            if (monitor.isEnabled()) {
                new Thread(() -> {
                    monitor.checkForNewReplies();
                }).start();
                AppModel.ApplicationError(null, "SMS Reply Worker: Triggered immediate check");
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMS Reply Worker: Failed to trigger immediate check");
        }
    }
}