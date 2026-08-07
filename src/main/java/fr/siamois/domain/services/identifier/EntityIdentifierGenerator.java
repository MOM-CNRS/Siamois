package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.identifier.IdentifierCounterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** One allocator/rendering transaction shared by every configurable entity table. */
@Service
public class EntityIdentifierGenerator {
    private final TableFieldConfigService tableFieldConfigService;
    private final IdentifierResolverRegistry resolverRegistry;
    private final IdentifierPartitionService partitionService;
    private final IdentifierCounterRepository counterRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public EntityIdentifierGenerator(TableFieldConfigService tableFieldConfigService,
                                     IdentifierResolverRegistry resolverRegistry,
                                     IdentifierPartitionService partitionService,
                                     IdentifierCounterRepository counterRepository) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.resolverRegistry = resolverRegistry;
        this.partitionService = partitionService;
        this.counterRepository = counterRepository;
    }

    @Transactional
    public GeneratedIdentifier generate(ConfigurableTable table,
                                        ActionUnit actionUnit,
                                        Long typeConceptId,
                                        Map<String, ?> displayValues,
                                        Map<String, ?> partitionValues,
                                        Predicate<String> identifierAlreadyUsed) {
        Objects.requireNonNull(actionUnit, "An action unit is required to generate an identifier");
        Objects.requireNonNull(actionUnit.getId(), "The action unit must be persisted before identifier generation");

        FormConfig config = tableFieldConfigService.resolveIdentifierConfig(
                actionUnit.getId(), table, typeConceptId);
        validateRange(config);

        Map<String, Object> values = new HashMap<>();
        displayValues.forEach(values::put);
        Map<String, Object> partitions = new HashMap<>();
        partitionValues.forEach(partitions::put);
        MapIdentifierRenderContext context = new MapIdentifierRenderContext(values, partitions);
        String canonicalKey = partitionService.canonicalKey(table, config.getIdentifierFormat(), context);

        FlushModeType previousFlushMode = entityManager == null ? null : entityManager.getFlushMode();
        if (entityManager != null) entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            while (true) {
                int number = counterRepository.nextValue(
                        actionUnit.getId(), config.getId(), canonicalKey, config.getMinCode());
                if (number > config.getMaxCode()) {
                    throw new IllegalStateException("Identifier range exhausted for form config " + config.getId());
                }
                values.put(resolverRegistry.ownNumericalToken(table), number);
                String rendered = resolverRegistry.render(
                        table, config.getIdentifierFormat(),
                        new MapIdentifierRenderContext(values, partitions));
                if (!identifierAlreadyUsed.test(rendered)) {
                    return new GeneratedIdentifier(number, rendered);
                }
            }
        } finally {
            if (entityManager != null && previousFlushMode != null) {
                entityManager.setFlushMode(previousFlushMode);
            }
        }
    }

    @Transactional
    public <E> Optional<GeneratedIdentifier> generateIdentifierIfRequired(
            E entity, IdentifierGenerationSpec<E> spec) {
        Objects.requireNonNull(spec, "Identifier generation specification is required");
        if (entity == null) {
            throw new IllegalArgumentException("A " + spec.entityName() + " is required to generate an identifier");
        }
        if (!spec.generationRequired().test(entity)) {
            return Optional.empty();
        }

        ActionUnit actionUnit = spec.actionUnit().apply(entity);

        // Entities have to be attached to a project
        if (actionUnit == null) {
            throw new IllegalArgumentException(
                    "An action unit is required to generate a " + spec.entityName() + " identifier");
        }

        Map<String, Object> displayValues = resolveValues(entity, spec.displayValues());
        Map<String, Object> partitionValues = resolveValues(entity, spec.partitionValues());
        GeneratedIdentifier generated = generate(spec.table(), actionUnit, spec.typeId().apply(entity),
                displayValues, partitionValues,
                candidate -> spec.identifierAlreadyUsed().test(entity, candidate));
        spec.numberSetter().accept(entity, generated.number());
        spec.identifierSetter().accept(entity, generated.value());
        return Optional.of(generated);
    }

    private static <E> Map<String, Object> resolveValues(
            E entity, Map<String, Function<E, ?>> accessors) {
        Map<String, Object> values = new HashMap<>();
        accessors.forEach((key, accessor) -> values.put(key, accessor.apply(entity)));
        return values;
    }

    private static void validateRange(FormConfig config) {
        if (config.getMinCode() < 0 || config.getMaxCode() < config.getMinCode()) {
            throw new IllegalArgumentException("Invalid identifier range on form config " + config.getId());
        }
    }
}
