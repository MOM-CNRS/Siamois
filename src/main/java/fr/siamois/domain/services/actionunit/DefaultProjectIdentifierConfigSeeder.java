package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.study.form.StudyDefaults;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives every newly created project a first type for each of its tables, so a table is never without
 * one (there is no configuration standing for "any type"):
 * <ul>
 *   <li>recording units: "US", whose concept is downloaded from the system thesaurus (th337) and
 *       persisted locally if not already there;</li>
 *   <li>the other tables: one type of the system thesaurus (e.g. "Céramique" for the finds), whose concept
 *       URI is set in {@code siamois.default-types.*}. A table whose URI is not set gets no type, and its
 *       types are added from the settings.</li>
 * </ul>
 * A step whose thesaurus is unreachable, or whose project has no vocabulary configured yet for the
 * relevant field, is skipped rather than failing project creation.
 * <p>The table configuration is read and written in the context of a user (their institution and language),
 * which a REST request does not bind to its thread the way a JSF one does: the caller hands it over, and it
 * is bound here for the duration of the seeding.</p>
 * <p>The seeding runs after the creation's transaction committed, when its entity manager is still bound but
 * would never flush: the vocabulary and concept of each type are therefore created in a transaction of
 * their own, committed before the form config services (which open theirs) run.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProjectIdentifierConfigSeeder {

    private static final int MIN_CODE = 1;
    private static final int MAX_CODE = 999;

    private record DefaultType(String uri, String identifierFormat) {
    }

    private static final List<DefaultType> DEFAULT_RECORDING_UNIT_TYPES = List.of(
            new DefaultType("https://thesaurus.mom.fr/?idc=1199&idt=th337", "US{NUM_UE:00}")
    );

    private final VocabularyService vocabularyService;
    private final ConceptService conceptService;
    private final TableFieldConfigService tableFieldConfigService;
    private final PlatformTransactionManager transactionManager;

    /** The concept of each table's first type in the system thesaurus: "Céramique"… */
    @Value("${siamois.default-types.mobilier:}")
    private String mobilierTypeUri;
    @Value("${siamois.default-types.phase:}")
    private String phaseTypeUri;
    @Value("${siamois.default-types.contenant:}")
    private String contenantTypeUri;
    @Value("${siamois.default-types.document:}")
    private String documentTypeUri;

    /** The concept URI of each default study type, by {@link StudyDefaults.DefaultStudyType#key()}. */
    @Value("${siamois.default-types.study.mobilier:}")
    private String studyFindTypeUri;
    @Value("${siamois.default-types.study.ue:}")
    private String studyRecordingUnitTypeUri;
    @Value("${siamois.default-types.study.traitement:}")
    private String studyTreatmentTypeUri;

    public void seed(UserInfo info, Long projectId) {
        UserInfo previous = ExecutionContextHolder.get();
        ExecutionContextHolder.set(info);
        try {
            seedAll(projectId);
        } finally {
            if (previous != null) {
                ExecutionContextHolder.set(previous);
            } else {
                ExecutionContextHolder.clear();
            }
        }
    }

    /**
     * Finds or creates the concept of a URI (and its vocabulary) in a transaction of its own, committed on
     * return: the form config services open transactions of their own, which must see the concept.
     */
    private Long conceptIdOf(String uri) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            try {
                Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(uri);
                return conceptService.saveOrGetConceptFromUri(vocabulary, uri, null).getId();
            } catch (InvalidEndpointException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void seedAll(Long projectId) {
        seedTypes(projectId, ConfigurableTable.UE, DEFAULT_RECORDING_UNIT_TYPES);
        seedStandardType(projectId, ConfigurableTable.MOBILIER, mobilierTypeUri);
        seedStandardType(projectId, ConfigurableTable.PHASE, phaseTypeUri);
        seedStandardType(projectId, ConfigurableTable.CONTENANT, contenantTypeUri);
        seedStandardType(projectId, ConfigurableTable.DOCUMENT, documentTypeUri);
        seedStudyType(projectId, StudyDefaults.FIND_STUDY, studyFindTypeUri);
        seedStudyType(projectId, StudyDefaults.RECORDING_UNIT_STUDY, studyRecordingUnitTypeUri);
        seedStudyType(projectId, StudyDefaults.TREATMENT, studyTreatmentTypeUri);
    }

    /**
     * Creates one of the default study types and switches its fields on or off: the study table's fields
     * are shared by its types, each type using only some of them.
     */
    private void seedStudyType(Long projectId, StudyDefaults.DefaultStudyType type, String uri) {
        if (uri == null || uri.isBlank()) {
            log.debug("No {} study type configured: the project starts without it", type.key());
            return;
        }
        ConfigurableTable table = ConfigurableTable.STUDY;
        try {
            Long conceptId = conceptIdOf(uri.trim());
            tableFieldConfigService.saveFormConfig(projectId, table, conceptId, TypeFormConfig.builder()
                    .identifierFormat(table.getDefaultIdentifierFormat())
                    .minCode(MIN_CODE)
                    .maxCode(MAX_CODE)
                    .build());
            for (String binding : StudyDefaults.ALL_BINDINGS) {
                String label = StudyDefaults.labelOf(binding);
                tableFieldConfigService.setFieldActive(projectId, table, conceptId, label, type.isActive(binding));
                tableFieldConfigService.setFieldMandatory(projectId, table, conceptId, label, type.isMandatory(binding));
            }
        } catch (Exception e) {
            log.warn("Could not seed the default study type {} from {} for project {}",
                    type.key(), uri, projectId, e);
        }
    }

    private void seedStandardType(Long projectId, ConfigurableTable table, String uri) {
        if (uri == null || uri.isBlank()) {
            log.debug("No standard type configured for table {}: the project starts without one", table);
            return;
        }
        List<DefaultType> types = new ArrayList<>();
        types.add(new DefaultType(uri.trim(), table.getDefaultIdentifierFormat()));
        seedTypes(projectId, table, types);
    }

    private void seedTypes(Long projectId, ConfigurableTable table, List<DefaultType> types) {
        for (DefaultType type : types) {
            try {
                Long conceptId = conceptIdOf(type.uri());
                TypeFormConfig config = TypeFormConfig.builder()
                        .identifierFormat(type.identifierFormat())
                        .minCode(MIN_CODE)
                        .maxCode(MAX_CODE)
                        .build();
                tableFieldConfigService.saveFormConfig(projectId, table, conceptId, config);
            } catch (Exception e) {
                log.warn("Could not seed the default {} type from {} for project {}",
                        table, type.uri(), projectId, e);
            }
        }
    }
}
