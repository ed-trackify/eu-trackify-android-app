package common;

import android.content.Context;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SMSLimitModifier {
    
    /**
     * Increases SMS limit to 100 messages per 30 minutes
     * Requires ROOT access on the device
     * 
     * Run this once at app startup or when needed
     */
    public static boolean increaseSMSLimit(Context context) {
        try {
            // Check if device is rooted
            if (!isDeviceRooted()) {
                AppModel.ApplicationError(null, "SMS Limit: Device is not rooted. Cannot modify SMS limits.");
                return false;
            }
            
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // Increase SMS limit to 100 messages per 30 minutes
            // Default is 30 messages per 1800000ms (30 minutes)
            os.writeBytes("settings put global sms_outgoing_check_max_count 100\n");
            os.writeBytes("settings put global sms_outgoing_check_interval_ms 1800000\n");
            
            // Also set for secure settings (some Android versions)
            os.writeBytes("settings put secure sms_outgoing_check_max_count 100\n");
            os.writeBytes("settings put secure sms_outgoing_check_interval_ms 1800000\n");
            
            // Clear any existing rate limit data
            os.writeBytes("pm clear com.android.providers.telephony\n");
            
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                AppModel.ApplicationError(null, "SMS Limit: Successfully increased to 100 messages per 30 minutes");
                
                // Verify the change
                verifyLimitChange();
                return true;
            } else {
                AppModel.ApplicationError(null, "SMS Limit: Failed to modify settings. Exit code: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMSLimitModifier: Failed to increase SMS limit");
            return false;
        }
    }
    
    /**
     * Check if device is rooted
     */
    private static boolean isDeviceRooted() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verify that the SMS limit was changed successfully
     */
    private static void verifyLimitChange() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            // Read current SMS limit setting
            os.writeBytes("settings get global sms_outgoing_check_max_count\n");
            os.writeBytes("exit\n");
            os.flush();
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() > 0) {
                    AppModel.ApplicationError(null, "SMS Limit: Current limit is " + line + " messages");
                    break;
                }
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMSLimitModifier: Could not verify limit change");
        }
    }
    
    /**
     * Reset SMS limit to Android default (30 messages)
     * Use this if you want to restore default behavior
     */
    public static boolean resetToDefault(Context context) {
        try {
            if (!isDeviceRooted()) {
                return false;
            }
            
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // Reset to Android defaults
            os.writeBytes("settings put global sms_outgoing_check_max_count 30\n");
            os.writeBytes("settings put global sms_outgoing_check_interval_ms 1800000\n");
            os.writeBytes("settings put secure sms_outgoing_check_max_count 30\n");
            os.writeBytes("settings put secure sms_outgoing_check_interval_ms 1800000\n");
            
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                AppModel.ApplicationError(null, "SMS Limit: Reset to default (30 messages)");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMSLimitModifier: Failed to reset SMS limit");
            return false;
        }
    }
    
    /**
     * One-time setup to be called from App.onCreate() or when needed
     */
    public static void setupSMSLimit(Context context) {
        // Try to increase SMS limit on app start
        boolean success = increaseSMSLimit(context);
        
        if (success) {
            MessageCtrl.Toast("SMS limit increased to 100 messages");
        } else {
            // Device not rooted or failed - use queue system instead
            AppModel.ApplicationError(null, "SMS Limit: Using queue system with default 30 message limit");
        }
    }
}