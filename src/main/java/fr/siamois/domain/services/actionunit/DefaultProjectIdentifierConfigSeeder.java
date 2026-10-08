package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives every newly created project a first type for each of its tables, so a table is never without
 * one (there is no configuration standing for "any type"):
 * <ul>
 *   <li>recording units: "US" (Unité d'enregistrement) and "F" (Unité incluante), whose concepts are
 *       downloaded from the thesaurus and persisted locally if not already there;</li>
 *   <li>every table: the standard type of the system thesaurus (e.g. "UE standard", "Mobilier standard"),
 *       whose concept URI is set in {@code siamois.default-types.*}. A table whose URI is not set gets no
 *       standard type, and its types are added from the settings.</li>
 * </ul>
 * A step whose thesaurus is unreachable, or whose project has no vocabulary configured yet for the
 * relevant field, is skipped rather than failing project creation.
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
            new DefaultType("https://opentheso2.mom.fr/?idc=4287627&idt=th1295", "US{NUM_UE:00}"),
            new DefaultType("https://opentheso2.mom.fr/?idc=4287628&idt=th1295", "F{NUM_UE:00}")
    );

    private final VocabularyService vocabularyService;
    private final ConceptService conceptService;
    private final TableFieldConfigService tableFieldConfigService;

    /** The concept of each table's standard type in the system thesaurus: "Mobilier standard"… */
    @Value("${siamois.default-types.ue:}")
    private String ueTypeUri;
    @Value("${siamois.default-types.mobilier:}")
    private String mobilierTypeUri;
    @Value("${siamois.default-types.phase:}")
    private String phaseTypeUri;
    @Value("${siamois.default-types.contenant:}")
    private String contenantTypeUri;
    @Value("${siamois.default-types.document:}")
    private String documentTypeUri;

    public void seed(Long projectId) {
        seedTypes(projectId, ConfigurableTable.UE, DEFAULT_RECORDING_UNIT_TYPES);
        seedStandardType(projectId, ConfigurableTable.UE, ueTypeUri);
        seedStandardType(projectId, ConfigurableTable.MOBILIER, mobilierTypeUri);
        seedStandardType(projectId, ConfigurableTable.PHASE, phaseTypeUri);
        seedStandardType(projectId, ConfigurableTable.CONTENANT, contenantTypeUri);
        seedStandardType(projectId, ConfigurableTable.DOCUMENT, documentTypeUri);
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
                Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(type.uri());
                Concept concept = conceptService.saveOrGetConceptFromUri(vocabulary, type.uri(), null);
                TypeFormConfig config = TypeFormConfig.builder()
                        .identifierFormat(type.identifierFormat())
                        .minCode(MIN_CODE)
                        .maxCode(MAX_CODE)
                        .build();
                tableFieldConfigService.saveFormConfig(projectId, table, concept.getId(), config);
            } catch (Exception e) {
                log.warn("Could not seed the default {} type from {} for project {}",
                        table, type.uri(), projectId, e);
            }
        }
    }
}
