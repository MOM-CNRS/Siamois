package fr.siamois.domain.models.exceptions.database;

public class DatabaseDataInitException extends Exception {

    public DatabaseDataInitException(String message) {
        super(message);
    }

    public DatabaseDataInitException(String message, Throwable cause) {
        super(message, cause);
    }
}
