package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerBoolean;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigAnswerRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationLinkAttributesServiceTest {

    @Mock FieldFormConfigRepository fieldFormConfigRepository;
    @Mock FormConfigAnswerRepository formConfigAnswerRepository;
    @Mock CustomFieldAnswerRepository customFieldAnswerRepository;
    @Mock PersonRepository personRepository;

    private RelationLinkAttributesService service;
    private RelationLink link;
    private FormConfig config;
    private CustomFieldBoolean uncertain;
    private final PersonDTO author = new PersonDTO();

    @BeforeEach
    void setUp() {
        service = new RelationLinkAttributesService(fieldFormConfigRepository, formConfigAnswerRepository,
                customFieldAnswerRepository, personRepository);
        author.setId(5L);

        config = new FormConfig();
        config.setId(30L);
        RelationDefinition definition = new RelationDefinition();
        definition.setLinkFormConfig(config);
        link = new RelationLink();
        link.setId(7L);
        link.setDefinition(definition);

        uncertain = CustomFieldBoolean.builder().id(-900L).build();
        FieldFormConfig fieldConfig = new FieldFormConfig();
        fieldConfig.setField(uncertain);
        fieldConfig.setFormConfig(config);
        fieldConfig.setActive(true);
        FieldFormConfig inactive = new FieldFormConfig();
        inactive.setField(CustomFieldText.builder().id(-901L).build());
        inactive.setFormConfig(config);
        inactive.setActive(false);
        when(fieldFormConfigRepository.findAllByFormConfigId(30L)).thenReturn(List.of(fieldConfig, inactive));

        when(personRepository.findById(Long.valueOf(5L))).thenReturn(Optional.of(new Person()));
        when(formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link)).thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenAnswer(inv -> {
            FormConfigAnswer set = inv.getArgument(0);
            set.setId(60L);
            return set;
        });
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void activeFields_isTheActiveFieldsOfTheLinkForm() {
        assertThat(service.activeFields(link)).containsOnlyKeys(-900L);
    }

    @Test
    void activeFields_isEmptyWithoutALinkForm() {
        link.getDefinition().setLinkFormConfig(null);

        assertThat(service.activeFields(link)).isEmpty();
    }

    @Test
    void save_createsTheLinksAnswerSetAndTheAnswer() {
        service.save(link, Map.of(-900L, true), author);

        ArgumentCaptor<FormConfigAnswer> set = ArgumentCaptor.forClass(FormConfigAnswer.class);
        verify(formConfigAnswerRepository).save(set.capture());
        assertThat(set.getValue().getRelationLink()).isSameAs(link);
        assertThat(set.getValue().getFormConfig()).isSameAs(config);

        ArgumentCaptor<CustomFieldAnswer> answer = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(answer.capture());
        assertThat(answer.getValue()).isInstanceOf(CustomFieldAnswerBoolean.class);
        assertThat(answer.getValue().getValue()).isEqualTo(true);
        assertThat(answer.getValue().getCustomField()).isSameAs(uncertain);
    }

    @Test
    void save_aNewAnswerIsVisibleToARead_inTheSameTransaction() {
        when(customFieldAnswerRepository.save(any(CustomFieldAnswer.class))).thenAnswer(inv -> inv.getArgument(0));
        FormConfigAnswer[] stored = new FormConfigAnswer[1];
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenAnswer(inv -> {
            stored[0] = inv.getArgument(0);
            return stored[0];
        });

        service.save(link, Map.of(-900L, true), author);
        when(formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link)).thenReturn(Optional.of(stored[0]));

        assertThat(service.read(link)).containsEntry(-900L, true);
    }

    @Test
    void save_updatesTheExistingAnswerInPlace() {
        FormConfigAnswer set = new FormConfigAnswer();
        set.setAnswers(new HashSet<>());
        CustomFieldAnswerBoolean existing = new CustomFieldAnswerBoolean();
        existing.setValue(true);
        when(formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link)).thenReturn(Optional.of(set));
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(set, uncertain)).thenReturn(Optional.of(existing));

        service.save(link, Map.of(-900L, false), author);

        assertThat(existing.getValue()).isEqualTo(false);
        verify(customFieldAnswerRepository).save(existing);
        verify(formConfigAnswerRepository, never()).save(any(FormConfigAnswer.class));
    }

    @Test
    void save_aNullValueDeletesTheAnswer() {
        FormConfigAnswer set = new FormConfigAnswer();
        CustomFieldAnswerBoolean existing = new CustomFieldAnswerBoolean();
        set.setAnswers(new HashSet<>(List.of(existing)));
        when(formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link)).thenReturn(Optional.of(set));
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(set, uncertain)).thenReturn(Optional.of(existing));
        Map<Long, Object> clear = new HashMap<>();
        clear.put(-900L, null);

        service.save(link, clear, author);

        verify(customFieldAnswerRepository).delete(existing);
        verify(customFieldAnswerRepository, never()).save(any(CustomFieldAnswer.class));
    }

    @Test
    void save_refusesAFieldThatIsNotAnActiveAttributeOfTheLink() {
        assertThatThrownBy(() -> service.save(link, Map.of(-901L, "x"), author))
                .isInstanceOfSatisfying(RelationLinkException.class,
                        e -> assertThat(e.getReason()).isEqualTo(RelationLinkException.Reason.INVALID_ATTRIBUTE));
        assertThatThrownBy(() -> service.save(link, Map.of(-1234L, true), author))
                .isInstanceOf(RelationLinkException.class);
        verify(formConfigAnswerRepository, never()).save(any(FormConfigAnswer.class));
    }

    @Test
    void save_refusesAValueOfTheWrongType() {
        assertThatThrownBy(() -> service.save(link, Map.of(-900L, "yes"), author))
                .isInstanceOfSatisfying(RelationLinkException.class,
                        e -> assertThat(e.getReason()).isEqualTo(RelationLinkException.Reason.INVALID_ATTRIBUTE));
        verify(formConfigAnswerRepository, never()).save(any(FormConfigAnswer.class));
    }

    @Test
    void save_withNothingToWriteTouchesNothing() {
        service.save(link, null, author);
        service.save(link, Map.of(), author);

        verify(formConfigAnswerRepository, never()).save(any(FormConfigAnswer.class));
    }

    @Test
    void read_returnsTheStoredValuesByFieldId() {
        FormConfigAnswer set = new FormConfigAnswer();
        CustomFieldAnswerBoolean answer = new CustomFieldAnswerBoolean();
        answer.setCustomField(uncertain);
        answer.setValue(true);
        set.setAnswers(new HashSet<>(List.of(answer)));
        when(formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link)).thenReturn(Optional.of(set));

        assertThat(service.read(link)).containsEntry(-900L, true);
    }

    @Test
    void deleteAll_removesTheAnswersThenTheSet() {
        FormConfigAnswer set = new FormConfigAnswer();
        CustomFieldAnswerBoolean answer = new CustomFieldAnswerBoolean();
        set.setAnswers(new HashSet<>(List.of(answer)));
        when(formConfigAnswerRepository.findByRelationLink(link)).thenReturn(List.of(set));

        service.deleteAll(link);

        verify(customFieldAnswerRepository).deleteAll(List.of(answer));
        verify(formConfigAnswerRepository).delete(set);
    }
}
