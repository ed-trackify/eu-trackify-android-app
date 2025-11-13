package common;

import java.util.HashMap;
import java.util.Map;

/**
 * Country-based SMS template system for pickup notifications
 * Maps country IDs to localized pickup SMS messages
 */
public class SMSTemplates {

    // Map of country_id to pickup SMS messages
    private static final Map<String, String> PICKUP_MESSAGES = new HashMap<>();

    // Default pickup message (Croatian)
    private static final String DEFAULT_PICKUP_MESSAGE =
        "Poštovani, pošiljka za vas je spakirana i poslana danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala";

    static {
        // Initialize country-specific pickup messages

        // Croatian (HR) - Default
        PICKUP_MESSAGES.put("HR", "Poštovani, pošiljka za vas je spakirana i poslana danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");
        PICKUP_MESSAGES.put("191", "Poštovani, pošiljka za vas je spakirana i poslana danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");

        // Serbian (RS)
        PICKUP_MESSAGES.put("RS", "Poštovani, pošiljka za vas je spakovana i poslata danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");
        PICKUP_MESSAGES.put("688", "Poštovani, pošiljka za vas je spakovana i poslata danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");

        // Bosnia and Herzegovina (BA)
        PICKUP_MESSAGES.put("BA", "Poštovani, pošiljka za vas je spakirana i poslana danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");
        PICKUP_MESSAGES.put("70", "Poštovani, pošiljka za vas je spakirana i poslana danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");

        // Slovenia (SI)
        PICKUP_MESSAGES.put("SI", "Spoštovani, vaša pošiljka je bila pakirana in poslana danes prek GLS dostave v 24-48 urah. Pričakujte klic kurirja. Hvala");
        PICKUP_MESSAGES.put("705", "Spoštovani, vaša pošiljka je bila pakirana in poslana danes prek GLS dostave v 24-48 urah. Pričakujte klic kurirja. Hvala");

        // North Macedonia (MK)
        PICKUP_MESSAGES.put("MK", "Почитувани, пратката за вас е спакувана и испратена денес преку GLS достава во рок од 24-48ч. Очекувајте повик од курирот. Благодариме");
        PICKUP_MESSAGES.put("807", "Почитувани, пратката за вас е спакувана и испратена денес преку GLS достава во рок од 24-48ч. Очекувајте повик од курирот. Благодариме");

        // Montenegro (ME)
        PICKUP_MESSAGES.put("ME", "Poštovani, pošiljka za vas je spakovana i poslata danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");
        PICKUP_MESSAGES.put("499", "Poštovani, pošiljka za vas je spakovana i poslata danas putem GLS dostavne službe u roku od 24-48h. Očekujte poziv kurira. Hvala");

        // Albania (AL)
        PICKUP_MESSAGES.put("AL", "Të nderuar, dërgesa juaj është paketuar dhe dërguar sot përmes shërbimit GLS brenda 24-48 orëve. Prisni telefonatën e kurierit. Faleminderit");
        PICKUP_MESSAGES.put("8", "Të nderuar, dërgesa juaj është paketuar dhe dërguar sot përmes shërbimit GLS brenda 24-48 orëve. Prisni telefonatën e kurierit. Faleminderit");

        // Kosovo (XK)
        PICKUP_MESSAGES.put("XK", "Të nderuar, dërgesa juaj është paketuar dhe dërguar sot përmes shërbimit GLS brenda 24-48 orëve. Prisni telefonatën e kurierit. Faleminderit");

        // English (fallback for other countries)
        PICKUP_MESSAGES.put("EN", "Dear customer, your package has been packed and sent today via GLS delivery service within 24-48h. Expect a call from the courier. Thank you");
        PICKUP_MESSAGES.put("US", "Dear customer, your package has been packed and sent today via GLS delivery service within 24-48h. Expect a call from the courier. Thank you");
        PICKUP_MESSAGES.put("GB", "Dear customer, your package has been packed and sent today via GLS delivery service within 24-48h. Expect a call from the courier. Thank you");
    }

    /**
     * Get pickup SMS message for a specific country
     * @param countryId Country ID (e.g., "HR", "RS", "191", "688")
     * @return Localized pickup message or default Croatian message
     */
    public static String getPickupMessage(String countryId) {
        if (countryId == null || countryId.isEmpty()) {
            return DEFAULT_PICKUP_MESSAGE;
        }

        // Try to get message for the country ID
        String message = PICKUP_MESSAGES.get(countryId.toUpperCase());

        // If not found, return default Croatian message
        return message != null ? message : DEFAULT_PICKUP_MESSAGE;
    }

    /**
     * Add or update a pickup message for a specific country
     * @param countryId Country ID
     * @param message SMS message template
     */
    public static void setPickupMessage(String countryId, String message) {
        if (countryId != null && !countryId.isEmpty() && message != null && !message.isEmpty()) {
            PICKUP_MESSAGES.put(countryId.toUpperCase(), message);
        }
    }

    /**
     * Check if a country has a specific pickup message
     * @param countryId Country ID
     * @return true if country has a specific message
     */
    public static boolean hasPickupMessage(String countryId) {
        return countryId != null && PICKUP_MESSAGES.containsKey(countryId.toUpperCase());
    }
}
