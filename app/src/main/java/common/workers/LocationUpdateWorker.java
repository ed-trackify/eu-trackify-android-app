package common.workers;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;

import common.GPS;
import common.App;
import common.Communicator;
import common.AppModel;

/**
 * WorkManager Worker for periodic location updates
 * Runs reliably even when app is in background or device is in Doze mode
 */
public class LocationUpdateWorker extends Worker {
    
    private static final String TAG = "LocationUpdateWorker";
    
    public LocationUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            // Check if user is logged in
            if (App.CurrentUser == null) {
                Log.d(TAG, "No logged in user, skipping location update");
                return Result.success();
            }
            
            // Check if GPS updates are enabled (using default true for now)
            if (false) { // TODO: Add GPS enable check
                Log.d(TAG, "GPS updates disabled, skipping");
                return Result.success();
            }
            
            // Get current location
            Location location = null;
            try {
                // Use static GPS coordinates if available
                if (GPS.IsConnected && GPS.Lat != 0 && GPS.Lon != 0) {
                    location = new Location("gps");
                    location.setLatitude(GPS.Lat);
                    location.setLongitude(GPS.Lon);
                    location.setAccuracy(10.0f);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting location", e);
            }
            
            if (location == null) {
                Log.w(TAG, "Could not get location");
                // Retry later if location is temporarily unavailable
                return Result.retry();
            }
            
            // Prepare location data
            Data outputData = new Data.Builder()
                .putDouble("latitude", location.getLatitude())
                .putDouble("longitude", location.getLongitude())
                .putFloat("accuracy", location.getAccuracy())
                .putLong("timestamp", System.currentTimeMillis())
                .build();
            
            // Send location to server
            boolean success = sendLocationToServer(location);
            
            if (success) {
                Log.d(TAG, "Location update successful");
                return Result.success(outputData);
            } else {
                Log.w(TAG, "Location update failed, will retry");
                // Retry with exponential backoff
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in location update worker", e);
            AppModel.ApplicationError(e, "LocationUpdateWorker::doWork");
            
            // Check if this is the last retry attempt
            if (getRunAttemptCount() >= 3) {
                // Stop retrying after 3 attempts
                return Result.failure();
            }
            
            return Result.retry();
        }
    }
    
    /**
     * Send location to server synchronously
     */
    private boolean sendLocationToServer(Location location) {
        final boolean[] result = {false};
        final Object lock = new Object();
        
        Communicator.UpdateGps(new Communicator.IServerResponse() {
            @Override
            public void onCompleted(boolean success, String messageToShow, Object... objs) {
                synchronized (lock) {
                    result[0] = success;
                    lock.notify();
                }
            }
        });
        
        // Wait for response with timeout
        synchronized (lock) {
            try {
                lock.wait(30000); // 30 second timeout
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for server response");
            }
        }
        
        return result[0];
    }
    
    /**
     * Called when the worker is stopped (constraints no longer met, or explicitly cancelled)
     */
    @Override
    public void onStopped() {
        super.onStopped();
        Log.d(TAG, "Location update worker stopped");
    }
}