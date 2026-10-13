package fr.siamois.domain.models.settings.tableconfig;

/**
 * What a field is once defined from the configuration screen: its name, its type, its description
 * and the options its type carries.
 */
public record FieldDefinition(String name, FieldType type, String description, FieldOptions options) {

    public FieldDefinition {
        options = options == null ? FieldOptions.NONE : options;
    }
}
