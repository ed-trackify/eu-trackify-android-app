package common;

/**
 * Application Configuration
 * Central place for enabling/disabling app features
 */
public class AppConfig {
    
    // ========== FEATURE FLAGS ==========
    
    /**
     * Bluetooth Feature
     * Enable/Disable Bluetooth functionality in the app
     * When disabled, all Bluetooth-related features will be hidden/inactive
     */
    public static final boolean BLUETOOTH_ENABLED = false;  // Set to true to enable Bluetooth
    
    /**
     * Printer Feature  
     * Enable/Disable Printer functionality in the app
     * When disabled, all printing features will be hidden/inactive
     */
    public static final boolean PRINTER_ENABLED = false;    // Set to true to enable Printing
    
    /**
     * SMS Feature
     * Enable/Disable SMS functionality in the app
     * Note: SMS configuration is also available in SMSConfig.java for detailed settings
     */
    public static final boolean SMS_FEATURE_ENABLED = true;  // Set to false to completely disable SMS
    
    // ========== DEBUG FLAGS ==========
    
    /**
     * Debug Mode
     * Enable additional logging and debug features
     */
    public static final boolean DEBUG_MODE = false;
    
    /**
     * Show Test Features
     * Enable test/development features in the UI
     */
    public static final boolean SHOW_TEST_FEATURES = false;
    
    // ========== HELPER METHODS ==========
    
    /**
     * Check if Bluetooth is enabled
     * @return true if Bluetooth feature is enabled
     */
    public static boolean isBluetoothEnabled() {
        return BLUETOOTH_ENABLED;
    }
    
    /**
     * Check if Printer is enabled
     * @return true if Printer feature is enabled
     */
    public static boolean isPrinterEnabled() {
        // Check dynamic setting from preferences if App context is available
        if (App.Object != null) {
            return SettingsCtrl.isPrinterEnabled(App.Object);
        }
        return PRINTER_ENABLED;
    }
    
    /**
     * Check if SMS is enabled
     * @return true if SMS feature is enabled
     */
    public static boolean isSmsEnabled() {
        return SMS_FEATURE_ENABLED && SMSConfig.SMS_ENABLED;
    }
}