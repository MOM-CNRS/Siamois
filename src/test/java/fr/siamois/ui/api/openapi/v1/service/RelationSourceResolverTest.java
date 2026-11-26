package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.relation.RelationFieldIndex;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.infrastructure.database.repositories.relation.RelationSource;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Which relation a field reads: a system relation field, else the side of a configurable relation. */
class RelationSourceResolverTest {

    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationSourceResolver resolver = new RelationSourceResolver(new RelationFieldIndex(definitionRepository));

    @Test
    void readsEachSideOfAConfigurableRelationAndLeavesOtherFieldsOut() {
        when(definitionRepository.findAll()).thenReturn(List.of(order(151L, 152L)));

        Map<String, RelationSource> sources = resolver.configurable(RecordingUnit.class, List.of("151", "152", "fullIdentifier"));

        assertThat(sources.get("151")).isEqualTo(new DefinitionRelation(1L, RelationSide.SOURCE, false, true,
                RelationEndKind.RECORDING_UNIT));
        assertThat(sources.get("152")).isEqualTo(new DefinitionRelation(1L, RelationSide.TARGET, false, true,
                RelationEndKind.RECORDING_UNIT));
        assertThat(sources).hasSize(2);
    }

    @Test
    void aSystemRelationFieldIsReadAsItAlwaysWas() {
        String parents = String.valueOf(SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "parents").getId());

        assertThat(resolver.of(RecordingUnit.class, parents)).contains(RelationField.RECORDING_UNIT_PARENTS);
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void theIndexIsReadOncePerLoadNotPerPage() {
        when(definitionRepository.findAll()).thenReturn(List.of(order(151L, 152L)));

        resolver.configurable(RecordingUnit.class, List.of("151"));
        resolver.configurable(RecordingUnit.class, List.of("152", "7"));

        verify(definitionRepository, times(1)).findAll();
    }

    @Test
    void theAnswersOfAPageReadTheConfigurableRelationFieldsTheyProject() {
        RelationFieldService relations = mock(RelationFieldService.class);
        RelationSourceResolver sources = mock(RelationSourceResolver.class);
        DefinitionRelation posterior = new DefinitionRelation(1L, RelationSide.SOURCE, false, true,
                RelationEndKind.RECORDING_UNIT);
        when(sources.configurable(eq(RecordingUnit.class), any())).thenReturn(Map.of("151", posterior));
        MultiValue preview = MultiValue.complete(List.of());
        when(relations.previews(eq(posterior), any(), anyInt(), anyString(), any())).thenReturn(Map.of(42L, preview));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("151", null);

        Map<String, Object> shaped = new MultiValueAnswers(relations, sources)
                .shape(RecordingUnit.class, Map.of(42L, row), List.of("151"), 5, "fr").get(42L);

        assertThat(shaped).containsEntry("151", preview);
    }

    private static RelationDefinition order(long source, long target) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(1L);
        definition.setKind(RelationKind.COMPLEX);
        CustomFieldRelation sourceField = new CustomFieldRelation();
        sourceField.setId(source);
        CustomFieldRelation targetField = new CustomFieldRelation();
        targetField.setId(target);
        definition.setSourceField(sourceField);
        definition.setTargetField(targetField);
        return definition;
    }
}
