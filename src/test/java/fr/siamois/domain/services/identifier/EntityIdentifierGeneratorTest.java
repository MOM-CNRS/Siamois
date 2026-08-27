package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.identifier.IdentifierCounterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityIdentifierGeneratorTest {

    private static final Map<String, Object> NO_VALUES = Map.of();
    private static final Predicate<String> IDENTIFIER_UNUSED = candidate -> false;

    @Mock
    private TableFieldConfigService tableFieldConfigService;
    @Mock
    private IdentifierResolverRegistry resolverRegistry;
    @Mock
    private IdentifierPartitionService partitionService;
    @Mock
    private IdentifierCounterRepository counterRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private EntityIdentifierGenerator generator;

    @BeforeEach
    void setUpPersistenceContext() {
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);
    }

    @Test
    void generate_allocatesRendersAndRestoresFlushMode() {
        ActionUnit actionUnit = actionUnit(7L);
        FormConfig config = config(12L, "UE-{NUM_UE:000}", 4, 99);
        Map<String, Object> displayValues = new HashMap<>(Map.of("ID_UA", "UA-7"));
        Map<String, Object> partitionValues = new HashMap<>(Map.of("PARENT_RU", 31L));

        when(tableFieldConfigService.resolveIdentifierConfig(7L, ConfigurableTable.UE, 42L)).thenReturn(config);
        when(partitionService.canonicalKey(eq(ConfigurableTable.UE), eq(config.getIdentifierFormat()), any()))
                .thenReturn("v1|PARENT_RU=31");
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(counterRepository.nextValue(7L, 12L, "v1|PARENT_RU=31", 4)).thenReturn(4);
        when(resolverRegistry.ownNumericalToken(ConfigurableTable.UE)).thenReturn("NUM_UE");
        when(resolverRegistry.render(eq(ConfigurableTable.UE), eq(config.getIdentifierFormat()), any()))
                .thenAnswer(invocation -> {
                    IdentifierRenderContext context = invocation.getArgument(2);
                    return "UE-00" + context.value("NUM_UE");
                });

        GeneratedIdentifier result = generator.generate(ConfigurableTable.UE, actionUnit, 42L,
                displayValues, partitionValues, candidate -> false);

        assertThat(result).isEqualTo(new GeneratedIdentifier(4, "UE-004"));
        assertThat(displayValues).containsExactly(Map.entry("ID_UA", "UA-7"));
        assertThat(partitionValues).containsExactly(Map.entry("PARENT_RU", 31L));
        verify(entityManager).setFlushMode(FlushModeType.COMMIT);
        verify(entityManager).setFlushMode(FlushModeType.AUTO);
    }

    @Test
    void generate_retriesWhenRenderedIdentifierIsAlreadyUsed() {
        ActionUnit actionUnit = actionUnit(7L);
        FormConfig config = config(12L, "UE-{NUM_UE}", 10, 99);
        when(tableFieldConfigService.resolveIdentifierConfig(7L, ConfigurableTable.UE, null)).thenReturn(config);
        when(partitionService.canonicalKey(any(), anyString(), any())).thenReturn("v1");
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(counterRepository.nextValue(7L, 12L, "v1", 10)).thenReturn(10, 11);
        when(resolverRegistry.ownNumericalToken(ConfigurableTable.UE)).thenReturn("NUM_UE");
        when(resolverRegistry.render(eq(ConfigurableTable.UE), anyString(), any()))
                .thenAnswer(invocation -> "UE-" + ((IdentifierRenderContext) invocation.getArgument(2)).value("NUM_UE"));

        GeneratedIdentifier result = generator.generate(ConfigurableTable.UE, actionUnit, null,
                Map.of(), Map.of(), "UE-10"::equals);

        assertThat(result).isEqualTo(new GeneratedIdentifier(11, "UE-11"));
        verify(counterRepository, times(2)).nextValue(7L, 12L, "v1", 10);
        verify(entityManager).setFlushMode(FlushModeType.AUTO);
    }

    @Test
    void generate_throwsWhenRangeIsExhaustedAndStillRestoresFlushMode() {
        ActionUnit actionUnit = actionUnit(7L);
        FormConfig config = config(12L, "{NUM_UE}", 0, 10);
        when(tableFieldConfigService.resolveIdentifierConfig(7L, ConfigurableTable.UE, null)).thenReturn(config);
        when(partitionService.canonicalKey(any(), anyString(), any())).thenReturn("v1");
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(counterRepository.nextValue(7L, 12L, "v1", 0)).thenReturn(11);

        assertThatThrownBy(() -> generator.generate(ConfigurableTable.UE, actionUnit, null,
                NO_VALUES, NO_VALUES, IDENTIFIER_UNUSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Identifier range exhausted for form config 12");

        verify(entityManager).setFlushMode(FlushModeType.COMMIT);
        verify(entityManager).setFlushMode(FlushModeType.AUTO);
        verifyNoInteractions(resolverRegistry);
    }

    @Test
    void generate_rejectsInvalidRangesBeforeCounterAllocation() {
        ActionUnit actionUnit = actionUnit(7L);
        FormConfig config = config(12L, "{NUM_UE}", -1, 10);
        when(tableFieldConfigService.resolveIdentifierConfig(7L, ConfigurableTable.UE, null)).thenReturn(config);

        assertThatThrownBy(() -> generator.generate(ConfigurableTable.UE, actionUnit, null,
                NO_VALUES, NO_VALUES, IDENTIFIER_UNUSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid identifier range on form config 12");

        config.setMinCode(11);
        config.setMaxCode(10);
        assertThatThrownBy(() -> generator.generate(ConfigurableTable.UE, actionUnit, null,
                NO_VALUES, NO_VALUES, IDENTIFIER_UNUSED))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(counterRepository);
    }

    @Test
    void generate_requiresAPersistedActionUnit() {
        assertThatThrownBy(() -> generator.generate(ConfigurableTable.UE, null, null,
                NO_VALUES, NO_VALUES, IDENTIFIER_UNUSED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("An action unit is required to generate an identifier");

        ActionUnit unpersistedActionUnit = new ActionUnit();
        assertThatThrownBy(() -> generator.generate(ConfigurableTable.UE, unpersistedActionUnit, null,
                NO_VALUES, NO_VALUES, IDENTIFIER_UNUSED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The action unit must be persisted before identifier generation");
        verifyNoInteractions(tableFieldConfigService, counterRepository);
    }

    @Test
    void generate_alsoWorksWithoutAnInjectedEntityManager() {
        EntityIdentifierGenerator generatorWithoutEntityManager = new EntityIdentifierGenerator(
                tableFieldConfigService, resolverRegistry, partitionService, counterRepository);
        ActionUnit actionUnit = actionUnit(7L);
        FormConfig config = config(12L, "{NUM_UE}", 1, 9);
        when(tableFieldConfigService.resolveIdentifierConfig(7L, ConfigurableTable.UE, null)).thenReturn(config);
        when(partitionService.canonicalKey(any(), anyString(), any())).thenReturn("v1");
        when(counterRepository.nextValue(7L, 12L, "v1", 1)).thenReturn(1);
        when(resolverRegistry.ownNumericalToken(ConfigurableTable.UE)).thenReturn("NUM_UE");
        when(resolverRegistry.render(eq(ConfigurableTable.UE), anyString(), any())).thenReturn("1");

        assertThat(generatorWithoutEntityManager.generate(ConfigurableTable.UE, actionUnit, null,
                Map.of(), Map.of(), candidate -> false))
                .isEqualTo(new GeneratedIdentifier(1, "1"));

        verifyNoInteractions(entityManager);
    }

    @Test
    void generateIdentifierIfRequired_resolvesSpecAndAssignsResult() {
        EntityIdentifierGenerator spyGenerator = spy(generator);
        TestEntity entity = new TestEntity(actionUnit(7L), 42L, "parent");
        IdentifierGenerationSpec<TestEntity> spec = testSpec(candidate -> candidate.equals("UE-4"));
        doReturn(new GeneratedIdentifier(4, "UE-4")).when(spyGenerator)
                .generate(eq(ConfigurableTable.UE), eq(entity.actionUnit), eq(42L),
                        anyMap(), anyMap(), any());

        assertThat(spyGenerator.generateIdentifierIfRequired(entity, spec))
                .contains(new GeneratedIdentifier(4, "UE-4"));
        assertThat(entity.number).isEqualTo(4);
        assertThat(entity.identifier).isEqualTo("UE-4");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> displayValues = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> partitionValues = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<String>> collision = ArgumentCaptor.forClass(Predicate.class);
        verify(spyGenerator).generate(eq(ConfigurableTable.UE), eq(entity.actionUnit), eq(42L),
                displayValues.capture(), partitionValues.capture(), collision.capture());
        assertThat(displayValues.getValue()).containsEntry("ID_PARENT", "parent");
        assertThat(partitionValues.getValue()).containsEntry("PARENT_RU", 31L);
        assertThat(collision.getValue()).accepts("UE-4").rejects("UE-5");
    }

    @Test
    void generateIdentifierIfRequired_skipsIneligibleEntityAndValidatesRequiredInputs() {
        IdentifierGenerationSpec<TestEntity> spec = testSpec(candidate -> false);
        TestEntity existing = new TestEntity(actionUnit(7L), 42L, "parent");
        existing.number = 1;
        assertThat(generator.generateIdentifierIfRequired(existing, spec)).isEmpty();

        assertThatThrownBy(() -> generator.generateIdentifierIfRequired(null, spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A test entity is required to generate an identifier");

        TestEntity withoutActionUnit = new TestEntity(null, 42L, "parent");
        assertThatThrownBy(() -> generator.generateIdentifierIfRequired(withoutActionUnit, spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An action unit is required to generate a test entity identifier");
        verifyNoInteractions(tableFieldConfigService, counterRepository);
    }

    @Test
    void generateIdentifierIfRequired_propagatesGenerationFailureWithoutAssigning() {
        EntityIdentifierGenerator spyGenerator = spy(generator);
        TestEntity entity = new TestEntity(actionUnit(7L), 42L, "parent");
        IdentifierGenerationSpec<TestEntity> spec = testSpec(candidate -> false);
        IllegalStateException failure = new IllegalStateException("range exhausted");
        doThrow(failure).when(spyGenerator).generate(any(), any(), any(), anyMap(), anyMap(), any());

        assertThatThrownBy(() -> spyGenerator.generateIdentifierIfRequired(entity, spec))
                .isSameAs(failure);
        assertThat(entity.number).isZero();
        assertThat(entity.identifier).isNull();
    }

    private static IdentifierGenerationSpec<TestEntity> testSpec(Predicate<String> collision) {
        return IdentifierGenerationSpec.<TestEntity>builder()
                .table(ConfigurableTable.UE)
                .entityName("test entity")
                .generationRequired(entity -> entity.number == 0)
                .actionUnit(entity -> entity.actionUnit)
                .typeId(entity -> entity.typeId)
                .displayValue("ID_PARENT", entity -> entity.parentIdentifier)
                .partitionValue("PARENT_RU", entity -> 31L)
                .identifierAlreadyUsed((entity, candidate) -> collision.test(candidate))
                .numberSetter((entity, number) -> entity.number = number)
                .identifierSetter((entity, identifier) -> entity.identifier = identifier)
                .build();
    }

    private static final class TestEntity {
        private final ActionUnit actionUnit;
        private final Long typeId;
        private final String parentIdentifier;
        private int number;
        private String identifier;

        private TestEntity(ActionUnit actionUnit, Long typeId, String parentIdentifier) {
            this.actionUnit = actionUnit;
            this.typeId = typeId;
            this.parentIdentifier = parentIdentifier;
        }
    }

    private static ActionUnit actionUnit(Long id) {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(id);
        return actionUnit;
    }

    private static FormConfig config(Long id, String format, int min, int max) {
        FormConfig config = new FormConfig();
        config.setId(id);
        config.setIdentifierFormat(format);
        config.setMinCode(min);
        config.setMaxCode(max);
        return config;
    }
}
