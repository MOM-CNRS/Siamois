package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

/**
 * Typed declaration of the entity-specific inputs and outputs used by
 * {@link EntityIdentifierGenerator#generateIdentifierIfRequired(Object, IdentifierGenerationSpec)}.
 *
 * <p>A service supplies accessors rather than precomputed values. Accessors are evaluated only when
 * {@link #generationRequired()} returns {@code true}, and only after the action unit has been validated.
 * Returning {@code null} from a value accessor is supported: numerical tokens render width-aware zeroes,
 * textual tokens render {@code XXX}, and partition values use the reserved missing-value bucket.</p>
 *
 * <h2>Specification attributes</h2>
 * <ul>
 *   <li>{@code table}: selects the resolver catalog and the effective {@code FormConfig}.</li>
 *   <li>{@code entityName}: human-readable singular name used in validation errors.</li>
 *   <li>{@code generationRequired}: returns {@code false} for entities whose identifier must be preserved.</li>
 *   <li>{@code actionUnit}: resolves the project owning the counter namespace. It must return a persisted action unit.</li>
 *   <li>{@code typeId}: returns the concept used to resolve the type-specific configuration, or {@code null} for default.</li>
 *   <li>{@code displayValues}: maps format token codes to the values rendered in the identifier.</li>
 *   <li>{@code partitionValues}: maps canonical counter-dimension codes to stable grouping values, normally database IDs.</li>
 *   <li>{@code identifierAlreadyUsed}: detects a rendered identifier already owned by another entity.</li>
 *   <li>{@code numberSetter}: stores the raw allocated number; {@code identifierSetter} stores the rendered identifier.</li>
 * </ul>
 *
 * <h2>Supported display and partition keys</h2>
 * <table>
 *   <caption>Keys accepted by each configurable table</caption>
 *   <tr><th>Table</th><th>Display-value keys supplied by the service</th><th>Partition-value keys</th></tr>
 *   <tr><td>{@code UE}</td><td>{@code NUM_PARENT}, {@code ID_PARENT}, {@code NUM_USPATIAL}, {@code ID_UA}</td>
 *       <td>{@code PARENT_RU}, {@code SPATIAL_PLACE}</td></tr>
 *   <tr><td>{@code MOBILIER}</td><td>{@code NUM_PARENT}, {@code ID_PARENT}, {@code NUM_UE}, {@code ID_UE}, {@code ID_UA}</td>
 *       <td>{@code PARENT_MOBILIER}, {@code PARENT_RU}</td></tr>
 *   <tr><td>{@code CONTENANT}</td><td>{@code NUM_PARENT}, {@code ID_PARENT}, {@code ID_UA}</td>
 *       <td>{@code PARENT_CONTAINER}</td></tr>
 *   <tr><td>{@code PHASE}</td><td>{@code NUM_PARENT}, {@code ID_PARENT}, {@code PHASE_ORDER}, {@code ID_UA}</td>
 *       <td>{@code PARENT_PHASE}, {@code PHASE_ORDER}</td></tr>
 * </table>
 *
 * <p>The table's own numerical token ({@code NUM_UE}, {@code NUM_MOBILIER}, {@code NUM_CONTAINER}, or
 * {@code NUM_PHASE}) must not be declared in {@code displayValues}; the generator inserts the allocated counter
 * value automatically. Display tokens that describe the same relationship must use the same partition dimension
 * (for example, mobilier {@code NUM_UE}/{@code ID_UE} both use {@code PARENT_RU}).</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * IdentifierGenerationSpec.<Container>builder(ConfigurableTable.CONTENANT)
 *     .entityName("container")
 *     .generationRequired(container -> container.getId() == null)
 *     .actionUnit(Container::getActionUnit)
 *     .typeId(container -> container.getType() == null ? null : container.getType().getId())
 *     .displayValue("NUM_PARENT", container -> container.getParent() == null
 *             ? null : container.getParent().getGeneratedNumber())
 *     .displayValue("ID_PARENT", container -> container.getParent() == null
 *             ? null : container.getParent().getIdentifier())
 *     .displayValue("ID_UA", container -> container.getActionUnit().getFullIdentifier())
 *     .partitionValue("PARENT_CONTAINER", container -> container.getParent() == null
 *             ? null : container.getParent().getId())
 *     .identifierAlreadyUsed((container, candidate) -> repository
 *             .existsByActionUnitIdAndIdentifier(container.getActionUnit().getId(), candidate))
 *     .numberSetter(Container::setGeneratedNumber)
 *     .identifierSetter(Container::setIdentifier)
 *     .build();
 * }</pre>
 *
 * @param table resolver catalog and configurable entity table
 * @param entityName singular name used in error messages
 * @param generationRequired condition controlling whether generation runs
 * @param actionUnit accessor for the counter-owning project
 * @param typeId accessor for the optional type/category concept ID
 * @param displayValues format token accessors, excluding the table's own numerical token
 * @param partitionValues canonical counter-dimension accessors
 * @param identifierAlreadyUsed collision check scoped to the entity's project
 * @param numberSetter setter for the raw allocated number
 * @param identifierSetter setter for the rendered identifier
 * @param <E> entity type configured by this specification
 */
