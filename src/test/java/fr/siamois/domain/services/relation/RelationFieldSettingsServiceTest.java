package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.*;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldOptions;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationFieldSettingsServiceTest {

    private static final long PROJECT = 7L;
    private static final long INSTITUTION = 3L;

    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private RelationDefinitionService definitionService;
    @Mock private RelationRuleService ruleService;
    @Mock private RelationDefinitionRepository definitionRepository;
    @Mock private RelationLinkRepository linkRepository;
    @Mock private RelationConfigAuthorizer configAuthorizer;
    @Mock private ActionUnitRepository actionUnitRepository;

    @InjectMocks private RelationFieldSettingsService service;

    private final PersonDTO caller = new PersonDTO();
    private final CustomFieldRelation field = relationField(152L, "Contient", false);

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setId(INSTITUTION);
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT);
        project.setCreatedByInstitution(institution);
        lenient().when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(tableFieldConfigService.findField(PROJECT, ConfigurableTable.UE, "Locus", "Contient"))
                .thenReturn(Optional.of(field));
    }

    @Test
    void aNewComplexFieldIsItsOwnQualifierAndLinksTheChosenKinds() {
        service.createRelationField(PROJECT, ConfigurableTable.UE, "Locus", "Contient", "d",
                new RelationFieldSettingsService.Draft(RelationKind.COMPLEX, false, true, "Contenu dans",
                        List.of(RelationEndKind.SPECIMEN)), caller);

        verify(tableFieldConfigService).createField(PROJECT, ConfigurableTable.UE, "Locus", "Contient",
                FieldType.RELATION, "d", FieldOptions.NONE);
        ArgumentCaptor<RelationDefinitionService.CreateRequest> request =
                ArgumentCaptor.forClass(RelationDefinitionService.CreateRequest.class);
        verify(definitionService).create(request.capture(), eq(PROJECT), eq(caller));
        assertThat(request.getValue().sourceFieldId()).isEqualTo(152L);
        assertThat(request.getValue().targetFieldLabel()).isEqualTo("Contenu dans");
        assertThat(request.getValue().qualifierFieldId()).isEqualTo(152L);
        assertThat(request.getValue().defaultRules().noCycle()).isTrue();
        assertThat(request.getValue().defaultRules().kinds()).containsExactly(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null),
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, null));
    }

    @Test
    void aSymmetricFieldHasNoMirrorAndListsEveryKindOnItsOneSide() {
        service.createRelationField(PROJECT, ConfigurableTable.UE, "Locus", "Contient", null,
                new RelationFieldSettingsService.Draft(RelationKind.SIMPLE, true, false, "ignoré",
                        List.of(RelationEndKind.RECORDING_UNIT, RelationEndKind.PHASE)), caller);

        ArgumentCaptor<RelationDefinitionService.CreateRequest> request =
                ArgumentCaptor.forClass(RelationDefinitionService.CreateRequest.class);
        verify(definitionService).create(request.capture(), eq(PROJECT), eq(caller));
        assertThat(request.getValue().targetFieldLabel()).isNull();
        assertThat(request.getValue().qualifierFieldId()).isNull();
        assertThat(request.getValue().defaultRules().kinds()).containsExactly(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null),
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.PHASE, null));
    }

    @Test
    void aSymmetricFieldOfAnyKindAcceptsAnything() {
        service.createRelationField(PROJECT, ConfigurableTable.UE, "Locus", "Contient", null,
                new RelationFieldSettingsService.Draft(RelationKind.SIMPLE, true, false, null, null), caller);

        ArgumentCaptor<RelationDefinitionService.CreateRequest> request =
                ArgumentCaptor.forClass(RelationDefinitionService.CreateRequest.class);
        verify(definitionService).create(request.capture(), eq(PROJECT), eq(caller));
        assertThat(request.getValue().defaultRules().kinds()).isEmpty();
    }

    @Test
    void theMirrorFieldSeesTheRelationFromItsSideWithTheProjectsInheritedRules() {
        RelationDefinition definition = definition(false);
        RelationRule platform = new RelationRule();
        RelationRuleSet rules = new RelationRuleSet(definition, platform, true, false, false,
                List.of(new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, null)),
                Map.of(), List.of());
        when(definitionRepository.findByField(153L)).thenReturn(Optional.of(definition));
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(rules);
        when(ruleService.ownRules(10L, RelationScope.project(INSTITUTION, PROJECT))).thenReturn(Optional.empty());
        when(linkRepository.countByDefinitionId(10L)).thenReturn(0L);
        when(configAuthorizer.canEditDefinition(false, PROJECT, caller)).thenReturn(true);

        RelationFieldSettingsService.View view = service.view(153L, PROJECT, caller).orElseThrow();

        assertThat(view.side()).isEqualTo(RelationSide.TARGET);
        assertThat(view.otherField().getId()).isEqualTo(152L);
        assertThat(view.ownRules()).isFalse();
        assertThat(view.rulesOrigin()).isEqualTo("platform");
        assertThat(view.identityEditable()).isTrue();
        assertThat(view.kindsOf(RelationSide.TARGET)).containsExactly(RelationEndKind.SPECIMEN);
        assertThat(view.complex()).isTrue();
    }

    @Test
    void aRelationWithLinksOrOfSystemFieldsKeepsItsIdentity() {
        RelationDefinition definition = definition(true);
        when(definitionRepository.findByField(152L)).thenReturn(Optional.of(definition));
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(RelationRuleSet.none(definition));
        when(ruleService.ownRules(eq(10L), any())).thenReturn(Optional.empty());
        when(linkRepository.countByDefinitionId(10L)).thenReturn(4L);

        RelationFieldSettingsService.View view = service.view(152L, PROJECT, caller).orElseThrow();

        assertThat(view.identityEditable()).isFalse();
        assertThat(view.system()).isTrue();
        assertThat(view.rulesOrigin()).isEqualTo("none");
        assertThat(service.view(999L, PROJECT, caller)).isEmpty();
    }

    @Test
    void identityRulesAndExclusionsGoThroughTheirServices() {
        RelationDefinition definition = definition(false);
        definition.setQualifierField(null);
        RelationDefinition other = definition(false);
        other.setId(11L);
        when(definitionRepository.findById(10L)).thenReturn(Optional.of(definition));
        when(definitionRepository.findAll()).thenReturn(List.of(definition, other));
        RelationRuleService.RuleSpec spec = new RelationRuleService.RuleSpec(true, false, false, List.of(), Map.of(), List.of());

        service.saveIdentity(10L, RelationKind.COMPLEX, false, PROJECT, caller);
        service.saveIdentity(10L, RelationKind.SIMPLE, true, PROJECT, caller);
        service.saveRules(10L, PROJECT, spec, caller);
        service.resetRules(10L, PROJECT, caller);

        verify(definitionService).updateIdentity(10L, new RelationDefinitionService.IdentityUpdate(
                RelationKind.COMPLEX, false, 152L), PROJECT, caller);
        verify(definitionService).updateIdentity(10L, new RelationDefinitionService.IdentityUpdate(
                RelationKind.SIMPLE, true, null), PROJECT, caller);
        verify(ruleService).save(10L, RelationScope.project(INSTITUTION, PROJECT), spec, caller);
        verify(ruleService).delete(10L, RelationScope.project(INSTITUTION, PROJECT), caller);
        assertThat(service.otherDefinitions(10L)).containsExactly(other);
    }

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void everyTableHoldsAKindOfRecord(ConfigurableTable table) {
        assertThat(RelationFieldSettingsService.kindOf(table)).isNotNull();
        assertThat(RelationFieldSettingsService.isRelationField(field)).isTrue();
    }

    private RelationDefinition definition(boolean system) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(10L);
        definition.setKind(RelationKind.COMPLEX);
        definition.setSourceField(system ? relationField(152L, "-", true) : field);
        definition.setTargetField(relationField(153L, "Contient (miroir)", false));
        definition.setQualifierField(field);
        return definition;
    }

    private static CustomFieldRelation relationField(long id, String label, boolean system) {
        CustomFieldRelation field = new CustomFieldRelation();
        field.setId(id);
        field.setLabel(label);
        field.setIsSystemField(system);
        return field;
    }
}
