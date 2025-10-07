package common;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;

import java.util.Locale;

/**
 * Helper class for highlighting search text within displayed text
 */
public class TextHighlighter {

    /**
     * Highlights all occurrences of search query within text with yellow background
     *
     * @param text The original text to display
     * @param searchQuery The search query to highlight (case-insensitive)
     * @return SpannableString with highlighted text, or original text if no matches
     */
    public static CharSequence highlight(String text, String searchQuery) {
        // Return original text if either is null/empty
        if (AppModel.IsNullOrEmpty(text) || AppModel.IsNullOrEmpty(searchQuery)) {
            return text == null ? "" : text;
        }

        // Create spannable string for highlighting
        SpannableString spannableString = new SpannableString(text);

        // Convert both to lowercase for case-insensitive search
        String textLower = text.toLowerCase(Locale.getDefault());
        String queryLower = searchQuery.toLowerCase(Locale.getDefault());

        // Find and highlight all occurrences
        int startPos = 0;
        while ((startPos = textLower.indexOf(queryLower, startPos)) >= 0) {
            int endPos = startPos + queryLower.length();

            // Apply yellow background highlight
            spannableString.setSpan(
                new BackgroundColorSpan(Color.YELLOW),
                startPos,
                endPos,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            startPos = endPos;
        }

        return spannableString;
    }
}
