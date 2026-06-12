package fr.siamois.infrastructure.database.initializer.seeder;

import java.util.function.Supplier;

public class SeederUtils {

    private SeederUtils() {
        /* This utility class should not be instantiated */
    }


    public static <T> T field(String name, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new IllegalStateException("Champ '" + name + "' : " + e.getMessage(), e);
        }
    }
}
