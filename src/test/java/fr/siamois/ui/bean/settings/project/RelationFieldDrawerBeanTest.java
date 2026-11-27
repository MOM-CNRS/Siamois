package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.relation.*;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.*;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelationFieldDrawerBeanTest {

    private static final long PROJECT = 7L;

    private final RelationFieldSettingsService settings = mock(RelationFieldSettingsService.class);
    private final FieldConfigurationService vocabulary = mock(FieldConfigurationService.class);
    private final LabelService labelService = mock(LabelService.class);
    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);
    private final PersonDTO caller = new PersonDTO();
    private RelationFieldDrawerBean bean;
    private RelationDefinition definition;

    @BeforeEach
    void setUp() {
        ProjectTableFieldSettingsBean settingsBean = mock(ProjectTableFieldSettingsBean.class);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(PROJECT);
        when(settingsBean.getProject()).thenReturn(project);
        SessionSettingsBean session = mock(SessionSettingsBean.class);
        when(session.getAuthenticatedUser()).thenReturn(caller);
        LangBean lang = mock(LangBean.class);
        when(lang.msg(anyString())).thenAnswer(call -> call.getArgument(0));
        when(lang.msg(anyString(), any(Object[].class))).thenAnswer(call -> call.getArgument(0));
        when(lang.getLanguageCode()).thenReturn("fr");
        bean = new RelationFieldDrawerBean(settings, vocabulary, labelService, conceptRepository, settingsBean, session, lang);

        definition = new RelationDefinition();
        definition.setId(10L);
        definition.setKind(RelationKind.COMPLEX);
        definition.setSourceField(field(152L, "Contient"));
        definition.setTargetField(field(153L, "Contenu dans"));
    }

    private static CustomFieldRelation field(long id, String label) {
        CustomFieldRelation field = new CustomFieldRelation();
        field.setId(id);
        field.setLabel(label);
        field.setIsSystemField(false);
        return field;
    }

    private RelationRuleSet rules(boolean own) {
        RelationRule rule = new RelationRule();
        return new RelationRuleSet(definition, own ? rule : null, false, true, false, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null),
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, 900L)),
                Map.of(201L, 301L), List.of(11L));
    }

    private void open(RelationSide side, boolean editable, long links) {
        long fieldId = side == RelationSide.SOURCE ? 152L : 153L;
        RelationFieldSettingsService.View view = new RelationFieldSettingsService.View(definition, side,
                side == RelationSide.SOURCE ? definition.getTargetField() : definition.getSourceField(), links,
                rules(false), false, "platform", editable, false);
        when(settings.view(fieldId, PROJECT, caller)).thenReturn(Optional.of(view));
        bean.openFor(TypeFieldFormConfig.builder().id(fieldId).type(FieldType.RELATION).build());
    }

    @Test
    void anotherFieldClosesTheRelationSections() {
        bean.openFor(TypeFieldFormConfig.builder().id(1L).type(FieldType.TEXT).build());
        bean.openFor(null);

        assertThat(bean.isOpen()).isFalse();
        verifyNoInteractions(settings);
    }

    @Test
    void theSourceFieldShowsItsRulesFromItsSide() {
        open(RelationSide.SOURCE, true, 0);

        assertThat(bean.isOpen()).isTrue();
        assertThat(bean.isComplex()).isTrue();
        assertThat(bean.isOwnUnique()).isTrue();
        assertThat(bean.isOtherUnique()).isFalse();
        assertThat(bean.getOtherKinds()).containsExactly("SPECIMEN");
        assertThat(bean.getExclusions()).containsExactly("11");
        assertThat(bean.getPairs()).extracting(RelationFieldDrawerBean.PairDraft::getConcept).containsExactly("201");
        assertThat(bean.getOtherFieldLabel()).isEqualTo("Contenu dans");
        assertThat(bean.getRulesOriginLabel()).isEqualTo("relation.rules.inherited.platform");
        assertThat(bean.isIdentityEditable()).isTrue();
    }

    @Test
    void savingUnchangedRulesKeepsInheritingThem() {
        open(RelationSide.SOURCE, true, 0);

        bean.saveIfOpen();

        verify(settings, never()).saveRules(anyLong(), anyLong(), any(), any());
        verify(settings, never()).saveIdentity(anyLong(), any(), anyBoolean(), anyLong(), any());
    }

    @Test
    void changedRulesAreSavedForTheProjectKeepingTheSubtypeAndTheOwnSide() {
        open(RelationSide.SOURCE, true, 0);
        bean.setOtherUnique(true);
        bean.setNoCycle(true);

        bean.saveIfOpen();

        ArgumentCaptor<RelationRuleService.RuleSpec> spec = ArgumentCaptor.forClass(RelationRuleService.RuleSpec.class);
        verify(settings).saveRules(eq(10L), eq(PROJECT), spec.capture(), eq(caller));
        assertThat(spec.getValue().targetUnique()).isTrue();
        assertThat(spec.getValue().noCycle()).isTrue();
        assertThat(spec.getValue().kinds()).containsExactlyInAnyOrder(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null),
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, 900L));
        assertThat(spec.getValue().mirrorByConcept()).containsEntry(201L, 301L);
    }

    @Test
    void fromTheMirrorSideThePairsAndUniquenessAreTurnedRound() {
        open(RelationSide.TARGET, false, 3);

        assertThat(bean.getPairs().get(0).getConcept()).isEqualTo("201");
        bean.setOwnUnique(true);
        RelationRuleService.RuleSpec spec = bean.draftSpec();

        assertThat(spec.targetUnique()).isTrue();
        assertThat(spec.mirrorByConcept()).containsEntry(301L, 201L);
        assertThat(bean.isIdentityEditable()).isFalse();
        assertThat(bean.getIdentityHint()).isEqualTo("relation.identityFrozen");
    }

    @Test
    void anIdentityChangeIsSavedWhileTheRelationHasNoLink() {
        open(RelationSide.SOURCE, true, 0);
        bean.setIdentityKind(RelationFieldDrawerBean.SIMPLE);
        bean.setPairs(new java.util.ArrayList<>());

        bean.saveIfOpen();

        verify(settings).saveIdentity(10L, RelationKind.SIMPLE, false, PROJECT, caller);
    }

    @Test
    void aRefusalShowsTheBrokenRulesAndTheirLinks() {
        open(RelationSide.SOURCE, true, 0);
        bean.setOtherUnique(true);
        List<Long> many = java.util.stream.LongStream.rangeClosed(1, 25).boxed().toList();
        when(settings.saveRules(anyLong(), anyLong(), any(), any())).thenThrow(new RelationLinkException(
                RelationLinkException.Reason.INVALID_CONFIGURATION, "refusé",
                List.of(new RuleViolation(RuleViolation.Rule.SINGLE_CHOICE, true, "plusieurs liens", many))));

        bean.saveIfOpen();

        assertThat(bean.getErrorMessage()).isEqualTo("relation.rules.refused");
        assertThat(bean.getViolations()).singleElement().asString().contains("plusieurs liens").contains("20")
                .doesNotContain("21,");
    }

    @Test
    void resettingDropsTheProjectsOwnRules() {
        open(RelationSide.SOURCE, true, 0);
        doThrow(new RelationLinkException(RelationLinkException.Reason.INVALID_CONFIGURATION, "non"))
                .when(settings).resetRules(10L, PROJECT, caller);

        bean.resetRules();

        assertThat(bean.getErrorMessage()).isEqualTo("non");
    }

    @Test
    void aNewFieldIsCreatedWithItsMirrorNamedAfterIt() {
        bean.startCreation();
        bean.setDraftKind(RelationFieldDrawerBean.COMPLEX);
        bean.setDraftOtherKinds(List.of("SPECIMEN"));

        bean.createField(ConfigurableTable.UE, "Locus", "Contient", null);

        ArgumentCaptor<RelationFieldSettingsService.Draft> draft = ArgumentCaptor.forClass(RelationFieldSettingsService.Draft.class);
        verify(settings).createRelationField(eq(PROJECT), eq(ConfigurableTable.UE), eq("Locus"), eq("Contient"),
                eq(null), draft.capture(), eq(caller));
        assertThat(draft.getValue().kind()).isEqualTo(RelationKind.COMPLEX);
        assertThat(draft.getValue().mirrorLabel()).isEqualTo("Contient relation.mirror.suffix");
        assertThat(draft.getValue().otherKinds()).containsExactly(RelationEndKind.SPECIMEN);
        assertThat(bean.isComplex()).isTrue();
        assertThat(bean.getKindOptions()).hasSize(RelationEndKind.values().length - 1);
    }

    @Test
    void aPairedConceptTheFieldNoLongerOffersIsStillShown() throws Exception {
        open(RelationSide.SOURCE, true, 0);
        Concept concept = new Concept();
        concept.setId(201L);
        when(conceptRepository.findById(201L)).thenReturn(Optional.of(concept));
        fr.siamois.domain.models.vocabulary.label.ConceptLabel label = mock(fr.siamois.domain.models.vocabulary.label.ConceptLabel.class);
        when(label.getLabel()).thenReturn("recouvre");
        when(labelService.findLabelOf(concept, "fr")).thenReturn(label);
        when(vocabulary.fetchAutocomplete(any(fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept.class), eq(""), eq(PROJECT)))
                .thenThrow(new fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException("none"));

        assertThat(bean.getOwnQualifiers()).extracting(RelationFieldDrawerBean.Choice::getLabel).containsExactly("recouvre");
        assertThat(bean.getExclusionOptions()).isEmpty();
    }

    @Test
    void rulesAreComparedWhateverTheOrderOfTheirLists() {
        var a = new RelationRuleService.RuleSpec(false, false, false, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.PHASE, null),
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, null)), Map.of(), List.of(1L, 2L));
        var b = new RelationRuleService.RuleSpec(false, false, false, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, null),
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.PHASE, null)), Map.of(), List.of(2L, 1L));

        assertThat(RelationFieldDrawerBean.sameRules(a, b)).isTrue();
        assertThat(RelationFieldDrawerBean.sameRules(a, null)).isFalse();
        assertThat(RelationFieldDrawerBean.isRelationType(FieldType.SELECT_MULTIPLE_STRATIGRAPHY)).isTrue();
    }
}
