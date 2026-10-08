package fr.siamois.domain.services.placesource;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The place sources a field is configured with: the {@code placeSources} of the rules of its column
 * in the form it is edited in. The project form is fixed in code for now; when the project's
 * configuration is per institution and type, this is the one place that reads the effective form
 * (project × type) instead — callers already say which project and type the field is edited for.
 */
@Component
public class PlaceSourceConfigResolver {

    /**
     * @param fieldId   the place field
     * @param projectId the project being edited, when there is one
     * @param typeId    the type concept of the project, when it has one
     * @return the field's sources, none when it has none or does not exist
     */
    public List<PlaceSourceSpec> forField(long fieldId, @Nullable String projectId, @Nullable String typeId) {
        FormUiDto form = ActionUnit.DETAILS_FORM;
        if (form == null || form.getLayout() == null) return List.of();
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .filter(column -> column.getField() != null && Long.valueOf(fieldId).equals(column.getField().getId()))
                .map(CustomColUiDto::getRules)
                .filter(rules -> rules != null && !rules.placeSources().isEmpty())
                .findFirst()
                .map(rules -> rules.placeSources())
                .orElse(List.of());
    }
}
