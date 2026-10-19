package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigGroup;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.config.ConceptFieldStateRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigGroupRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectCopyTemplateApplierTest {

    private final ActionUnitRepository actionUnitRepository = mock(ActionUnitRepository.class);
    private final FormConfigRepository formConfigRepository = mock(FormConfigRepository.class);
    private final FormConfigGroupRepository groupRepository = mock(FormConfigGroupRepository.class);
    private final FieldFormConfigRepository fieldRepository = mock(FieldFormConfigRepository.class);
    private final ConceptFieldStateRepository stateRepository = mock(ConceptFieldStateRepository.class);
    private final ConceptFieldConfigRepository conceptFieldConfigRepository = mock(ConceptFieldConfigRepository.class);

    private final ProjectCopyTemplateApplier applier = new ProjectCopyTemplateApplier(actionUnitRepository,
            formConfigRepository, groupRepository, fieldRepository, stateRepository, conceptFieldConfigRepository);

    private Institution institution;
    private ActionUnit source;
    private ActionUnit target;

    @BeforeEach
    void setUp() {
        institution = new Institution();
        institution.setId(1L);
        source = project(10L);
        target = project(20L);
        when(actionUnitRepository.findById(10L)).thenReturn(Optional.of(source));
        when(actionUnitRepository.findById(20L)).thenReturn(Optional.of(target));
    }

    private ActionUnit project(long id) {
        ActionUnit unit = new ActionUnit();
        unit.setId(id);
        unit.setCreatedByInstitution(institution);
        return unit;
    }

    @Test
    void supports_onlyProjectTemplateIds() {
        assertThat(applier.supports("project:10")).isTrue();
        assertThat(applier.supports("empty")).isFalse();
        assertThat(applier.supports(null)).isFalse();
    }

    @Test
    void apply_copiesFormConfigGroupsFieldsAndRemapsThemToTheCopies() {
        Concept fieldConcept = new Concept();
        fieldConcept.setId(900L);
        Concept typeValue = new Concept();
        typeValue.setId(901L);
        FormConfig original = new FormConfig();
        original.setId(1L);
        original.setActionUnit(source);
        original.setInstitution(institution);
        original.setFieldConcept(fieldConcept);
        original.setValueConcept(typeValue);
        original.setIdentifierFormat("US{NUM_UE:00}");
        original.setMinCode(1);
        original.setMaxCode(99);
        when(formConfigRepository.findAllByActionUnitId(10L)).thenReturn(List.of(original));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> {
            FormConfig saved = call.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        FormConfigGroup group = new FormConfigGroup(original, "Général", 0);
        group.setId(50L);
        when(groupRepository.findAllByFormConfigIdOrderByPosition(1L)).thenReturn(List.of(group));
        when(groupRepository.save(any(FormConfigGroup.class))).thenAnswer(call -> {
            FormConfigGroup saved = call.getArgument(0);
            saved.setId(51L);
            return saved;
        });

        CustomFieldText customField = new CustomFieldText();
        customField.setId(7L);
        FieldFormConfig plain = new FieldFormConfig();
        plain.setField(customField);
        plain.setFormConfig(original);
        plain.setMandatory(true);
        plain.setPosition(3);
        plain.setGroup(group);
        plain.setRules("{\"x\":1}");
        ConceptFieldFormConfig restricted = new ConceptFieldFormConfig();
        restricted.setField(customField);
        restricted.setFormConfig(original);
        when(fieldRepository.findAllByFormConfigId(1L)).thenReturn(List.of(plain, restricted));

        applier.apply(null, "project:10", 20L);

        ArgumentCaptor<FormConfig> config = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(config.capture());
        assertThat(config.getValue().getActionUnit()).isSameAs(target);
        assertThat(config.getValue().getValueConcept()).isSameAs(typeValue);
        assertThat(config.getValue().getIdentifierFormat()).isEqualTo("US{NUM_UE:00}");

        ArgumentCaptor<FieldFormConfig> fields = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldRepository, org.mockito.Mockito.times(2)).save(fields.capture());
        FieldFormConfig copy = fields.getAllValues().get(0);
        assertThat(copy).isNotSameAs(plain);
        assertThat(copy.getFormConfig().getId()).isEqualTo(2L);
        assertThat(copy.getId().getFormsConfigId()).isEqualTo(2L);
        assertThat(copy.isMandatory()).isTrue();
        assertThat(copy.getPosition()).isEqualTo(3);
        assertThat(copy.getRules()).isEqualTo("{\"x\":1}");
        assertThat(copy.getGroup().getId()).isEqualTo(51L);
        assertThat(fields.getAllValues().get(1)).isInstanceOf(ConceptFieldFormConfig.class);
    }

    @Test
    void apply_copiesTheProjectThesaurusOverridesToTheNewProject() {
        ConceptFieldConfig original = new ConceptFieldConfig();
        original.setInstitution(institution);
        original.setActionUnit(source);
        original.setConcept(new Concept());
        original.setFieldCode("SIAAU.TYPE");
        when(conceptFieldConfigRepository.findAllByActionUnitId(10L)).thenReturn(List.of(original));

        applier.apply(null, "project:10", 20L);

        ArgumentCaptor<ConceptFieldConfig> saved = ArgumentCaptor.forClass(ConceptFieldConfig.class);
        verify(conceptFieldConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isSameAs(target);
        assertThat(saved.getValue().getFieldCode()).isEqualTo("SIAAU.TYPE");
    }

    @Test
    void apply_refusesATemplateFromAnotherOrganization() {
        Institution other = new Institution();
        other.setId(2L);
        source.setCreatedByInstitution(other);

        assertThatThrownBy(() -> applier.apply(null, "project:10", 20L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(formConfigRepository, never()).save(any());
    }
}