public record IdentifierGenerationSpec<E>(
        ConfigurableTable table,
        String entityName,
        Predicate<E> generationRequired,
        Function<E, ActionUnit> actionUnit,
        Function<E, Long> typeId,
        Map<String, Function<E, ?>> displayValues,
        Map<String, Function<E, ?>> partitionValues,
        BiPredicate<E, String> identifierAlreadyUsed,
        ObjIntConsumer<E> numberSetter,
        BiConsumer<E, String> identifierSetter) {

    public IdentifierGenerationSpec {
        Objects.requireNonNull(table, "A configurable table is required");
        Objects.requireNonNull(entityName, "An entity name is required");
        Objects.requireNonNull(generationRequired, "A generation condition is required");
        Objects.requireNonNull(actionUnit, "An action-unit accessor is required");
        Objects.requireNonNull(typeId, "A type accessor is required");
        displayValues = Collections.unmodifiableMap(new LinkedHashMap<>(displayValues));
        partitionValues = Collections.unmodifiableMap(new LinkedHashMap<>(partitionValues));
        Objects.requireNonNull(identifierAlreadyUsed, "A collision predicate is required");
        Objects.requireNonNull(numberSetter, "A number setter is required");
        Objects.requireNonNull(identifierSetter, "An identifier setter is required");
    }

    public static <E> Builder<E> builder(ConfigurableTable table) {
        return new Builder<>(table);
    }

    /** Fluent builder used by entity services to declare their identifier inputs. */
    public static final class Builder<E> {
        private final ConfigurableTable table;
        private String entityName;
        private Predicate<E> generationRequired;
        private Function<E, ActionUnit> actionUnit;
        private Function<E, Long> typeId = entity -> null;
        private final Map<String, Function<E, ?>> displayValues = new LinkedHashMap<>();
        private final Map<String, Function<E, ?>> partitionValues = new LinkedHashMap<>();
        private BiPredicate<E, String> identifierAlreadyUsed;
        private ObjIntConsumer<E> numberSetter;
        private BiConsumer<E, String> identifierSetter;

        private Builder(ConfigurableTable table) {
            this.table = table;
        }

        /** Sets the singular entity name used in validation messages, for example {@code "container"}. */
        public Builder<E> entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        /** Sets the condition that protects existing or manually assigned identifiers from regeneration. */
        public Builder<E> generationRequired(Predicate<E> generationRequired) {
            this.generationRequired = generationRequired;
            return this;
        }

        /** Sets the accessor for the persisted action unit owning the configuration and counter namespace. */
        public Builder<E> actionUnit(Function<E, ActionUnit> actionUnit) {
            this.actionUnit = actionUnit;
            return this;
        }

        /** Sets the optional type/category concept-ID accessor; the default accessor returns {@code null}. */
        public Builder<E> typeId(Function<E, Long> typeId) {
            this.typeId = typeId;
            return this;
        }

        /** Adds a resolver token accessor. Do not add the table's own numerical token. */
        public Builder<E> displayValue(String token, Function<E, ?> accessor) {
            displayValues.put(token, accessor);
            return this;
        }

        /** Adds a canonical counter-partition dimension accessor. Prefer stable database IDs for relationships. */
        public Builder<E> partitionValue(String dimension, Function<E, ?> accessor) {
            partitionValues.put(dimension, accessor);
            return this;
        }

        /** Sets the project-scoped check used to reject an already persisted rendered identifier. */
        public Builder<E> identifierAlreadyUsed(BiPredicate<E, String> identifierAlreadyUsed) {
            this.identifierAlreadyUsed = identifierAlreadyUsed;
            return this;
        }

        /** Sets the entity setter receiving the raw number allocated by the counter. */
        public Builder<E> numberSetter(ObjIntConsumer<E> numberSetter) {
            this.numberSetter = numberSetter;
            return this;
        }

        /** Sets the entity setter receiving the fully rendered identifier. */
        public Builder<E> identifierSetter(BiConsumer<E, String> identifierSetter) {
            this.identifierSetter = identifierSetter;
            return this;
        }

        /** Builds an immutable specification and validates all mandatory attributes. */
        public IdentifierGenerationSpec<E> build() {
            return new IdentifierGenerationSpec<>(table, entityName, generationRequired, actionUnit, typeId,
                    displayValues, partitionValues, identifierAlreadyUsed, numberSetter, identifierSetter);
        }
    }
}
