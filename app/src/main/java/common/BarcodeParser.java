package common;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing different barcode formats found on shipping labels.
 *
 * Supported formats:
 * 1. Plain tracking ID (returned as-is)
 * 2. GS1/ANSI MH10.8.2 barcodes (starts with [)>) - full shipping label QR data
 *    separated by invisible control characters (GS=0x1D, RS=0x1E, EOT=0x04)
 * 3. GS1-128 barcodes (1D barcodes with ]C1 prefix or AI codes) - shipping label barcodes
 *    containing Application Identifiers like 00 (SSCC), 01 (GTIN), etc.
 * 4. URL-based QR codes (extracts tracking parameter if present)
 * 5. Any barcode containing a known tracking ID as a substring
 */
public class BarcodeParser {

    /**
     * Extract the most likely tracking number from any barcode format.
     * Tries to match against known loaded shipment tracking IDs first,
     * then falls back to format-specific parsing and heuristic extraction.
     *
     * @param rawCode The raw barcode content from the scanner
     * @return The extracted tracking number, or the raw code if not a structured barcode
     */
    public static String extractTrackingNumber(String rawCode) {
        if (rawCode == null || rawCode.isEmpty()) return rawCode;

        // First: check if any loaded shipment tracking ID matches or is contained in the barcode
        // This works for ALL barcode types and is the most reliable approach
        String matchedTracking = matchAgainstLoadedItems(rawCode);
        if (matchedTracking != null) return matchedTracking;

        // GS1/ANSI MH10.8.2 format (shipping label QR codes)
        if (rawCode.startsWith("[)>")) {
            List<String> candidates = parseANSIBarcode(rawCode);
            return candidates.isEmpty() ? rawCode : candidates.get(0);
        }

        // GS1-128 format (1D shipping label barcodes with ]C1 prefix)
        if (rawCode.startsWith("]C1")) {
            List<String> candidates = parseGS1128Barcode(rawCode.substring(3));
            return candidates.isEmpty() ? rawCode : candidates.get(0);
        }

        // URL-based QR codes
        if (rawCode.startsWith("http://") || rawCode.startsWith("https://")) {
            return parseURLBarcode(rawCode);
        }

        // DPD routing code (Code128 with FNC1 prefix)
        // Format: %[7-digit depot][14-digit tracking][6-digit service] = 28 chars
        // Or without %: [7-digit depot][14-digit tracking][6-digit service] = 27 chars
        String dpdTracking = parseDPDRoutingBarcode(rawCode);
        if (dpdTracking != null) return dpdTracking;

        // Check if it looks like GS1-128 data without prefix (starts with known AI)
        // Only match if it's pure digits with GS separators (real GS1-128, not DPD routing codes)
        if (rawCode.length() > 16 && rawCode.matches("^(01|02)\\d{14,}.*")) {
            List<String> candidates = parseGS1128Barcode(rawCode);
            if (!candidates.isEmpty()) return candidates.get(0);
        }

        // Long barcode that might contain embedded tracking number (e.g., DPD routing codes)
        // Try to extract the tracking number by matching against loaded items as substring
        if (rawCode.length() > 18) {
            String extracted = extractFromLongBarcode(rawCode);
            if (extracted != null) return extracted;
        }

        // Plain tracking ID - return as-is
        return rawCode;
    }

    /**
     * Extract an ordered list of tracking number candidates from any barcode.
     * Returns candidates sorted by likelihood (best first).
     * Used by StatusCheckCtrl and ReturnReceivedCtrl to try multiple candidates
     * if the first one fails.
     *
     * @param rawCode The raw barcode content from the scanner
     * @return List of candidate tracking numbers, best first.
     */
    public static List<String> extractCandidates(String rawCode) {
        List<String> candidates = new ArrayList<>();
        if (rawCode == null || rawCode.isEmpty()) return candidates;

        // First: check loaded items (works for ALL barcode types)
        String matchedTracking = matchAgainstLoadedItems(rawCode);
        if (matchedTracking != null) {
            candidates.add(matchedTracking);
            return candidates;
        }

        // GS1/ANSI MH10.8.2 format (shipping label QR codes)
        if (rawCode.startsWith("[)>")) {
            return parseANSIBarcode(rawCode);
        }

        // GS1-128 format (1D shipping label barcodes)
        if (rawCode.startsWith("]C1")) {
            candidates = parseGS1128Barcode(rawCode.substring(3));
            if (!candidates.isEmpty()) return candidates;
        }

        // DPD routing code (Code128 with FNC1 prefix)
        String dpdTracking = parseDPDRoutingBarcode(rawCode);
        if (dpdTracking != null) {
            candidates.add(dpdTracking);
            return candidates;
        }

        // Check for GS1-128 without prefix (only for clear GS1 formats)
        if (rawCode.length() > 16 && rawCode.matches("^(01|02)\\d{14,}.*")) {
            candidates = parseGS1128Barcode(rawCode);
            if (!candidates.isEmpty()) return candidates;
        }

        // URL-based QR codes
        if (rawCode.startsWith("http://") || rawCode.startsWith("https://")) {
            candidates.add(parseURLBarcode(rawCode));
            return candidates;
        }

        // Long barcode that might contain embedded tracking (e.g., DPD routing codes)
        if (rawCode.length() > 18) {
            candidates = extractCandidatesFromLongBarcode(rawCode);
            if (!candidates.isEmpty()) return candidates;
        }

        // Plain barcode - return as-is
        candidates.add(rawCode);
        return candidates;
    }

