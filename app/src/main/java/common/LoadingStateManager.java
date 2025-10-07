package common;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Manages loading states throughout the app with consistent UI feedback
 */
public class LoadingStateManager {
    
    private static LoadingStateManager instance;
    private ProgressDialog progressDialog;
    private Handler mainHandler;
    private int activeLoadingCount = 0;
    
    private LoadingStateManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static LoadingStateManager getInstance() {
        if (instance == null) {
            synchronized (LoadingStateManager.class) {
                if (instance == null) {
                    instance = new LoadingStateManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Show loading with custom message
     */
    public void showLoading(Context context, String message) {
        mainHandler.post(() -> {
            activeLoadingCount++;
            if (progressDialog == null || !progressDialog.isShowing()) {
                progressDialog = new ProgressDialog(context);
                progressDialog.setMessage(message != null ? message : "Loading...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            } else if (message != null) {
                progressDialog.setMessage(message);
            }
        });
    }
    
    /**
     * Show loading with default message
     */
    public void showLoading(Context context) {
        showLoading(context, "Loading...");
    }
    
    /**
     * Hide loading dialog
     */
    public void hideLoading() {
        mainHandler.post(() -> {
            activeLoadingCount = Math.max(0, activeLoadingCount - 1);
            if (activeLoadingCount == 0 && progressDialog != null && progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    // Dialog might be attached to destroyed context
                }
                progressDialog = null;
            }
        });
    }
    
    /**
     * Force hide all loading dialogs
     */
    public void forceHideAllLoading() {
        mainHandler.post(() -> {
            activeLoadingCount = 0;
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    // Ignore
                }
                progressDialog = null;
            }
        });
    }
    
    /**
     * Show loading state in a specific view container
     */
    public static void showViewLoading(View container, View loadingView, View contentView) {
        if (container == null) return;
        
        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
        }
        if (contentView != null) {
            contentView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Hide loading state in a specific view container
     */
    public static void hideViewLoading(View container, View loadingView, View contentView) {
        if (container == null) return;
        
        if (loadingView != null) {
            loadingView.setVisibility(View.GONE);
        }
        if (contentView != null) {
            contentView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Show error state in a view
     */
    public static void showViewError(View container, View errorView, String errorMessage) {
        if (container == null || errorView == null) return;
        
        errorView.setVisibility(View.VISIBLE);
        
        // Try to find error text view
        if (errorView instanceof TextView) {
            ((TextView) errorView).setText(errorMessage);
        } else {
            TextView errorText = errorView.findViewWithTag("error_text");
            if (errorText != null) {
                errorText.setText(errorMessage);
            }
        }
    }
    
    /**
     * Update progress for long operations
     */
    public void updateProgress(Context context, String message, int progress, int max) {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.setMessage(message);
                if (max > 0) {
                    progressDialog.setIndeterminate(false);
                    progressDialog.setMax(max);
                    progressDialog.setProgress(progress);
                }
            }
        });
    }
    
    /**
     * Show loading with timeout
     */
    public void showLoadingWithTimeout(Context context, String message, long timeoutMs) {
        showLoading(context, message);
        mainHandler.postDelayed(this::hideLoading, timeoutMs);
    }
    
    /**
     * Check if loading is currently shown
     */
    public boolean isLoading() {
        return activeLoadingCount > 0;
    }
}