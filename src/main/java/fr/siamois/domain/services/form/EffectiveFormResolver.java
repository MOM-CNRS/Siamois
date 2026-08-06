package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes a base system form with a project's {@link TableFieldConfigService} configuration:
 * inactive system fields removed, active additional fields appended.
 * <p>
 * This is the single implementation of the logic single-item panels (e.g.
 * {@code RecordingUnitPanel}) and the OpenAPI services both rely on, so a project's configured
 * form looks the same everywhere it's resolved.
 */
@Service
@RequiredArgsConstructor
public class EffectiveFormResolver {

    private final TableFieldConfigService tableFieldConfigService;

    /**
     * @param baseForm      the system form to start from, left untouched
     * @param projectId     the project (action unit) the configuration is scoped to
     * @param table         the table the type belongs to
     * @param typeConceptId the type's concept id, or {@code null} for the default configuration
     * @return {@code baseForm} minus its inactive system fields, plus the project's active
     * additional fields for that type
     */
    public FormUiDto resolveEffectiveForm(FormUiDto baseForm, Long projectId, ConfigurableTable table, Long typeConceptId) {
        Set<String> inactive = tableFieldConfigService.getFieldsConfig(projectId, table, typeConceptId).getFields().stream()
                .filter(field -> !field.isActive())
                .map(TypeFieldFormConfig::getValueBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        FormUiDto base = CustomFormComposer.withoutFields(baseForm, inactive);

        List<CustomColUiDto> additional = tableFieldConfigService.getActiveAdditionalFields(projectId, table, typeConceptId).stream()
                .map(field -> new CustomColUiDto.Builder().field(field).build())
                .toList();
        return CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additional);
    }
}
