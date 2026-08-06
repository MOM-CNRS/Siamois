package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
@Order(-8)
@RequiredArgsConstructor
public class SystemFieldInitializer implements DatabaseInitializer {

    /**
     * Associations of the definitions that have no counterpart to point at: their concepts and
     * units are built in code and were never persisted, and an author is meaningless for a field
     * the application defines. Copying them would make the insert fail on a transient reference.
     */
    private static final String[] NOT_PERSISTABLE = {"concept", "author", "unit", "measurementNature"};

    private final CustomFieldRepository customFieldRepository;

    @Override
    @Transactional(rollbackFor = DatabaseDataInitException.class)
    public void initialize() throws DatabaseDataInitException {
        Map<Long, CustomField> existing = new HashMap<>();
        customFieldRepository.findAllSystemFields()
                .forEach(field -> existing.putIfAbsent(field.getId(), field));

        int created = 0;
        for (ConfigurableTable table : ConfigurableTable.values()) {
            for (CustomField definition : SystemFieldCatalog.fieldsOf(table)) {
                Long id = Objects.requireNonNull(definition.getId(),
                        () -> "System field '" + definition.getLabel() + "' of table " + table + " has no id");
                if (existing.containsKey(id)) {
                    continue;
                }
                existing.put(id, customFieldRepository.save(rowFor(definition)));
                created++;
            }
        }
        log.info("System fields initialized: {} created, {} known in total", created, existing.size());
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
