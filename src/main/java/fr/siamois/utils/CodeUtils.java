package fr.siamois.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Utility class to generate random codes.
 * The generated code will be a string of random characters (A-Z, a-z, 0-9, and '-') of specified length.
 * The first and last characters will not be '-' to ensure valid formatting.
 */
public class CodeUtils {

    private CodeUtils() {
    }

    private static final Random RANDOM = new SecureRandom();

    private static char generateRandomChar() {
        String allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-";
        int randomIndex = RANDOM.nextInt(0, allowedChars.length());
        return allowedChars.charAt(randomIndex);
    }

    /**
     * Generate a random code of specified length.
     *
     * @param maxLength the length of the code to generate
     * @return a random code as a String
     */
    public static String generateCode(int maxLength) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < maxLength; i++) {
            code.append(generateRandomChar());
        }

        while (code.charAt(maxLength - 1) == '-' || code.charAt(0) == '-') {
            code.setCharAt(0, generateRandomChar());
            code.setCharAt(maxLength - 1, generateRandomChar());
        }

        return code.toString();
    }

}