    /**
     * Try to match the barcode content against loaded shipment tracking IDs.
     * Handles exact match, substring match, and match after stripping control chars.
     *
     * @return The matched tracking ID, or null if no match found
     */
    private static String matchAgainstLoadedItems(String rawCode) {
        List<String> knownTrackingIds = getAllLoadedTrackingIds();
        if (knownTrackingIds.isEmpty()) return null;

        // Strip control characters for matching purposes
        String cleanCode = rawCode.replaceAll("[\\x00-\\x1F\\x7F]", "");

        for (String trackingId : knownTrackingIds) {
            // Exact match (raw or cleaned)
            if (trackingId.equalsIgnoreCase(rawCode) || trackingId.equalsIgnoreCase(cleanCode)) {
                return trackingId;
            }
            // Tracking ID is contained within the barcode data
            if (cleanCode.contains(trackingId)) {
                return trackingId;
            }
            // Case-insensitive substring match
            if (cleanCode.toLowerCase().contains(trackingId.toLowerCase())) {
                return trackingId;
            }
        }

        // Also try splitting by control chars first, then exact match per segment
        String[] segments = rawCode.split("[\\x00-\\x1F\\x7F]+");
        if (segments.length > 1) {
            for (String segment : segments) {
                String trimmed = segment.trim();
                if (trimmed.isEmpty()) continue;
                for (String trackingId : knownTrackingIds) {
                    if (trackingId.equalsIgnoreCase(trimmed)) {
                        return trackingId;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Parse DPD routing barcode (Code128 with FNC1).
     * Format: [%][7-digit depot][14-digit tracking][6+ digit service]
     * The % is FNC1 character from Code128 symbology.
     * ZXing may also represent FNC1 as \x1D (GS) or ]C1 prefix.
     *
     * Examples:
     *   %009740417522003367335330703 → tracking: 17522003367335
     *   %000737017522003367333330348 → tracking: 17522003367333
     *
     * @return The 14-digit tracking number, or null if not a DPD routing barcode
     */
    private static String parseDPDRoutingBarcode(String rawCode) {
        if (rawCode == null) return null;

        // Strip FNC1 prefix: % or \x1D (GS char)
        String data = rawCode;
        if (data.startsWith("%") || data.startsWith("\u001D")) {
            data = data.substring(1);
        }

        // DPD routing code is 27 digits (possibly with trailing letter)
        // Must be at least 27 chars: 7 depot + 14 tracking + 6 service
        if (data.length() < 27) return null;

        // First 21 characters must be all digits (depot + tracking)
        String first21 = data.substring(0, 21);
        if (!first21.matches("\\d{21}")) return null;

        // Service portion (remaining) should be mostly digits (may end with letter)
        String service = data.substring(21);
        if (!service.matches("\\d{5,}[A-Za-z]?")) return null;

        // Extract the 14-digit tracking number (positions 7-20)
        String tracking = data.substring(7, 21);
        return tracking;
    }

    /**
     * Extract tracking number from a long barcode (e.g., DPD routing codes).
     * These barcodes embed the tracking number within a longer string containing
     * depot codes, routing info, and service codes.
     *
     * Only returns a result if we can confidently extract the tracking number.
     * For loaded shipments, matchAgainstLoadedItems() handles this via substring matching.
     *
     * @return The extracted tracking number, or null if not found
     */
    private static String extractFromLongBarcode(String rawCode) {
        // For long barcodes without a recognized format, we can't reliably extract
        // the tracking number without loaded items to match against.
        // Return null - the caller will use the raw code as-is.
        return null;
    }

    /**
     * Extract tracking candidates from a long barcode.
     * Keeps the list short to avoid cycling through many wrong candidates.
     * For loaded shipments, matchAgainstLoadedItems() already handles this.
     */
    private static List<String> extractCandidatesFromLongBarcode(String rawCode) {
        List<String> candidates = new ArrayList<>();

        // Just try the raw code - the backend might handle routing codes directly
        candidates.add(rawCode);

        return candidates;
    }

    /**
     * Parse GS1-128 barcode format (Code 128 with GS1 Application Identifiers).
     * Common AIs in shipping:
     * - 00: SSCC (18 digits) - Serial Shipping Container Code
     * - 01: GTIN (14 digits) - Global Trade Item Number
     * - 02: GTIN of contained items (14 digits)
     * - 10: Batch/Lot number (variable, up to 20 chars)
     * - 17: Expiration date (6 digits)
     * - 420: Ship-to postal code (variable)
     *
     * Fields may be separated by GS character (0x1D) for variable-length AIs.
     */
    private static List<String> parseGS1128Barcode(String data) {
        List<String> candidates = new ArrayList<>();
        if (data == null || data.isEmpty()) return candidates;

        // Split by GS characters (field separators in GS1-128)
        String[] fields = data.split("[\\x1D]");

        for (String field : fields) {
            if (field.isEmpty()) continue;

            // Try to parse GS1 Application Identifiers
            List<String> extracted = extractFromGS1Field(field);
            candidates.addAll(extracted);
        }

        // If no AI-based extraction worked, try the whole data as segments
        if (candidates.isEmpty()) {
            // Maybe it's a simple barcode with the tracking number and some prefix/suffix
            String digits = data.replaceAll("[^\\d]", "");
            if (digits.length() >= 10 && digits.length() <= 20) {
                candidates.add(digits);
            }
        }

        return candidates;
    }

    /**
     * Extract tracking candidates from a GS1 field by parsing Application Identifiers.
     */
    private static List<String> extractFromGS1Field(String field) {
        List<String> results = new ArrayList<>();
        int pos = 0;

        while (pos < field.length()) {
            // Try to match known AIs at current position
            if (pos + 2 <= field.length()) {
                String ai2 = field.substring(pos, pos + 2);

                // AI 00: SSCC - 18 digits (fixed length)
                if (ai2.equals("00") && pos + 20 <= field.length()) {
                    String sscc = field.substring(pos + 2, pos + 20);
                    if (sscc.matches("\\d{18}")) {
                        results.add(sscc);
                        pos += 20;
                        continue;
                    }
                }

                // AI 01: GTIN - 14 digits (fixed length)
                if (ai2.equals("01") && pos + 16 <= field.length()) {
                    String gtin = field.substring(pos + 2, pos + 16);
                    if (gtin.matches("\\d{14}")) {
                        results.add(gtin);
                        pos += 16;
                        continue;
                    }
                }

                // AI 02: GTIN of contained items - 14 digits (fixed length)
                if (ai2.equals("02") && pos + 16 <= field.length()) {
                    String gtin = field.substring(pos + 2, pos + 16);
                    if (gtin.matches("\\d{14}")) {
                        results.add(gtin);
                        pos += 16;
                        continue;
                    }
                }

                // AI 10: Batch/Lot number - variable length up to 20 chars
                if (ai2.equals("10")) {
                    String remaining = field.substring(pos + 2);
                    // Variable length - take everything until end or next AI
                    String value = remaining.length() > 20 ? remaining.substring(0, 20) : remaining;
                    if (!value.isEmpty()) {
                        results.add(value);
                    }
                    pos += 2 + value.length();
                    continue;
                }

                // AI 17: Expiration date - 6 digits (fixed length) - skip, not tracking
                if (ai2.equals("17") && pos + 8 <= field.length()) {
                    pos += 8;
                    continue;
                }
            }

            // Try 3-digit AIs
            if (pos + 3 <= field.length()) {
                String ai3 = field.substring(pos, pos + 3);

                // AI 420: Ship-to postal code - skip, not tracking
                if (ai3.equals("420")) {
                    break; // Variable length, just stop here
                }
            }

            // Can't parse further - take remaining as potential candidate
            String remaining = field.substring(pos);
            if (remaining.length() >= 4 && remaining.matches("[A-Za-z0-9/\\-_.]+")) {
                results.add(remaining);
            }
            break;
        }

        return results;
    }

    /**
     * Parse ANSI MH10.8.2 barcode format (shipping label QR codes).
     * These barcodes contain fields separated by control characters:
     * GS (0x1D), RS (0x1E), EOT (0x04)
     */
    private static List<String> parseANSIBarcode(String rawCode) {
        List<String> candidates = new ArrayList<>();

        // Split by control characters
        String[] segments = rawCode.split("[\\x00-\\x1F\\x7F]+");

        if (segments.length <= 1) {
            candidates.add(rawCode);
            return candidates;
        }

        // Collect clean segments, skipping header/format fields
        List<String> dataSegments = new ArrayList<>();
        for (int i = 0; i < segments.length; i++) {
            String trimmed = segments[i].trim();
            if (trimmed.isEmpty()) continue;
            // Skip the ANSI header "[)>" and format type "01"
            if (trimmed.startsWith("[)>")) continue;
            if (i <= 2 && (trimmed.equals("01") || trimmed.equals("02") || trimmed.equals("00"))) continue;
            dataSegments.add(trimmed);
        }

        // Score each segment as a potential tracking number
        List<String> trackingCandidates = new ArrayList<>();
        List<String> secondaryCandidates = new ArrayList<>();

        for (int i = 0; i < dataSegments.size(); i++) {
            String seg = dataSegments.get(i);

            // Definite skip: obvious non-tracking fields
            if (isDefinitelyNotTracking(seg)) continue;

            // Check if it looks like a phone number
            if (isLikelyPhoneNumber(seg)) continue;

            // Check if it looks like a tracking number
            if (isLikelyTrackingNumber(seg)) {
                // Earlier segments are more likely to be tracking numbers
                // (tracking/ID fields come before address/contact fields in ANSI format)
                if (i < dataSegments.size() / 2) {
                    trackingCandidates.add(seg);
                } else {
                    secondaryCandidates.add(seg);
                }
            }
        }

        // Combine: primary candidates first, then secondary
        candidates.addAll(trackingCandidates);
        candidates.addAll(secondaryCandidates);

        // Limit to max 3 candidates to avoid cycling through many wrong values
        if (candidates.size() > 3) {
            candidates = new ArrayList<>(candidates.subList(0, 3));
        }

        // If no candidates found, return raw code as fallback
        if (candidates.isEmpty()) {
            candidates.add(rawCode);
        }

        return candidates;
    }

    /**
     * Check if a segment is definitely NOT a tracking number.
     */
    private static boolean isDefinitelyNotTracking(String seg) {
        String upper = seg.toUpperCase();

        // Weight values (e.g., "0.20KG", "5.5KG", "3LB")
        if (upper.endsWith("KG") || upper.endsWith("LB")) {
            if (seg.matches(".*\\d.*")) return true;
        }

        // Currency/money values (e.g., "EUR21.000", "USD50.00", "33.00")
        if (upper.matches("^(EUR|USD|HRK|MKD|RSD|BAM|ALL|GBP|CHF)\\d.*")) return true;
        if (seg.matches("^\\d+\\.\\d{2}$")) return true; // Decimal amounts like 33.00

        // Very short fields (single char, two chars, three chars) - not tracking numbers
        if (seg.length() <= 3) return true;

        // Fields with spaces are names/addresses
        if (seg.contains(" ")) return true;

        // Package count patterns (e.g., "001/001", "1/1", "2/3")
        if (seg.matches("^\\d{1,3}/\\d{1,3}$")) return true;

        // ANSI MH10.8.2 data identifiers (e.g., S010, S020, S030, G03)
        if (seg.matches("^[A-Z]\\d{2,3}$")) return true;

        // 3-4 letter uppercase codes (GEOP, COD, EUR, etc.)
        if (seg.matches("^[A-Z]{2,4}$")) return true;

        // Zip codes (exactly 4-5 digits)
        if (seg.matches("^\\d{4,5}$")) return true;

        // Email addresses
        if (seg.contains("@")) return true;

        // Addresses: text ending with house number (e.g., "Oremburska11")
        // Letters followed by a small number (1-4 digits)
        if (seg.matches("^[A-Za-z]+\\d{1,4}$")) return true;

        // Purely alphabetic strings are names/words, not tracking numbers
        if (seg.matches("^[A-Za-z]+$")) return true;

        return false;
    }

    /**
     * Check if a segment looks like a phone number.
     * Phone numbers in the EU/Balkans region typically:
     * - Start with country codes: 385 (HR), 381 (RS), 387 (BA), 383 (XK),
     *   389 (MK), 355 (AL), 386 (SI), 382 (ME), 36 (HU), 43 (AT), 39 (IT)
     * - Start with 0 (local format) or + (international)
     * - Are 10-15 digits long
     */
    private static boolean isLikelyPhoneNumber(String seg) {
        // Must be mostly digits
        if (!seg.matches("^[+]?\\d{8,}$")) return false;

        String digits = seg.replaceAll("[^\\d]", "");

        // International format with +
        if (seg.startsWith("+")) return true;

        // Starts with 0 (local phone format in most EU countries)
        if (seg.startsWith("0") && digits.length() >= 9 && digits.length() <= 15) return true;

        // Common country codes for the region
        String[] phonePrefixes = {
            "385", "381", "387", "383", "389", "355", "386", "382",  // Balkans
            "36", "43", "39", "49", "33", "44", "34", "31",          // Major EU
            "48", "40", "420", "421"                                   // Central EU
        };

        for (String prefix : phonePrefixes) {
            if (digits.startsWith(prefix) && digits.length() >= 10 && digits.length() <= 15) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a segment looks like a tracking number.
     * Tracking numbers are typically:
     * - 4-25 characters long
     * - Alphanumeric (may contain / or -)
     */
    private static boolean isLikelyTrackingNumber(String seg) {
        if (seg.length() < 4 || seg.length() > 25) return false;

        // Must be alphanumeric (with optional / - . _)
        if (!seg.matches("[A-Za-z0-9/\\-_.]+")) return false;

        return true;
    }

    /**
     * Parse URL-based QR codes.
     * Tries to extract a tracking ID from common URL patterns.
     */
    private static String parseURLBarcode(String rawCode) {
        try {
            // Try query parameter patterns
            String[] trackingParams = {"tracking", "track", "id", "code", "shipment", "parcel", "barcode"};
            if (rawCode.contains("?") || rawCode.contains("&")) {
                String query = rawCode.substring(rawCode.indexOf('?') + 1);
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].toLowerCase();
                        for (String trackingParam : trackingParams) {
                            if (key.contains(trackingParam)) {
                                return keyValue[1];
                            }
                        }
                    }
                }
            }

            // Try path-based patterns (last path segment)
            String path = rawCode;
            if (path.contains("?")) path = path.substring(0, path.indexOf('?'));
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            if (!lastSegment.isEmpty() && lastSegment.matches("[A-Za-z0-9/\\-_.]+") &&
                lastSegment.length() >= 4 && lastSegment.length() <= 25) {
                if (!lastSegment.contains(".") || lastSegment.matches(".*\\d.*")) {
                    return lastSegment;
                }
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "BarcodeParser::parseURLBarcode");
        }

        // Can't extract tracking from URL, return as-is
        return rawCode;
    }

    /**
     * Get all tracking IDs from all loaded shipment fragments.
     */
    private static List<String> getAllLoadedTrackingIds() {
        List<String> trackingIds = new ArrayList<>();

        try {
            if (App.Object != null) {
                // From My Shipments fragment (Draggable)
                if (App.Object.userDistributorMyShipmentsFragment != null &&
                    App.Object.userDistributorMyShipmentsFragment.ITEMS != null) {
                    for (ShipmentWithDetail s : App.Object.userDistributorMyShipmentsFragment.ITEMS) {
                        if (s.tracking_id != null) trackingIds.add(s.tracking_id);
                    }
                }
                // From Reconcile fragment
                if (App.Object.userDistributorReconcileShipmentsFragment != null &&
                    App.Object.userDistributorReconcileShipmentsFragment.ITEMS != null) {
                    for (ShipmentWithDetail s : App.Object.userDistributorReconcileShipmentsFragment.ITEMS) {
                        if (s.tracking_id != null) trackingIds.add(s.tracking_id);
                    }
                }
                // From Returns fragment
                if (App.Object.userDistributorReturnShipmentsFragment != null &&
                    App.Object.userDistributorReturnShipmentsFragment.ITEMS != null) {
                    for (ShipmentWithDetail s : App.Object.userDistributorReturnShipmentsFragment.ITEMS) {
                        if (s.tracking_id != null) trackingIds.add(s.tracking_id);
                    }
                }
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "BarcodeParser::getAllLoadedTrackingIds");
        }

        return trackingIds;
    }
}
