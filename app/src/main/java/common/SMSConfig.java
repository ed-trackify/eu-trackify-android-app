package common;

public class SMSConfig {

    // Enable/Disable SMS globally
    public static final boolean SMS_ENABLED = true;

    // SMS Reply Monitoring Configuration
    public static final boolean SMS_REPLY_MONITORING_ENABLED = true;  // Enable/disable SMS reply monitoring
    public static final int SMS_REPLY_CHECK_INTERVAL_MINUTES = 2;     // How often to check for SMS replies (in minutes)

    // Control which statuses trigger SMS
    public static final boolean SEND_SMS_FOR_IN_DELIVERY = true;   // status_id = 1 (In Delivery)
    public static final boolean SEND_SMS_FOR_DELIVERED = false;     // status_id = 2 (Delivered)
    public static final boolean SEND_SMS_FOR_REJECTED = false;      // status_id = 3 (Problematic)
    public static final boolean SEND_SMS_FOR_PICKED_UP = true;      // status_id = 4 (Picked Up) - ENABLED
    public static final boolean SEND_SMS_FOR_PACKED = true;         // status_id = 14 (Packed) - ENABLED
    
    // Message Templates - Base messages
    public static final String MSG_IN_DELIVERY = "Pocituvani, vo EU Trackify e primena pratka za vas, ke vi bide isporacana vo narednite 48h. Ve molime ocekuvajte povik od ovoj telefonski broj. ";
    public static final String MSG_IN_DELIVERY_SHORT = "Pratka e kaj kurirot i e na pat kon vas.";
    
    // COD addition for In Delivery message
    public static final String MSG_COD_ADDITION = " Suma za naplata e {receiver_cod}, ve molime imajte ja spremno. Vi blagodarime.";
    
    // Other status messages (currently disabled)
    public static final String MSG_DELIVERED = "Your package has been successfully delivered. Thank you for using {company}.";
    public static final String MSG_REJECTED = "Delivery attempt for package was unsuccessful. We will contact you to reschedule.";
    public static final String MSG_PICKED_UP = "Your package has been picked up from {receiver_address} and is being processed. Thank you for using {company}.";
    public static final String MSG_PICKED_UP_SHORT = "Package picked up from {receiver_address}. Processing now.";
    
    // Method to check if SMS should be sent for a given status
    public static boolean shouldSendSMS(int statusId) {
        if (!SMS_ENABLED) {
            return false;
        }
        
        switch (statusId) {
            case 1: // In Delivery
                return SEND_SMS_FOR_IN_DELIVERY;
            case 2: // Delivered
                return SEND_SMS_FOR_DELIVERED;
            case 3: // Rejected/Problematic
                return SEND_SMS_FOR_REJECTED;
            case 4: // Picked Up
                return SEND_SMS_FOR_PICKED_UP;
            case 14: // Packed
                return SEND_SMS_FOR_PACKED;
            default:
                return false;
        }
    }
    
    // Method to get the appropriate message template for a status
    public static String getMessageTemplate(int statusId, boolean hasCustomText, String customText) {
        // If custom text is provided, use it
        if (hasCustomText && customText != null && !customText.isEmpty()) {
            return customText;
        }

        // Otherwise use configured templates
        switch (statusId) {
            case 1: // In Delivery
                return MSG_IN_DELIVERY;
            case 2: // Delivered
                return MSG_DELIVERED;
            case 3: // Rejected/Problematic
                return MSG_REJECTED;
            case 4: // Picked Up
                return MSG_PICKED_UP;
            case 14: // Packed
                return MSG_PICKED_UP;
            default:
                return "";
        }
    }

    // Method to get pickup message based on country ID
    public static String getPickupMessageByCountry(String countryId) {
        return SMSTemplates.getPickupMessage(countryId);
    }
    
    // Method to build the complete message with COD addition if needed
    public static String buildCompleteMessage(String baseMessage, boolean hasCod, double codAmount) {
        if (hasCod && codAmount > 0) {
            // Add COD message
            String codMessage = MSG_COD_ADDITION.replace("{receiver_cod}", String.format("%.0f €", codAmount));
            return baseMessage + codMessage;
        }
        return baseMessage;
    }
}