package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.services.form.layout.FormLayoutComposer;
import fr.siamois.domain.services.form.layout.FormLayoutSeeds;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Composes the form of a (project, table, type) from its configuration. When the type's
 * configuration holds a stored layout ({@link FormLayoutService}), that layout is the form. Until
 * one is stored, the table's initial layout ({@link FormLayoutSeeds}) is composed with the project's
 * {@link TableFieldConfigService} configuration: inactive system fields removed, mandatory fields
 * marked required, active additional fields appended.
 * <p>
 * This is the single implementation of the logic single-item panels (e.g.
 * {@code RecordingUnitPanel}) and the OpenAPI services both rely on, so a project's configured
 * form looks the same everywhere it's resolved.
 */
@Service
@RequiredArgsConstructor
public class EffectiveFormResolver {

    private final TableFieldConfigService tableFieldConfigService;
    private final FormLayoutService formLayoutService;
    private final FormLayoutSeeds formLayoutSeeds;

    /**
     * @param projectId     the project (action unit) the configuration is scoped to
     * @param table         the table the type belongs to
     * @param typeConceptId the type's concept id, or {@code null} for the default configuration
     * @return {@code baseForm} minus its inactive system fields, plus the project's active
     * additional fields for that type
     */
    public FormUiDto resolveEffectiveForm(Long projectId, ConfigurableTable table, Long typeConceptId) {
        if (projectId == null) {
            return FormLayoutComposer.compose(formLayoutSeeds.layoutOf(table), table);
        }
        Optional<FormLayout> stored = formLayoutService.storedLayout(projectId, table, typeConceptId);
        if (stored.isPresent()) {
            return FormLayoutComposer.compose(stored.get(), table);
        }
        FormUiDto baseForm = FormLayoutComposer.compose(formLayoutSeeds.layoutOf(table), table);
        List<TypeFieldFormConfig> configs = tableFieldConfigService.getFieldsConfig(projectId, table, typeConceptId).getFields();
        Set<String> inactive = configs.stream()
                .filter(field -> !field.isActive())
                .map(TypeFieldFormConfig::getValueBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Mandatory per type: matched by valueBinding when the config carries one, else by name (the
        // field's label — the same key TableFieldConfigService#findField uses).
        List<TypeFieldFormConfig> mandatory = configs.stream()
                .filter(field -> field.isActive() && field.isMandatory())
                .toList();
        Set<String> mandatoryBindings = mandatory.stream()
                .map(TypeFieldFormConfig::getValueBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> mandatoryNames = mandatory.stream()
                .filter(field -> field.getValueBinding() == null)
                .map(TypeFieldFormConfig::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Predicate<CustomField> isMandatory = field -> field.getValueBinding() != null
                ? mandatoryBindings.contains(field.getValueBinding())
                : mandatoryNames.contains(field.getLabel());

        FormUiDto base = CustomFormComposer.withoutFields(baseForm, inactive);
        if (!mandatory.isEmpty()) {
            base = CustomFormComposer.withRequiredFields(base, isMandatory);
        }

        // ColumnWidth.STANDARD, not left unset: this column reaches the same
        // FormUiDtoLayoutJson.serialize as ActionUnitDetailsForm/RecordingUnitDetailsForm's own
        // columns (both now built with .width(...), not .className(...)), and the React side
        // assumes every column carries a width — leaving this one without any caused
        // toPrimeFlexClass(col.width) to crash on `undefined`.
        List<CustomColUiDto> additional = tableFieldConfigService.getActiveAdditionalFields(projectId, table, typeConceptId).stream()
                .map(field -> new CustomColUiDto.Builder().field(field).width(ColumnWidth.STANDARD)
                        .isRequired(isMandatory.test(field)).build())
                .toList();
        return CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additional);
    }
}
