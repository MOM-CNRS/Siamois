package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.identifier.GeneratedIdentifier;
import fr.siamois.domain.services.identifier.IdentifierPartitionService;
import fr.siamois.domain.services.identifier.IdentifierResolverRegistry;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.identifier.InstitutionIdentifierCounterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectIdentifierServiceTest {

    private final FormConfigRepository formConfigRepository = mock(FormConfigRepository.class);
    private final FieldConfigurationService fieldConfigurationService = mock(FieldConfigurationService.class);
    private final InstitutionIdentifierCounterRepository counterRepository = mock(InstitutionIdentifierCounterRepository.class);
    private final ActionUnitRepository actionUnitRepository = mock(ActionUnitRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private ProjectIdentifierService service;
    private UserInfo info;
    private FormConfig config;

    @BeforeEach
    void setUp() throws Exception {
        IdentifierResolverRegistry registry = new IdentifierResolverRegistry();
        service = new ProjectIdentifierService(formConfigRepository, fieldConfigurationService, registry,
                new IdentifierPartitionService(registry), counterRepository, actionUnitRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(5L);
        info = mock(UserInfo.class);
        when(info.getInstitution()).thenReturn(institution);

        Concept field = new Concept();
        field.setId(900L);
        ConceptFieldConfig fieldConfig = new ConceptFieldConfig();
        fieldConfig.setConcept(field);
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), eq("SIAAU.TYPE"), eq((Long) null)))
                .thenReturn(fieldConfig);

        config = new FormConfig();
        config.setId(11L);
        config.setIdentifierFormat("PROJ{NUM_PROJECT}");
        config.setMinCode(1);
        config.setMaxCode(999);
        when(formConfigRepository.findInstitutionDefaults(5L, 900L)).thenReturn(List.of(config));
    }

    @Test
    void generate_rendersTheFormatWithTheAllocatedNumber() {
        when(counterRepository.nextValue(eq(5L), eq(11L), anyString(), eq(1))).thenReturn(3);

        GeneratedIdentifier generated = service.generate(info);

        assertThat(generated.number()).isEqualTo(3);
        assertThat(generated.value()).isEqualTo("PROJ3");
    }

    @Test
    void generate_skipsAnIdentifierAlreadyTakenByAProject() {
        when(counterRepository.nextValue(eq(5L), eq(11L), anyString(), eq(1))).thenReturn(1, 2);
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("PROJ1", 5L))
                .thenReturn(Optional.of(new ActionUnit()));

        assertThat(service.generate(info).value()).isEqualTo("PROJ2");
    }

    @Test
    void generate_failsOnceTheRangeIsExhausted() {
        config.setMaxCode(2);
        when(counterRepository.nextValue(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(3);

        assertThatThrownBy(() -> service.generate(info)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generate_createsTheInstitutionConfigWithTheBuiltInFormatOnFirstUse() {
        when(formConfigRepository.findInstitutionDefaults(5L, 900L)).thenReturn(List.of());
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> {
            FormConfig saved = call.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(entityManager.getReference(Institution.class, 5L)).thenReturn(new Institution());
        when(counterRepository.nextValue(eq(5L), eq(12L), anyString(), eq(1))).thenReturn(1);

        assertThat(service.generate(info).value()).isEqualTo("PROJ1");

        ArgumentCaptor<FormConfig> saved = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isNull();
        assertThat(saved.getValue().getValueConcept()).isNull();
        assertThat(saved.getValue().getIdentifierFormat()).isEqualTo("PROJ{NUM_PROJECT}");
    }

    @Test
    void preview_showsTheNextFreeIdentifierWithoutConsumingIt() {
        when(counterRepository.findCounter(eq(5L), eq(11L), anyString())).thenReturn(Optional.of(4));
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("PROJ4", 5L))
                .thenReturn(Optional.of(new ActionUnit()));

        assertThat(service.preview(info)).contains("PROJ5");
        verify(counterRepository, org.mockito.Mockito.never()).nextValue(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    void preview_startsAtTheFirstNumberWhenNothingWasAllocatedYet() {
        when(formConfigRepository.findInstitutionDefaults(5L, 900L)).thenReturn(List.of());

        assertThat(service.preview(info)).contains("PROJ1");
    }
}
