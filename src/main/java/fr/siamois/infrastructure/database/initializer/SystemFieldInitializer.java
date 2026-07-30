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
    private static final String[] NOT_PERSISTABLE = {"id", "concept", "author", "unit", "measurementNature"};

    private final CustomFieldRepository customFieldRepository;

    @Override
    @Transactional(rollbackFor = DatabaseDataInitException.class)
    public void initialize() throws DatabaseDataInitException {
        Map<String, CustomField> existing = new HashMap<>();
        customFieldRepository.findAllSystemFields()
                .forEach(field -> existing.putIfAbsent(SystemFieldCatalog.identityOf(field), field));

        int created = 0;
        for (ConfigurableTable table : ConfigurableTable.values()) {
            for (CustomField definition : SystemFieldCatalog.fieldsOf(table)) {
                String identity = SystemFieldCatalog.identityOf(definition);
                if (existing.containsKey(identity)) {
                    continue;
                }
                existing.put(identity, customFieldRepository.save(rowFor(definition)));
                created++;
            }
        }
        log.info("System fields initialized: {} created, {} known in total", created, existing.size());
    }

    /**
     * A persistable copy of a definition. The subclass is kept — it is what the {@code answer_type}
     * discriminator and every {@code instanceof} dispatch elsewhere read the field's type from — and
     * so are its own columns, hence the property copy rather than a hand-written field list.
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
