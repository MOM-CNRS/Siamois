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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Gives the row of every system measurement field (altitudes, weights, container dimensions) the
 * default unit its definition declares.
 * <p>
 * {@link SystemFieldInitializer} cannot: it runs before the unit catalogue is seeded, and the
 * definitions' units are built in code, with no id. Without a unit on its row, a measurement field
 * offers no unit to enter a value in and its answers cannot be stored (a measurement needs one).
 * <p>
 * Only a row with no unit is filled, so a unit chosen since is kept.
 */
@Slf4j
@Service
@Order(1) // after UnitDefinitionInitializer (0): the units must exist
@RequiredArgsConstructor
public class SystemFieldUnitInitializer implements DatabaseInitializer {

    private final CustomFieldRepository customFieldRepository;
    private final UnitDefinitionService unitDefinitionService;

    @Override
    @Transactional(rollbackFor = DatabaseDataInitException.class)
    public void initialize() throws DatabaseDataInitException {
        Map<Long, CustomField> rows = new HashMap<>();
        customFieldRepository.findAllSystemFields().forEach(field -> rows.putIfAbsent(field.getId(), field));

        int filled = 0;
        for (ConfigurableTable table : ConfigurableTable.projectScopedTables()) {
            for (CustomField definition : SystemFieldCatalog.fieldsOf(table)) {
                if (!(definition instanceof CustomFieldMeasurement measurement) || measurement.getUnit() == null) continue;
                if (Hibernate.unproxy(rows.get(definition.getId())) instanceof CustomFieldMeasurement row
                        && row.getUnit() == null) {
                    row.setUnit(resolve(measurement));
                    customFieldRepository.save(row);
                    filled++;
                }
            }
        }
        log.info("System measurement fields: {} given their default unit", filled);
    }

    private UnitDefinition resolve(CustomFieldMeasurement definition)
            throws DatabaseDataInitException {
        try {
            return unitDefinitionService.resolve(definition.getUnit());
        } catch (IllegalStateException e) {
            throw new DatabaseDataInitException("Unit of system field " + definition.getId() + " is not seeded", e);
        }
    }
}
