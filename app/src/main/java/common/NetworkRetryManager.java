package common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Manages network request retries with exponential backoff
 */
public class NetworkRetryManager {
    
    public interface RetryCallback {
        void onRetry(int attemptNumber);
        void onSuccess();
        void onFailure(String error);
    }
    
    public interface RetryableOperation {
        void execute(OperationCallback callback);
    }
    
    public interface OperationCallback {
        void onSuccess(Object result);
        void onError(Exception error);
    }
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY = 1000; // 1 second
    private static final float DEFAULT_BACKOFF_MULTIPLIER = 2.0f;
    private static final long MAX_DELAY = 30000; // 30 seconds
    
    private Handler handler;
    private Context context;
    
    public NetworkRetryManager(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Execute operation with automatic retry on failure
     */
    public void executeWithRetry(RetryableOperation operation, RetryCallback callback) {
        executeWithRetry(operation, callback, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY);
    }
    
    /**
     * Execute operation with custom retry configuration
     */
    public void executeWithRetry(RetryableOperation operation, RetryCallback callback, 
                                 int maxRetries, long initialDelay) {
        RetryState state = new RetryState(maxRetries, initialDelay);
        attemptOperation(operation, callback, state);
    }
    
    private void attemptOperation(RetryableOperation operation, RetryCallback callback, RetryState state) {
        state.currentAttempt++;
        
        // Notify about retry attempt
        if (state.currentAttempt > 1 && callback != null) {
            callback.onRetry(state.currentAttempt);
        }
        
        // Check network before attempting
        if (!ErrorHandler.isNetworkAvailable(context)) {
            if (state.currentAttempt < state.maxRetries) {
                // Schedule retry
                scheduleRetry(operation, callback, state);
            } else {
                if (callback != null) {
                    callback.onFailure("No internet connection. Please check your network settings.");
                }
            }
            return;
        }
        
        // Execute the operation
        operation.execute(new OperationCallback() {
            @Override
            public void onSuccess(Object result) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }
            
            @Override
            public void onError(Exception error) {
                if (ErrorHandler.isRecoverableError(error) && state.currentAttempt < state.maxRetries) {
                    // Schedule retry for recoverable errors
                    scheduleRetry(operation, callback, state);
                } else {
                    // Max retries reached or non-recoverable error
                    if (callback != null) {
                        String errorMessage = ErrorHandler.getUserFriendlyMessage(error);
                        if (state.currentAttempt >= state.maxRetries) {
                            errorMessage = "Failed after " + state.maxRetries + " attempts. " + errorMessage;
                        }
                        callback.onFailure(errorMessage);
                    }
                }
            }
        });
    }
    
    private void scheduleRetry(RetryableOperation operation, RetryCallback callback, RetryState state) {
        long delay = calculateDelay(state);
        
        handler.postDelayed(() -> {
            attemptOperation(operation, callback, state);
        }, delay);
    }
    
    private long calculateDelay(RetryState state) {
        long delay = (long) (state.currentDelay * Math.pow(DEFAULT_BACKOFF_MULTIPLIER, state.currentAttempt - 1));
        delay = Math.min(delay, MAX_DELAY);
        
        // Add some jitter to prevent thundering herd
        delay += (long) (Math.random() * 1000);
        
        return delay;
    }
    
    /**
     * Cancel all pending retries
     */
    public void cancelAllRetries() {
        handler.removeCallbacksAndMessages(null);
    }
    
    private static class RetryState {
        int maxRetries;
        int currentAttempt = 0;
        long currentDelay;
        
        RetryState(int maxRetries, long initialDelay) {
            this.maxRetries = maxRetries;
            this.currentDelay = initialDelay;
        }
    }
    
    /**
     * Convenience method for simple HTTP requests with retry
     */
    public static void retryHttpRequest(Context context, RetryableOperation operation, 
                                       Communicator.IServerResponse response) {
        NetworkRetryManager manager = new NetworkRetryManager(context);
        
        manager.executeWithRetry(operation, new RetryCallback() {
            @Override
            public void onRetry(int attemptNumber) {
                // Show retry message
                String message = "Retrying... (Attempt " + attemptNumber + ")";
                if (attemptNumber > 1) {
                    MessageCtrl.Toast(message);
                }
            }
            
            @Override
            public void onSuccess() {
                // Success handled by operation callback
            }
            
            @Override
            public void onFailure(String error) {
                if (response != null) {
                    response.onCompleted(false, error);
                }
            }
        });
    }
}