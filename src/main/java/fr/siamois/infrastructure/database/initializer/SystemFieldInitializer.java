package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gives every system field the application defines a row of its own, once per instance.
 * <p>
 * The fields themselves are defined in code (see {@link SystemFieldCatalog}), not in the database.
 * The row this creates carries no definition: it is the identity the rest of the database refers to
 * them by — {@code field_form_config} links a field to a form configuration by foreign key, so a
 * field with no row of its own could not be activated, made mandatory or ordered for a type.
 * <p>
 * The same row serves every project of every institution: a system field is the application's, not
 * an institution's. What each project does with it is what its configurations hold, and those are
 * per project already.
 * <p>
 * Each definition carries its own fixed (negative) id, assigned in code and persisted as-is by
 * {@link fr.siamois.infrastructure.database.id.AssignedOrSequenceIdGenerator} — so this checks and
 * keys existing rows by id rather than by label/binding: two definitions are the same field
 * precisely when they share an id, and a definition changing its label no longer orphans its row.
 * <p>
 * A measurement field's row also carries the default unit its definition declares — without it the
 * field offers no unit to enter a value in, and its answers cannot be stored. The definitions' units
 * are built in code, so the row gets the catalogue unit of the same symbol and dimension, hence this
 * runs after the catalogue is seeded. A row that has no unit yet (one created before units were
 * persisted) is given it; one whose unit was chosen since keeps it.
 */
@Slf4j
@Service
@Order(1) // after UnitDefinitionInitializer (0): a measurement field's row refers to its unit
@RequiredArgsConstructor
public class SystemFieldInitializer implements DatabaseInitializer {

    /**
     * Associations of the definitions that have no counterpart to point at: their concepts and
     * units are built in code and were never persisted, and an author is meaningless for a field
     * the application defines. Copying them would make the insert fail on a transient reference;
     * the unit is resolved against the catalogue instead (see {@link #withUnit}).
     */
    private static final String[] NOT_PERSISTABLE = {"concept", "author", "unit", "measurementNature"};

    private final CustomFieldRepository customFieldRepository;
    private final UnitDefinitionService unitDefinitionService;

    @Override
    @Transactional(rollbackFor = DatabaseDataInitException.class)
    public void initialize() throws DatabaseDataInitException {
        Map<Long, CustomField> existing = new HashMap<>();
        customFieldRepository.findAllSystemFields()
                .forEach(field -> existing.putIfAbsent(field.getId(), field));

        int created = 0;
        int unitsSet = 0;
        for (ConfigurableTable table : ConfigurableTable.projectScopedTables()) {
            for (CustomField definition : SystemFieldCatalog.fieldsOf(table)) {
                Long id = Objects.requireNonNull(definition.getId(),
                        () -> "System field '" + definition.getLabel() + "' of table " + table + " has no id");
                CustomField row = existing.get(id);
                if (row == null) {
                    existing.put(id, customFieldRepository.save(withUnit(rowFor(definition), definition)));
                    created++;
                } else if (lacksUnit(row, definition)) {
                    customFieldRepository.save(withUnit(row, definition));
                    unitsSet++;
                }
            }
        }
        log.info("System fields initialized: {} created, {} given their default unit, {} known in total",
                created, unitsSet, existing.size());
    }

    /** Whether the row of a measurement field is missing the unit its definition declares. */
    private static boolean lacksUnit(CustomField row, CustomField definition) {
        return definition instanceof CustomFieldMeasurement measurement && measurement.getUnit() != null
                && Hibernate.unproxy(row) instanceof CustomFieldMeasurement stored && stored.getUnit() == null;
    }

    /** The row, given the catalogue unit its definition declares when it is a measurement field. */
    private CustomField withUnit(CustomField row, CustomField definition) throws DatabaseDataInitException {
        if (!(definition instanceof CustomFieldMeasurement measurement) || measurement.getUnit() == null
                || !(Hibernate.unproxy(row) instanceof CustomFieldMeasurement stored)) {
            return row;
        }
        try {
            UnitDefinition unit = unitDefinitionService.resolve(measurement.getUnit());
            stored.setUnit(unit);
        } catch (IllegalStateException e) {
            throw new DatabaseDataInitException("Unit of system field " + definition.getId() + " is not seeded", e);
        }
        return row;
    }

    /**
     * A persistable copy of a definition. The subclass is kept — it is what the {@code answer_type}
     * discriminator and every {@code instanceof} dispatch elsewhere read the field's type from — and
     * so are its own columns, hence the property copy rather than a hand-written field list. The id
     * is kept too, so {@link fr.siamois.infrastructure.database.id.AssignedOrSequenceIdGenerator}
     * persists it as-is instead of drawing a new one from the sequence.
     */
    private CustomField rowFor(CustomField definition) throws DatabaseDataInitException {
        try {
            CustomField row = definition.getClass().getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(definition, row, NOT_PERSISTABLE);
            return row;
        } catch (ReflectiveOperationException e) {
            throw new DatabaseDataInitException(
                    "Cannot instantiate system field " + definition.getClass().getName(), e);
        }
    }

}
