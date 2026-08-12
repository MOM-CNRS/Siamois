package fr.siamois.utils;

import org.springframework.lang.NonNull;

public final class ArkUtils {

    private ArkUtils() {
        throw new UnsupportedOperationException("ArkUtils should never be instantiated");
    }

    public static String extractArkOf(@NonNull String arkAndWhatFollows) {
        int end = arkAndWhatFollows.length();
        for (char separator : new char[]{'?', '#'}) {
            int index = arkAndWhatFollows.indexOf(separator);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return arkAndWhatFollows.substring(0, end);
    }

}
