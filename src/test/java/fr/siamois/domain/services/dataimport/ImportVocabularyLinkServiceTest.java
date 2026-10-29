package fr.siamois.domain.services.dataimport;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.initializer.seeder.MissingConceptException;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.VocabularyResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportVocabularyLinkServiceTest {

    private static final String NATURE = "SIARU.NATURE";
    private static final String BRANCH = "https://thesaurus.mom.fr/?idc=1180&idt=th337";

    @Mock private ConceptService conceptService;

    private ImportVocabularyLinkService service;
    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        service = new ImportVocabularyLinkService(conceptService);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(3L);
        project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
    }

    private static ImportError vocabularyError(String fieldCode, String typeLabel) {
        return ImportError.forRow("UE", 4, new VocabularyResolutionException(fieldCode, "[colonne 'x'] : introuvable", null), typeLabel);
    }

    private static ImportError missingConcept(String tableId, String conceptUrl) {
        return ImportError.forTable(tableId, new RuntimeException("sauvegarde",
                new MissingConceptException("Concept non chargé dans Siamois", conceptUrl)), "Concept non chargé dans Siamois");
    }

    @Test
    void linksFor_aTypeThatResolves_opensTheConfigurationOnThatType() {
        Concept type = new Concept();
        type.setId(77L);
        when(conceptService.resolveConceptByLabel(3L, ConfigurableTable.UE.getFieldCode(), "US")).thenReturn(type);
        when(conceptService.findFieldBranchUri(3L, 5L, NATURE)).thenReturn(Optional.of(BRANCH));

        var links = service.linksFor(project, vocabularyError(NATURE, "US"), "/siamois");

        assertThat(links.configUrl()).isEqualTo("/siamois/settings/project/5/tables?table=UE&typeId=77");
        assertThat(links.thesaurusUrl()).isEqualTo(BRANCH);
    }

    @Test
    void linksFor_noTypeOnTheRow_opensTheConfigurationOfTheTable() {
        when(conceptService.findFieldBranchUri(3L, 5L, NATURE)).thenReturn(Optional.of(BRANCH));

        var links = service.linksFor(project, vocabularyError(NATURE, null), "");

        assertThat(links.configUrl()).isEqualTo("/settings/project/5/tables?table=UE");
    }

    @Test
    void linksFor_aTypeThatDoesNotResolve_fallsBackToTheTable() {
        when(conceptService.resolveConceptByLabel(3L, ConfigurableTable.UE.getFieldCode(), "Inconnu"))
                .thenThrow(new IllegalStateException("introuvable"));
        when(conceptService.findFieldBranchUri(3L, 5L, NATURE)).thenReturn(Optional.empty());

        var links = service.linksFor(project, vocabularyError(NATURE, "Inconnu"), "");

        assertThat(links.configUrl()).isEqualTo("/settings/project/5/tables?table=UE");
        assertThat(links.thesaurusUrl()).isNull();
    }

    @Test
    void linksFor_theFailingLabelIsTheTypeItself_neverLooksTheTypeUp() {
        when(conceptService.findFieldBranchUri(3L, 5L, ConfigurableTable.UE.getFieldCode())).thenReturn(Optional.of(BRANCH));

        var links = service.linksFor(project, vocabularyError(ConfigurableTable.UE.getFieldCode(), "Entité"), "");

        assertThat(links.configUrl()).isEqualTo("/settings/project/5/tables?table=UE");
    }

    @Test
    void linksFor_aFieldOfNoConfigurableTable_onlyLinksTheThesaurusBranch() {
        when(conceptService.findFieldBranchUri(3L, 5L, "SIASU.TYPE")).thenReturn(Optional.of(BRANCH));

        var links = service.linksFor(project, vocabularyError("SIASU.TYPE", null), "");

        assertThat(links.configUrl()).isNull();
        assertThat(links.thesaurusUrl()).isEqualTo(BRANCH);
        assertThat(links.hasAny()).isTrue();
    }

    @Test
    void linksFor_anErrorThatIsNotAboutVocabulary_hasNoLinks() {
        var links = service.linksFor(project, new ImportError("UE", 2, "taq", "pas un nombre"), "");

        assertThat(links.hasAny()).isFalse();
    }

    @Test
    void linksFor_aConceptNotLoaded_linksTheTableAndTheConcept() {
        var links = service.linksFor(project, missingConcept("recording_unit", "https://thesaurus.mom.fr/?idc=4287627&idt=th252"), "");

        assertThat(links.configUrl()).isEqualTo("/settings/project/5/tables?table=UE");
        assertThat(links.thesaurusUrl()).isEqualTo("https://thesaurus.mom.fr/?idc=4287627&idt=th252");
    }

    @Test
    void linksFor_aConceptNotLoadedWithoutKnownThesaurus_stillLinksTheTable() {
        var links = service.linksFor(project, missingConcept("specimen", null), "");

        assertThat(links.configUrl()).isEqualTo("/settings/project/5/tables?table=MOBILIER");
        assertThat(links.thesaurusUrl()).isNull();
    }

    @Test
    void linksFor_aConceptNotLoadedOfARelationSheet_onlyLinksTheConcept() {
        var links = service.linksFor(project, missingConcept("stratiRel", "https://thesaurus.mom.fr/?idc=1&idt=th337"), "");

        assertThat(links.configUrl()).isNull();
        assertThat(links.thesaurusUrl()).isEqualTo("https://thesaurus.mom.fr/?idc=1&idt=th337");
    }

    @Test
    void forTable_aFailureWithoutMissingConcept_isNotAVocabularyError() {
        ImportError error = ImportError.forTable("phase", new IllegalStateException("autre"), "autre");

        assertThat(error.isVocabularyError()).isFalse();
    }

    @Test
    void ofFieldCode_findsTheTableOfAField() {
        assertThat(ConfigurableTable.ofFieldCode("SIARU.NATURE")).isEqualTo(ConfigurableTable.UE);
        assertThat(ConfigurableTable.ofFieldCode("SIAS.MATIERE")).isEqualTo(ConfigurableTable.MOBILIER);
        assertThat(ConfigurableTable.ofFieldCode("SIAPHASE.TYPE")).isEqualTo(ConfigurableTable.PHASE);
        assertThat(ConfigurableTable.ofFieldCode("SIASU.TYPE")).isNull();
        assertThat(ConfigurableTable.ofFieldCode(null)).isNull();
    }
}
