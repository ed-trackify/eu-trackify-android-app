package common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/**
 * Centralized error handler for consistent user-friendly error messages
 */
public class ErrorHandler {
    
    public enum ErrorType {
        NETWORK_TIMEOUT,
        NO_INTERNET,
        SERVER_ERROR,
        AUTH_FAILED,
        VALIDATION_ERROR,
        PERMISSION_DENIED,
        FILE_NOT_FOUND,
        UNKNOWN
    }
    
    /**
     * Get user-friendly error message based on exception
     */
    public static String getUserFriendlyMessage(Exception e) {
        if (e == null) {
            return "An unexpected error occurred. Please try again.";
        }
        
        // Network related errors
        if (e instanceof SocketTimeoutException) {
            return "Connection timed out. Please check your internet connection and try again.";
        } else if (e instanceof UnknownHostException) {
            return "Unable to reach the server. Please check your internet connection.";
        } else if (e instanceof ConnectException) {
            return "Cannot connect to the server. Please check your internet connection.";
        } else if (e instanceof SSLException) {
            return "Secure connection failed. Please try again.";
        }
        
        // Parse server error messages
        String message = e.getMessage();
        if (message != null) {
            if (message.toLowerCase().contains("unauthorized") || 
                message.toLowerCase().contains("401")) {
                return "Your session has expired. Please log in again.";
            } else if (message.toLowerCase().contains("forbidden") || 
                       message.toLowerCase().contains("403")) {
                return "You don't have permission to perform this action.";
            } else if (message.toLowerCase().contains("not found") || 
                       message.toLowerCase().contains("404")) {
                return "The requested information was not found.";
            } else if (message.toLowerCase().contains("server") || 
                       message.toLowerCase().contains("500")) {
                return "Server error occurred. Please try again later.";
            } else if (message.toLowerCase().contains("network")) {
                return "Network error. Please check your connection.";
            }
        }
        
        // Default message
        return "Something went wrong. Please try again.";
    }
    
    /**
     * Get specific error message for common operations
     */
    public static String getOperationErrorMessage(String operation, Exception e) {
        String baseMessage = getUserFriendlyMessage(e);
        
        switch (operation.toLowerCase()) {
            case "login":
                if (baseMessage.contains("session")) {
                    return "Invalid username or password. Please try again.";
                }
                return "Unable to log in. " + baseMessage;
                
            case "upload":
                return "Failed to upload. " + baseMessage;
                
            case "download":
                return "Failed to download. " + baseMessage;
                
            case "sync":
                return "Failed to sync data. " + baseMessage;
                
            case "print":
                return "Failed to print. Please check printer connection.";
                
            case "scan":
                return "Failed to scan. Please try again.";
                
            case "deliver":
                return "Failed to mark as delivered. " + baseMessage;
                
            case "signature":
                return "Failed to save signature. " + baseMessage;
                
            default:
                return baseMessage;
        }
    }
    
    /**
     * Check if device has internet connection
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
    
    /**
     * Get error type from exception
     */
    public static ErrorType getErrorType(Exception e) {
        if (e == null) return ErrorType.UNKNOWN;
        
        if (e instanceof SocketTimeoutException) {
            return ErrorType.NETWORK_TIMEOUT;
        } else if (e instanceof UnknownHostException || e instanceof ConnectException) {
            return ErrorType.NO_INTERNET;
        }
        
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
                return ErrorType.AUTH_FAILED;
            } else if (message.contains("403") || message.toLowerCase().contains("forbidden")) {
                return ErrorType.PERMISSION_DENIED;
            } else if (message.contains("404") || message.toLowerCase().contains("not found")) {
                return ErrorType.FILE_NOT_FOUND;
            } else if (message.contains("500") || message.toLowerCase().contains("server")) {
                return ErrorType.SERVER_ERROR;
            } else if (message.toLowerCase().contains("validation")) {
                return ErrorType.VALIDATION_ERROR;
            }
        }
        
        return ErrorType.UNKNOWN;
    }
    
    /**
     * Check if error is recoverable (worth retrying)
     */
    public static boolean isRecoverableError(Exception e) {
        ErrorType type = getErrorType(e);
        return type == ErrorType.NETWORK_TIMEOUT || 
               type == ErrorType.NO_INTERNET || 
               type == ErrorType.SERVER_ERROR;
    }
    
    /**
     * Get retry delay based on attempt number
     */
    public static long getRetryDelay(int attemptNumber) {
        // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
        long delay = Math.min((long) Math.pow(2, attemptNumber - 1) * 1000, 30000);
        return delay;
    }
}