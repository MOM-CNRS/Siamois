package fr.siamois.domain.services.export;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for accessing nested properties in objects using dot notation paths
 */
public class PropertyPathUtil {
    
    /**
     * Gets a value from an object using a dot-notation path
     * Example: "phase.libelle" would get phase.libelle from an object
     * @param obj The object to extract from
     * @param path The dot-notation path (e.g., "phase.libelle")
     * @return The value at the path, or null if not found
     */
    public static Object getValue(Object obj, String path) {
        if (obj == null || path == null || path.isEmpty()) {
            return null;
        }
        
        String[] parts = path.split("\\.");
        Object current = obj;
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            if (current == null) {
                return null;
            }
            
            // Try to get the value using reflection
            current = getValueForProperty(current, part);
            
            // If we've reached the last part and still have a value, return it
            if (i == parts.length - 1) {
                return current;
            }
        }
        
        return current;
    }
    
    /**
     * Gets the value for a single property using reflection
     */
    private static Object getValueForProperty(Object obj, String propertyName) {
        if (obj == null || propertyName == null) {
            return null;
        }
        
        // Try getter method first (e.g., getLibelle())
        try {
            String getterName = "get" + capitalize(propertyName);
            Method method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (Exception ignored) {
            // Try direct field access
            try {
                Field field = obj.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ignored2) {
                // Return null if nothing works
                return null;
            }
        }
    }
    
    /**
     * Capitalizes the first letter of a string
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * Extracts the last part of a dot-notation path
     * Example: "phase.libelle" -> "libelle"
     */
    public static String getLastPart(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        List<String> parts = Arrays.asList(path.split("\\."));
        return parts.get(parts.size() - 1);
    }
}