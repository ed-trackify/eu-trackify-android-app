package common;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.HashMap;
import java.util.Map;

/**
 * Efficient background task manager replacing inefficient Thread.sleep polling
 */
public class BackgroundTaskManager {
    
    private static BackgroundTaskManager instance;
    private ScheduledExecutorService scheduler;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler;
    private Map<String, ScheduledFuture<?>> scheduledTasks;
    
    private static final int CORE_POOL_SIZE = 3;
    
    private BackgroundTaskManager() {
        scheduler = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
        scheduledTasks = new HashMap<>();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Create background thread for non-scheduled tasks
        backgroundThread = new HandlerThread("BackgroundTaskThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    public static BackgroundTaskManager getInstance() {
        if (instance == null) {
            synchronized (BackgroundTaskManager.class) {
                if (instance == null) {
                    instance = new BackgroundTaskManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Schedule a periodic task with fixed delay
     */
    public void schedulePeriodicTask(String taskId, Runnable task, long initialDelay, long period, TimeUnit unit) {
        cancelTask(taskId);
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(task, initialDelay, period, unit);
        scheduledTasks.put(taskId, future);
    }
    
    /**
     * Schedule GPS updates efficiently
     */
    public void scheduleGpsUpdates(long intervalSeconds) {
        schedulePeriodicTask("GPS_UPDATE", () -> {
            if (App.CurrentUser != null && GPS.IsConnected && AppModel.IS_GPS_UPDATE_ENABLED) {
                Communicator.UpdateGps(new Communicator.IServerResponse() {
                    @Override
                    public void onCompleted(boolean success, String messageToShow, Object... objs) {
                        // Handle response if needed
                    }
                });
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule settings sync efficiently
     */
    public void scheduleSettingsSync(long intervalSeconds) {
        schedulePeriodicTask("SETTINGS_SYNC", () -> {
            if (App.CurrentUser != null) {
                Communicator.GetSettings(new Communicator.IServerResponse() {
                    @Override
                    public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                        if (success && objs != null && objs.length > 0 && objs[0] instanceof AppSetting) {
                            final AppSetting setting = (AppSetting) objs[0];
                            runOnMainThread(() -> {
                                if (App.Object != null) {
                                    App.Object.ApplySettings(setting);
                                }
                            });
                        }
                    }
                });
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Schedule data sync efficiently
     */
    public void scheduleDataSync(long intervalMinutes) {
        schedulePeriodicTask("DATA_SYNC", () -> {
            if (App.CurrentUser != null && ErrorHandler.isNetworkAvailable(App.Object)) {
                // Sync data
                runOnBackgroundThread(() -> {
                    try {
                        // Your sync logic here
                        if (App.Object != null && App.Object.userDistributorMyShipmentsFragment != null) {
                            App.Object.userDistributorMyShipmentsFragment.Load();
                        }
                    } catch (Exception e) {
                        AppModel.ApplicationError(e, "BackgroundTaskManager::DataSync");
                    }
                });
            }
        }, 0, intervalMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Run task on background thread
     */
    public void runOnBackgroundThread(Runnable task) {
        if (backgroundHandler != null) {
            backgroundHandler.post(task);
        }
    }
    
    /**
     * Run task on background thread with delay
     */
    public void runOnBackgroundThreadDelayed(Runnable task, long delayMs) {
        if (backgroundHandler != null) {
            backgroundHandler.postDelayed(task, delayMs);
        }
    }
    
    /**
     * Run task on main/UI thread
     */
    public void runOnMainThread(Runnable task) {
        mainHandler.post(task);
    }
    
    /**
     * Run task on main thread with delay
     */
    public void runOnMainThreadDelayed(Runnable task, long delayMs) {
        mainHandler.postDelayed(task, delayMs);
    }
    
    /**
     * Cancel a specific scheduled task
     */
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(taskId);
        }
    }
    
    /**
     * Cancel all scheduled tasks
     */
    public void cancelAllTasks() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
    }
    
    /**
     * Shutdown the task manager
     */
    public void shutdown() {
        cancelAllTasks();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        
        instance = null;
    }
    
    /**
     * Start all background tasks
     */
    public void startAllBackgroundTasks() {
        // GPS updates every 30 seconds (configurable)
        scheduleGpsUpdates(AppModel.GPS_DELAY_IN_SECONDS);
        
        // Settings sync every 60 seconds
        scheduleSettingsSync(60);
        
        // Data sync every 5 minutes
        scheduleDataSync(5);
    }
    
    /**
     * Stop all background tasks (for when app goes to background)
     */
    public void stopAllBackgroundTasks() {
        cancelTask("GPS_UPDATE");
        cancelTask("SETTINGS_SYNC");
        cancelTask("DATA_SYNC");
    }
}