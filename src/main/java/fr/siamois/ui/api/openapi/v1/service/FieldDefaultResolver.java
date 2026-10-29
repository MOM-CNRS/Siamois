package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.FieldDefault;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The defaults of a form configuration, as the answers a new entity starts with ({@code fieldId -> raw
 * value}, the shape a request carries them in). A default is stored as concept ids with no foreign key, and
 * the list it came from may have changed since, so each concept is checked here: it must still exist and
 * still be offered. One that is not is dropped (and logged) — a stale default never fails a creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldDefaultResolver {

    private final ConceptRepository conceptRepository;
    private final FieldConfigurationService fieldConfigurationService;

    /**
     * @param form          the entity's effective form, whose columns carry the defaults
     * @param projectId     the project the entity is created in
     * @param typeConceptId the entity's type, which picks the vocabulary configuration the default is checked against
     * @param answered      the keys the request already answers: they keep their own value, never a default
     * @return field id (as the request keys it) to its default, a single id or a list of ids for a multiple choice
     */
    public Map<String, Object> defaultsOf(@Nullable FormUiDto form, @Nullable Long projectId, @Nullable Long typeConceptId,
                                          Set<String> answered) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (form == null || form.getLayout() == null) return out;
        form.getLayout().stream()
                .filter(panel -> panel != null && panel.getRows() != null)
                .flatMap(panel -> panel.getRows().stream())
                .filter(row -> row.getColumns() != null)
                .flatMap(row -> row.getColumns().stream())
                .filter(col -> col.getField() != null && col.getField().getId() != null && !col.isReadOnly())
                .forEach(col -> {
                    String key = String.valueOf(col.getField().getId());
                    if (answered.contains(key) || out.containsKey(key)) return;
                    Object value = valueOf(col, projectId, typeConceptId);
                    if (value != null) out.put(key, value);
                });
        return out;
    }

    @Nullable
    private Object valueOf(CustomColUiDto col, @Nullable Long projectId, @Nullable Long typeConceptId) {
        FieldDefault defaultValue = col.getDefaultValue();
        if (defaultValue == null || defaultValue.isEmpty()) return null;
        CustomField field = col.getField();
        List<String> kept = new ArrayList<>();
        for (String id : defaultValue.conceptIds()) {
            if (isStillOffered(field, id, projectId, typeConceptId)) kept.add(id);
        }
        if (kept.isEmpty()) return null;
        return isMultiple(field) ? kept : kept.get(0);
    }

    private boolean isStillOffered(CustomField field, String conceptId, @Nullable Long projectId, @Nullable Long typeConceptId) {
        long id;
        try {
            id = Long.parseLong(conceptId);
        } catch (NumberFormatException e) {
            log.warn("Default of field {} ignored: {} is not a concept id", field.getId(), conceptId);
            return false;
        }
        if (!conceptRepository.existsById(id)) {
            log.warn("Default of field {} ignored: concept {} no longer exists", field.getId(), id);
            return false;
        }
        if (!(Hibernate.unproxy(field) instanceof CustomFieldConcept conceptField)) return true;
        FieldVocabularyConfig vocabulary = fieldConfigurationService.describeVocabulary(conceptField, projectId, typeConceptId);
        if (vocabulary == null) return true;
        ConceptState state = vocabulary.states().get(id);
        boolean offered = vocabulary.mode() == VocabularyMode.FROZEN ? state == ConceptState.ENABLED : state != ConceptState.DISABLED;
        if (!offered) log.warn("Default of field {} ignored: concept {} is no longer offered", field.getId(), id);
        return offered;
    }

    private static boolean isMultiple(CustomField field) {
        Object f = Hibernate.unproxy(field);
        return f instanceof CustomFieldSelectMultipleFromFieldCode || f instanceof CustomFieldSelectMultiple;
    }
}
