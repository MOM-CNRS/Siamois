package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.CodeSourceService;
import fr.siamois.domain.services.vocabulary.FieldCodeResolver;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Configures a new project from the {@link DefaultProjectDefinition}: for each table, the types the definition names, and for
 * each type the vocabulary of its fields. A field's list for a type is, in order:
 * <ol>
 *   <li>the collection the definition names for this field and this type;</li>
 *   <li>otherwise the subtree of the field's own code in the thesaurus, when the thesaurus defines it.</li>
 * </ol>
 * The types of each table are redirected to the thesaurus ({@link CodeSourceService}), so they are its candidates.
 *
 * <p>Nothing here reads the thesaurus: it was imported when the application started
 * ({@code DefaultProjectThesaurusInitializer}). A step whose concept, collection or code is not in the local database is
 * skipped rather than failing project creation, which has already committed when this runs.
 *
 * <p>The seeding runs after the creation's transaction committed, when its entity manager is still bound but would never
 * flush: a service that joins that transaction writes nothing. Each table's code source and each type is therefore written
 * in a transaction of its own, so one failing type loses only itself.
 *
 * <p>The table configuration is read and written in the context of a user (their institution), which a REST request does
 * not bind to its thread the way a JSF one does: the caller hands it over, and it is bound here for the seeding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThesaurusProjectConfigSeeder {

    private static final int MIN_CODE = 1;
    private static final int MAX_CODE = 999;

    private final DefaultProjectDefinition definition;
    private final VocabularyRepository vocabularyRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptCollectionRepository conceptCollectionRepository;
    private final FieldCodeResolver fieldCodeResolver;
    private final CodeSourceService codeSourceService;
    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigService formConfigService;
    private final PlatformTransactionManager transactionManager;

    public void seed(UserInfo info, Long projectId) {
        UserInfo previous = ExecutionContextHolder.get();
        ExecutionContextHolder.set(info);
        try {
            seedAll(projectId);
        } catch (RuntimeException e) {
            log.warn("The default project {} could not be configured from {}", projectId, definition.thesaurusUrl(), e);
        } finally {
            if (previous != null) {
                ExecutionContextHolder.set(previous);
            } else {
                ExecutionContextHolder.clear();
            }
        }
    }

    private void seedAll(Long projectId) {
        Optional<Vocabulary> vocabulary = vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(
                definition.baseUri(), definition.externalVocabularyId());
        if (vocabulary.isEmpty()) {
            log.warn("Thesaurus {} is not imported: project {} starts without the default configuration",
                    definition.thesaurusUrl(), projectId);
            return;
        }
        for (DefaultProjectDefinition.Table table : definition.tables()) {
            seedTable(projectId, vocabulary.get(), table);
        }
    }

    private void seedTable(Long projectId, Vocabulary vocabulary, DefaultProjectDefinition.Table definitionTable) {
        ConfigurableTable table = definitionTable.table();
        if (fieldCodeResolver.findIn(vocabulary, definitionTable.typeCode()).isEmpty()) {
            log.warn("Thesaurus {} does not define {}: the {} table of project {} starts without types",
                    definition.thesaurusUrl(), definitionTable.typeCode(), table, projectId);
            return;
        }
        inNewTransaction(() -> codeSourceService.useFieldCode(projectId, definitionTable.typeCode(), vocabulary, definitionTable.typeCode()));
        List<CustomFieldConceptFromFieldCode> vocabularyFields = vocabularyFieldsOf(table);
        for (DefaultProjectDefinition.Type type : definitionTable.types()) {
            try {
                inNewTransaction(() -> seedType(projectId, vocabulary, table, type, vocabularyFields));
            } catch (RuntimeException e) {
                log.warn("Could not seed the default {} type '{}' for project {}", table, type.label(), projectId, e);
            }
        }
    }

    private void seedType(Long projectId, Vocabulary vocabulary, ConfigurableTable table, DefaultProjectDefinition.Type type,
                          List<CustomFieldConceptFromFieldCode> vocabularyFields) {
        Optional<Concept> concept = conceptRepository.findConceptByExternalIdIgnoreCase(vocabulary.getExternalVocabularyId(), type.concept());
        if (concept.isEmpty()) {
            log.warn("Type '{}' ({}) of table {} is not in the local copy of {}: not seeded",
                    type.label(), type.concept(), table, definition.thesaurusUrl());
            return;
        }
        Long typeConceptId = concept.get().getId();
        tableFieldConfigService.saveFormConfig(projectId, table, typeConceptId, TypeFormConfig.builder()
                .identifierFormat(type.identifierFormat() != null ? type.identifierFormat() : table.getDefaultIdentifierFormat())
                .minCode(MIN_CODE)
                .maxCode(MAX_CODE)
                .build());
        Optional<FormConfig> formConfig = tableFieldConfigService.createOrGetFormConfig(projectId, table, typeConceptId);
        if (formConfig.isEmpty()) {
            return;
        }
        for (CustomFieldConceptFromFieldCode spec : vocabularyFields) {
            seedField(projectId, vocabulary, table, typeConceptId, type, formConfig.get(), spec);
        }
    }

    private void seedField(Long projectId, Vocabulary vocabulary, ConfigurableTable table, Long typeConceptId,
                           DefaultProjectDefinition.Type type, FormConfig formConfig, CustomFieldConceptFromFieldCode spec) {
        String collectionId = type.lists().get(spec.getFieldCode());
        Optional<ConceptCollection> collection = collectionId == null
                ? Optional.empty()
                : conceptCollectionRepository.findByVocabularyAndExternalId(vocabulary, collectionId);
        if (collectionId != null && collection.isEmpty()) {
            log.warn("Collection {} of {} is not in the local copy: the field {} of type '{}' reads its whole list",
                    collectionId, definition.thesaurusUrl(), spec.getFieldCode(), type.label());
        }
        boolean definedByThesaurus = fieldCodeResolver.findIn(vocabulary, spec.getFieldCode()).isPresent();
        if (collection.isEmpty() && !definedByThesaurus) {
            return;
        }
        Optional<CustomField> field = tableFieldConfigService.findField(projectId, table, typeConceptId, spec.getLabel());
        if (field.isEmpty() || !(field.get() instanceof CustomFieldConcept conceptField)) {
            log.warn("No vocabulary field '{}' on type '{}' of table {}", spec.getLabel(), type.label(), table);
            return;
        }
        if (collection.isPresent()) {
            formConfigService.addConceptConfigFor(formConfig, conceptField, collection.get());
        } else {
            formConfigService.addFieldCodeConfigFor(formConfig, conceptField, vocabulary, spec.getFieldCode());
        }
    }

    /** The vocabulary fields of a table other than its type, whose list the thesaurus can define. */
    private static List<CustomFieldConceptFromFieldCode> vocabularyFieldsOf(ConfigurableTable table) {
        return SystemFieldCatalog.specsOf(table).stream()
                .map(spec -> spec.field())
                .filter(CustomFieldConceptFromFieldCode.class::isInstance)
                .map(CustomFieldConceptFromFieldCode.class::cast)
                .filter(field -> field.getFieldCode() != null && !field.getFieldCode().equals(table.getFieldCode()))
                .toList();
    }

    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }
}
