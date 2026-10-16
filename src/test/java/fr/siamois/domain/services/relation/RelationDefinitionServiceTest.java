package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.initializer.seeder.RelationDefinitionSeeder;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationDefinitionServiceTest {

    @Mock RelationDefinitionRepository definitionRepository;
    @Mock RelationLinkRepository linkRepository;
    @Mock RelationLinkService linkService;
    @Mock RelationTypeMappingRepository mappingRepository;
    @Mock CustomFieldRepository customFieldRepository;
    @Mock FieldFormConfigRepository fieldFormConfigRepository;
    @Mock FormConfigRepository formConfigRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock VocabularyRepository vocabularyRepository;
    @Mock ConceptRepository conceptRepository;
    @Mock LabelService labelService;
    @Mock RelationDefinitionSeeder seeder;
    @Mock ProfilePermissionService profilePermissionService;
    @Mock FieldConfigurationService fieldConfigurationService;

    private RelationDefinitionService service;
    private UserInfo userInfo;

    @BeforeEach
    void setUp() {
        service = new RelationDefinitionService(definitionRepository, linkRepository, linkService, mappingRepository,
                customFieldRepository, fieldFormConfigRepository, formConfigRepository, institutionRepository,
                vocabularyRepository, conceptRepository, labelService, seeder, profilePermissionService,
                fieldConfigurationService);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(7L);
        userInfo = new UserInfo(institution, new PersonDTO(), "fr");
    }

    private void allowManage() {
        when(profilePermissionService.hasInstancePermission(any(), anyString())).thenReturn(true);
    }

    private void stubCreation() {
        Institution institution = new Institution();
        institution.setId(7L);
        when(institutionRepository.findById(7L)).thenReturn(Optional.of(institution));
        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(anyString(), anyString()))
                .thenReturn(Optional.of(new Vocabulary()));
        when(conceptRepository.save(any(Concept.class))).thenAnswer(i -> i.getArgument(0));
        when(definitionRepository.save(any(RelationDefinition.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void create_withMirror_makesOneFieldPerSideBoundToTheDefinition() {
        allowManage();
        stubCreation();

        RelationDefinition created = service.create(userInfo, new RelationDefinitionService.Draft(
                "Parent", "Enfants", RelationMirror.OTHER, RelationEntityKind.RECORDING_UNIT, List.of(),
                RelationEntityKind.RECORDING_UNIT, List.of(), false));

        assertThat(created.getCode()).isEqualTo("INST7.parent");
        assertThat(created.isSystem()).isFalse();
        assertThat(created.kindsOf(RelationSide.SOURCE)).hasSize(1);
        ArgumentCaptor<CustomField> fields = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, org.mockito.Mockito.times(2)).save(fields.capture());
        assertThat(fields.getAllValues()).allMatch(f -> f instanceof CustomFieldRelation);
        assertThat(fields.getAllValues()).extracting(CustomField::getValueBinding).containsExactly(
                "relation:INST7.parent:SOURCE:RECORDING_UNIT", "relation:INST7.parent:TARGET:RECORDING_UNIT");
        assertThat(fields.getAllValues()).extracting(CustomField::getLabel).containsExactly("Parent", "Enfants");
        verify(seeder, never()).addUncertainLinkForm(any());
    }

    @Test
    void create_canUseAnExistingFieldOfTheTargetAsTheMirror() {
        allowManage();
        stubCreation();
        Institution institution = new Institution();
        institution.setId(7L);
        RelationDefinition free = new RelationDefinition();
        free.setCode("INST7.libre");
        free.setInstitution(institution);
        free.setMirror(RelationMirror.NONE);
        free.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        free.addKind(RelationSide.TARGET, RelationEntityKind.SPECIMEN, null);
        Concept concept = new Concept();
        CustomFieldRelation freeField = CustomFieldRelation.builder().id(900L).label("Libre").concept(concept)
                .valueBinding("relation:INST7.libre:SOURCE:SPECIMEN").build();
        when(definitionRepository.findVisibleTo(7L)).thenReturn(List.of(free));
        when(definitionRepository.findByCode("INST7.libre")).thenReturn(Optional.of(free));
        when(definitionRepository.findByCode("INST7.contenu")).thenReturn(Optional.empty());
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.libre:")).thenReturn(List.of(freeField));

        assertThat(service.mirrorCandidates(userInfo, RelationEntityKind.RECORDING_UNIT)).containsExactly(freeField);
        assertThat(service.mirrorCandidates(userInfo, RelationEntityKind.SPECIMEN)).isEmpty();

        service.create(userInfo, new RelationDefinitionService.Draft("Contenu", null, RelationMirror.OTHER,
                RelationEntityKind.SPECIMEN, List.of(), RelationEntityKind.RECORDING_UNIT, List.of(), false, 900L));

        assertThat(freeField.getValueBinding()).isEqualTo("relation:INST7.contenu:TARGET:SPECIMEN");
        assertThat(freeField.getConcept()).isSameAs(concept);
        verify(definitionRepository).delete(free);
        verify(customFieldRepository, never()).delete(any());
    }

    @Test
    void create_symmetric_makesOneFieldReadFromBothEnds() {
        allowManage();
        stubCreation();

        service.create(userInfo, new RelationDefinitionService.Draft("Voisin", null, RelationMirror.SELF,
                RelationEntityKind.SPECIMEN, List.of(), RelationEntityKind.SPECIMEN, List.of(), true));

        ArgumentCaptor<CustomField> fields = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository).save(fields.capture());
        assertThat(fields.getValue().getValueBinding()).isEqualTo("relation:INST7.voisin:BOTH:SPECIMEN");
        verify(seeder).addUncertainLinkForm(any());
    }

    @Test
    void create_rejectsAMirrorOnAKindWithNoForm() {
        allowManage();

        assertThatThrownBy(() -> service.create(userInfo, new RelationDefinitionService.Draft("Lieu", "Unités",
                RelationMirror.OTHER, RelationEntityKind.RECORDING_UNIT, List.of(), RelationEntityKind.SPATIAL_UNIT,
                List.of(), false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_refusesWithoutTheSettingsPermission() {
        assertThatThrownBy(() -> service.create(userInfo, new RelationDefinitionService.Draft("Parent", null,
                RelationMirror.NONE, RelationEntityKind.RECORDING_UNIT, List.of(), RelationEntityKind.RECORDING_UNIT,
                List.of(), false))).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void delete_refusesAPlatformDefinition() {
        allowManage();
        RelationDefinition system = new RelationDefinition();
        system.setSystem(true);
        when(definitionRepository.findById(1L)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.delete(userInfo, 1L)).isInstanceOf(ForbiddenOperationException.class);
        verify(linkService, never()).deleteAllLinksOf(any(RelationDefinition.class));
    }

    @Test
    void delete_removesLinksFieldsAndTheDefinition() {
        allowManage();
        Institution institution = new Institution();
        institution.setId(7L);
        RelationDefinition definition = new RelationDefinition();
        definition.setCode("INST7.parent");
        definition.setInstitution(institution);
        CustomFieldRelation field = CustomFieldRelation.builder().valueBinding("relation:INST7.parent:SOURCE:RECORDING_UNIT").build();
        when(definitionRepository.findById(2L)).thenReturn(Optional.of(definition));
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.parent:")).thenReturn(List.of(field));

        service.delete(userInfo, 2L);

        verify(linkService).deleteAllLinksOf(definition);
        verify(customFieldRepository).delete(field);
        verify(definitionRepository).delete(definition);
        verify(fieldFormConfigRepository).deleteAll(any());
        verify(mappingRepository).deleteAll(any());
        verify(institutionRepository, never()).deleteById(eq(7L));
    }

    @Test
    void fieldsFor_keepsTheFieldsOfTheEndTheKindIsAt() {
        Institution institution = new Institution();
        institution.setId(7L);
        RelationDefinition definition = new RelationDefinition();
        definition.setCode("INST7.parent");
        definition.setInstitution(institution);
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.SPECIMEN, null);
        CustomFieldRelation onUnit = CustomFieldRelation.builder().label("a").valueBinding("relation:INST7.parent:SOURCE:SPECIMEN").build();
        CustomFieldRelation onSpecimen = CustomFieldRelation.builder().label("b").valueBinding("relation:INST7.parent:TARGET:RECORDING_UNIT").build();
        when(definitionRepository.findVisibleTo(7L)).thenReturn(List.of(definition));
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.parent:")).thenReturn(List.of(onUnit, onSpecimen));

        assertThat(service.fieldsFor(7L, RelationEntityKind.RECORDING_UNIT)).containsExactly(onUnit);
        assertThat(service.fieldsFor(7L, RelationEntityKind.SPECIMEN)).containsExactly(onSpecimen);
    }

    private RelationDefinition ownDefinition() {
        Institution institution = new Institution();
        institution.setId(7L);
        RelationDefinition definition = new RelationDefinition();
        definition.setCode("INST7.parent");
        definition.setInstitution(institution);
        definition.setMirror(RelationMirror.OTHER);
        Concept fact = new Concept();
        fact.setId(5L);
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, fact);
        return definition;
    }

    private static CustomFieldRelation parentField() {
        return CustomFieldRelation.builder().label("Parent")
                .valueBinding("relation:INST7.parent:SOURCE:RECORDING_UNIT").build();
    }

    private static CustomFieldRelation childrenField() {
        return CustomFieldRelation.builder().label("Enfants")
                .valueBinding("relation:INST7.parent:TARGET:RECORDING_UNIT").build();
    }

    @Test
    void describe_readsTheTypesOfEachSideFromTheFieldsPointOfView() {
        RelationDefinition definition = ownDefinition();
        when(definitionRepository.findByCode("INST7.parent")).thenReturn(Optional.of(definition));
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.parent:"))
                .thenReturn(List.of(parentField(), childrenField()));

        var fromParent = service.describe(parentField()).orElseThrow();
        var fromChildren = service.describe(childrenField()).orElseThrow();

        assertThat(fromParent.targetTypes()).containsExactly(5L);
        assertThat(fromParent.sourceTypes()).isEmpty();
        assertThat(fromParent.mirrorLabel()).isEqualTo("Enfants");
        // seen from the mirror, the two sides swap
        assertThat(fromChildren.sourceTypes()).containsExactly(5L);
        assertThat(fromChildren.mirrorLabel()).isEqualTo("Parent");
    }

    @Test
    void update_replacesTheAcceptedTypesAndRenamesTheFields() {
        allowManage();
        RelationDefinition definition = ownDefinition();
        CustomFieldRelation parent = parentField();
        CustomFieldRelation children = childrenField();
        Concept type = new Concept();
        type.setId(9L);
        when(definitionRepository.findByCode("INST7.parent")).thenReturn(Optional.of(definition));
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.parent:")).thenReturn(List.of(parent, children));
        when(conceptRepository.findById(9L)).thenReturn(Optional.of(type));

        service.update(userInfo, parent, "Ancêtre", new fr.siamois.domain.models.settings.tableconfig.RelationOptions(
                RelationEntityKind.RECORDING_UNIT, List.of(), List.of(9L), RelationMirror.OTHER, "Descendants", false));

        assertThat(definition.kindsOf(RelationSide.TARGET)).extracting(k -> k.getTypeConcept().getId()).containsExactly(9L);
        assertThat(definition.kindsOf(RelationSide.SOURCE)).hasSize(1);
        assertThat(parent.getLabel()).isEqualTo("Ancêtre");
        assertThat(children.getLabel()).isEqualTo("Descendants");
    }

    @Test
    void update_refusesAPlatformRelation() {
        allowManage();
        RelationDefinition system = ownDefinition();
        system.setSystem(true);
        when(definitionRepository.findByCode("INST7.parent")).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.update(userInfo, parentField(), "x",
                new fr.siamois.domain.models.settings.tableconfig.RelationOptions(RelationEntityKind.RECORDING_UNIT,
                        List.of(), List.of(), RelationMirror.OTHER, null, false)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void deleteIfUnused_keepsARelationThatStillHasLinks() {
        RelationDefinition definition = ownDefinition();
        when(definitionRepository.findByCode("INST7.parent")).thenReturn(Optional.of(definition));
        when(linkRepository.countByDefinition(definition)).thenReturn(3L);

        service.deleteIfUnused(userInfo, parentField());

        verify(definitionRepository, never()).delete(any());
    }

    @Test
    void deleteIfUnused_keepsARelationWhoseMirrorIsStillInAForm() {
        RelationDefinition definition = ownDefinition();
        CustomFieldRelation children = childrenField();
        children.setId(12L);
        when(definitionRepository.findByCode("INST7.parent")).thenReturn(Optional.of(definition));
        when(linkRepository.countByDefinition(definition)).thenReturn(0L);
        when(customFieldRepository.findByValueBindingStartingWith("relation:INST7.parent:")).thenReturn(List.of(children));
        when(fieldFormConfigRepository.countByFieldId(12L)).thenReturn(1L);

        service.deleteIfUnused(userInfo, parentField());

        verify(definitionRepository, never()).delete(any());
    }
}
