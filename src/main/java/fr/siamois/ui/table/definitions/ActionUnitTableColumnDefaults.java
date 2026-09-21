package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.actionunit.form.ActionUnitForm;
import fr.siamois.domain.models.form.customfield.CustomField;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordre et visibilité par défaut des colonnes « documentaires / administratives » de la liste des projets.
 *
 * <p>Source unique de vérité, consommée à la fois par {@link ActionUnitTableDefinitionFactory} (table JSF)
 * et par l'API {@code GET /api/v1/organizations/{id}/project-types} (table React). Une constante TypeScript
 * dupliquant cette liste dériverait en quelques semaines ; le front la lit donc depuis le serveur.</p>
 *
 * <p>Ne contient <strong>pas</strong> les trois colonnes structurelles — chip identifiant, nom, compteur
 * d'unités d'enregistrement : elles ne sont pas togglables, ne viennent pas du catalogue de champs et sont
 * construites à la main de chaque côté.</p>
 */
public final class ActionUnitTableColumnDefaults {

    private ActionUnitTableColumnDefaults() {}

    /**
     * @param columnId   identifiant de colonne côté table (inchangé depuis la version JSF)
     * @param headerKey  clé i18n de l'en-tête
     * @param field      champ système du formulaire de détail ({@code ActionUnit.DETAILS_FORM}) — son id est
     *                   celui que le catalogue de champs de l'API expose
     * @param visible    visible par défaut (sinon disponible dans le sélecteur de colonnes)
     */
    public record ColumnDefault(String columnId, String headerKey, CustomField field, boolean visible) {
        public String fieldId() {
            return field.getId() == null ? null : String.valueOf(field.getId());
        }
    }

    private static final List<ColumnDefault> COLUMNS = List.of(
            new ColumnDefault("status", "actionunit.field.status", ActionUnitForm.STATUS_FIELD, true),
            new ColumnDefault("oaCode", "actionunit.field.oaCode", ActionUnitForm.OA_CODE_FIELD, true),
            new ColumnDefault("mainLocation", "common.label.mainLocation", ActionUnitForm.MAIN_LOCATION_FIELD, true),
            new ColumnDefault("openingRate", "actionunit.field.openingRate", ActionUnitForm.OPENING_RATE_FIELD, true),
            new ColumnDefault("periods", "actionunit.field.periods", ActionUnitForm.PERIODS_FIELD, true),
            new ColumnDefault("subjects", "actionunit.field.subjects", ActionUnitForm.SUBJECTS_FIELD, true),
            new ColumnDefault("scientificManager", "actionunit.field.scientificManager", ActionUnitForm.SCIENTIFIC_MANAGER_FIELD, true),
            new ColumnDefault("prescriptionOrderNumber", "actionunit.field.prescriptionOrderNumber", ActionUnitForm.PRESCRIPTION_ORDER_NUMBER_FIELD, false),
            new ColumnDefault("prescriptionOrderDate", "actionunit.field.prescriptionOrderDate", ActionUnitForm.PRESCRIPTION_ORDER_DATE_FIELD, false),
            new ColumnDefault("hostStructure", "actionunit.field.hostStructure", ActionUnitForm.HOST_STRUCTURE_FIELD, false),
            new ColumnDefault("developer", "actionunit.field.developer", ActionUnitForm.DEVELOPER_FIELD, false),
            new ColumnDefault("scientificNotice", "actionunit.field.scientificNotice", ActionUnitForm.SCIENTIFIC_NOTICE_FIELD, false),
            new ColumnDefault("comments", "common.field.comments", ActionUnitForm.COMMENTS_FIELD, false),
            new ColumnDefault("system", "actionunit.field.system", ActionUnitForm.SYSTEM_FIELD, false),
            new ColumnDefault("fieldStatus", "actionunit.field.fieldStatus", ActionUnitForm.FIELD_STATUS_FIELD, false),
            new ColumnDefault("zmin", "actionunit.field.zmin", ActionUnitForm.ZMIN_FIELD, false),
            new ColumnDefault("zmax", "actionunit.field.zmax", ActionUnitForm.ZMAX_FIELD, false),
            new ColumnDefault("designationOrderNumber", "actionunit.field.designationOrderNumber", ActionUnitForm.DESIGNATION_ORDER_NUMBER_FIELD, false),
            new ColumnDefault("designationOrderDate", "actionunit.field.designationOrderDate", ActionUnitForm.DESIGNATION_ORDER_DATE_FIELD, false),
            new ColumnDefault("prescribedArea", "actionunit.field.prescribedArea", ActionUnitForm.PRESCRIBED_AREA_FIELD, false),
            new ColumnDefault("excavatedArea", "actionunit.field.excavatedArea", ActionUnitForm.EXCAVATED_AREA_FIELD, false),
            new ColumnDefault("accessibleArea", "actionunit.field.accessibleArea", ActionUnitForm.ACCESSIBLE_AREA_FIELD, false),
            new ColumnDefault("developmentNature", "actionunit.field.developmentNature", ActionUnitForm.DEVELOPMENT_NATURE_FIELD, false),
            new ColumnDefault("volumeCount", "actionunit.field.volumeCount", ActionUnitForm.VOLUME_COUNT_FIELD, false),
            new ColumnDefault("pageCount", "actionunit.field.pageCount", ActionUnitForm.PAGE_COUNT_FIELD, false),
            new ColumnDefault("figureCount", "actionunit.field.figureCount", ActionUnitForm.FIGURE_COUNT_FIELD, false),
            new ColumnDefault("appendixCount", "actionunit.field.appendixCount", ActionUnitForm.APPENDIX_COUNT_FIELD, false)
    );

    public static List<ColumnDefault> columns() {
        return COLUMNS;
    }

    /**
     * Ids des champs visibles par défaut — ce que {@code GET /api/v1/projects?fields=default} projette.
     */
    public static Set<String> defaultVisibleFieldIds() {
        return COLUMNS.stream()
                .filter(ColumnDefault::visible)
                .map(ColumnDefault::fieldId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
