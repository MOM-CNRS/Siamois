package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.identifier.GeneratedIdentifier;
import fr.siamois.domain.services.identifier.IdentifierPartitionService;
import fr.siamois.domain.services.identifier.IdentifierResolverRegistry;
import fr.siamois.domain.services.identifier.MapIdentifierRenderContext;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.identifier.InstitutionIdentifierCounterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Numbers the projects of an institution ({@code PROJ1}, {@code PROJ2}...), the way the entities of a project
 * are numbered inside it: a token format and a counter, but scoped to the institution. The format lives in an
 * institution-level {@link FormConfig} (no project, no value concept) of the project type field, so it can later
 * be configured per project type from an institution settings screen.
 */
@Service
@RequiredArgsConstructor
public class ProjectIdentifierService {

    static final int DEFAULT_MIN_CODE = 1;
    static final int DEFAULT_MAX_CODE = 999_999;
    private static final ConfigurableTable TABLE = ConfigurableTable.PROJECT;
    /** Advisory lock namespace (high bits) so the lock keys never collide with another feature's. */
    private static final long LOCK_NAMESPACE = 0x5052_4F4A_0000_0000L;

    private final FormConfigRepository formConfigRepository;
    private final FieldConfigurationService fieldConfigurationService;
    private final IdentifierResolverRegistry resolverRegistry;
    private final IdentifierPartitionService partitionService;
    private final InstitutionIdentifierCounterRepository counterRepository;
    private final ActionUnitRepository actionUnitRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Allocates the next free project identifier of the institution.
     *
     * @throws IllegalStateException if the institution has no project type vocabulary configured, or its range is exhausted
     */
    @Transactional
    public GeneratedIdentifier generate(UserInfo info) {
        Long institutionId = info.getInstitution().getId();
        FormConfig config = resolveConfig(info)
                .orElseThrow(() -> new IllegalStateException("No project type vocabulary configured"));
        if (config.getMinCode() < 0 || config.getMaxCode() < config.getMinCode()) {
            throw new IllegalArgumentException("Invalid identifier range on form config " + config.getId());
        }
        String canonicalKey = canonicalKey(config);

        FlushModeType previous = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            while (true) {
                int number = counterRepository.nextValue(institutionId, config.getId(), canonicalKey, config.getMinCode());
                if (number > config.getMaxCode()) {
                    throw new IllegalStateException("Project identifier range exhausted for form config " + config.getId());
                }
                String rendered = render(config.getIdentifierFormat(), number);
                if (!isUsed(institutionId, rendered)) {
                    return new GeneratedIdentifier(number, rendered);
                }
            }
        } finally {
            entityManager.setFlushMode(previous);
        }
    }

    /**
     * The identifier the next project would get, without consuming it (a concurrent creation may take it).
     * Empty when the institution cannot number its projects.
     */
    @Transactional(readOnly = true)
    public Optional<String> preview(UserInfo info) {
        Long institutionId = info.getInstitution().getId();
        Optional<FormConfig> stored = findConfig(info);
        String format = stored.map(FormConfig::getIdentifierFormat).orElse(TABLE.getDefaultIdentifierFormat());
        int min = stored.map(FormConfig::getMinCode).orElse(DEFAULT_MIN_CODE);
        int max = stored.map(FormConfig::getMaxCode).orElse(DEFAULT_MAX_CODE);
        int next = stored
                .flatMap(config -> counterRepository.findCounter(institutionId, config.getId(), canonicalKey(config)))
                .map(counter -> Math.max(counter, min))
                .orElse(min);
        for (int number = next; number <= max; number++) {
            String rendered = render(format, number);
            if (!isUsed(institutionId, rendered)) {
                return Optional.of(rendered);
            }
        }
        return Optional.empty();
    }

    private boolean isUsed(Long institutionId, String identifier) {
        return actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(identifier, institutionId).isPresent();
    }

    private String render(String format, int number) {
        Map<String, Object> values = new HashMap<>();
        values.put(resolverRegistry.ownNumericalToken(TABLE), number);
        return resolverRegistry.render(TABLE, format, new MapIdentifierRenderContext(values, Map.of()));
    }

    private String canonicalKey(FormConfig config) {
        return partitionService.canonicalKey(TABLE, config.getIdentifierFormat(),
                new MapIdentifierRenderContext(new HashMap<>(), Map.of()));
    }

    private Optional<Concept> findFieldConcept(UserInfo info) {
        try {
            return Optional.ofNullable(fieldConfigurationService
                    .findConfigurationForFieldCode(info, TABLE.getFieldCode(), (Long) null).getConcept());
        } catch (NoConfigForFieldException e) {
            return Optional.empty();
        }
    }

    private Optional<FormConfig> findConfig(UserInfo info) {
        return findFieldConcept(info).flatMap(field ->
                formConfigRepository.findInstitutionDefaults(info.getInstitution().getId(), field.getId())
                        .stream().findFirst());
    }

    /** The institution's project numbering config, created with the built-in format on first use. */
    private Optional<FormConfig> resolveConfig(UserInfo info) {
        Optional<Concept> field = findFieldConcept(info);
        if (field.isEmpty()) {
            return Optional.empty();
        }
        Long institutionId = info.getInstitution().getId();
        Long fieldId = field.get().getId();
        Optional<FormConfig> existing = formConfigRepository.findInstitutionDefaults(institutionId, fieldId)
                .stream().findFirst();
        if (existing.isPresent()) {
            return existing;
        }
        // NULLs are never equal in a UNIQUE constraint: serialize the lazy creation of the default row
        formConfigRepository.lockTransaction(LOCK_NAMESPACE | (institutionId & 0xFFFF_FFFFL));
        return Optional.of(formConfigRepository.findInstitutionDefaults(institutionId, fieldId).stream().findFirst()
                .orElseGet(() -> {
                    FormConfig config = new FormConfig();
                    config.setInstitution(entityManager.getReference(Institution.class, institutionId));
                    config.setFieldConcept(field.get());
                    config.setFieldConfigs(new ArrayList<>());
                    config.setIdentifierFormat(TABLE.getDefaultIdentifierFormat());
                    config.setMinCode(DEFAULT_MIN_CODE);
                    config.setMaxCode(DEFAULT_MAX_CODE);
                    return formConfigRepository.save(config);
                }));
    }
}
