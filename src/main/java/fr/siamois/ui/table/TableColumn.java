package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.FieldColumn;
import lombok.Builder;
import lombok.Data;

/**
 * Colonne d'un tableau de RecordingUnit (ou autre entité).
 * Peut être liée à un CustomField pour réutiliser les mêmes
 * composants d'édition que dans les panels.
 */
@Data
@Builder
public class TableColumn implements FieldColumn {

    /**
     * Identifiant technique de la colonne (utilisé pour la vue, tri, etc.).
     */
    private String id;

    /**
     * Code i18n du header (ex: "common.entity.recordingunit.identifier").
     */
    private String headerKey;

    /**
     * Champ métier associé à cette colonne.
     * Peut être null pour des colonnes purement UI (actions, etc.).
     */
    private CustomField field;

    /**
     * Colonne requise pour la saisie (vision UI de la table).
     */
    private boolean required;

    /**
     * Colonne en lecture seule dans la table.
     */
    private boolean readOnly;

    /**
     * Colonne triable ?
     */
    private boolean sortable;

    /**
     * Colonne filtrable ?
     */
    private boolean filterable;

    /**
     * Type de filtre (texte, date, select, ...).
     */
    private String filterType;

    /**
     * Classe CSS optionnelle.
     */
    private String styleClass;

    /**
     * Largeur CSS optionnelle (ex: "150px").
     */
    private String width;

    /**
     * Colonne visible par défaut ?
     */
    private boolean visible;

    // Implémentation FieldColumn

    @Override
    public CustomField getField() {
        return field;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }
}
