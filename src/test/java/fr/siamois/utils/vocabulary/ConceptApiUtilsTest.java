package fr.siamois.utils.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConceptApiUtilsTest {

    @Mock
    private ConceptApi conceptApi;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    private final Vocabulary vocabulary = new Vocabulary();

    private ConceptApiUtils.BranchLoadComponents components() {
        return new ConceptApiUtils.BranchLoadComponents(conceptApi, conceptService, conceptRepository, conceptHierarchyRepository);
    }

    private FullInfoDTO conceptWithNarrower(String... narrowerUrls) {
        FullInfoDTO dto = new FullInfoDTO();
        PurlInfoDTO[] narrower = new PurlInfoDTO[narrowerUrls.length];
        for (int i = 0; i < narrowerUrls.length; i++) {
            PurlInfoDTO purl = new PurlInfoDTO();
            purl.setValue(narrowerUrls[i]);
            narrower[i] = purl;
        }
        dto.setNarrower(narrower);
        return dto;
    }

    /**
     * {@link Concept#equals} compares {@code externalId}/{@code vocabulary}, not {@code id} — two
     * concepts built with the same externalId would be considered the same concept by
     * {@code createRelations}' self-reference check, so each test concept needs its own externalId.
     */
    private Concept conceptWithId(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setVocabulary(vocabulary);
        concept.setExternalId("concept-" + id);
        return concept;
    }

    @Test
    void saveAllConceptsOfBranch_shouldCreateTheHierarchyRelation_whenBothConceptsAreInTheBranch() {
        String parentUrl = "https://example.org/parent";
        String childUrl = "https://example.org/child";
        ConceptBranchDTO branch = new ConceptBranchDTO();
        branch.addConceptBranchDTO(parentUrl, conceptWithNarrower(childUrl));
        branch.addConceptBranchDTO(childUrl, new FullInfoDTO());

        Concept parent = conceptWithId(1L);
        Concept child = conceptWithId(2L);
        when(conceptService.saveOrGetConceptsFromFullDTOs(vocabulary, branch.getData(), null))
                .thenReturn(Map.of(parentUrl, parent, childUrl, child));

        ConceptApiUtils.saveAllConceptsOfBranch(components(), vocabulary, branch);

        verify(conceptHierarchyRepository).saveAll(argThat(links -> {
            List<ConceptHierarchy> list = new ArrayList<>();
            links.forEach(list::add);
            return list.size() == 1 && list.get(0).getParent() == parent && list.get(0).getChild() == child;
        }));
    }

    @Test
    void saveAllConceptsOfBranch_shouldNotDuplicateTheHierarchyRelation_whenItIsAlreadyRecorded() {
        String parentUrl = "https://example.org/parent";
        String childUrl = "https://example.org/child";
        ConceptBranchDTO branch = new ConceptBranchDTO();
        branch.addConceptBranchDTO(parentUrl, conceptWithNarrower(childUrl));
        branch.addConceptBranchDTO(childUrl, new FullInfoDTO());

        Concept parent = conceptWithId(1L);
        Concept child = conceptWithId(2L);
        when(conceptService.saveOrGetConceptsFromFullDTOs(vocabulary, branch.getData(), null))
                .thenReturn(Map.of(parentUrl, parent, childUrl, child));
        List<Object[]> recorded = new ArrayList<>();
        recorded.add(new Object[]{1L, 2L});
        when(conceptHierarchyRepository.findLinkIdsBelowOutsideFieldContext(any())).thenReturn(recorded);

        ConceptApiUtils.saveAllConceptsOfBranch(components(), vocabulary, branch);

        verify(conceptHierarchyRepository).saveAll(argThat(links -> !links.iterator().hasNext()));
    }

    @Test
    void saveAllConceptsOfBranch_shouldSkipTheRelation_whenTheNarrowerConceptIsOutsideTheFetchedBranch() {
        // a collection is a curated subset of the thesaurus : one of its concepts can have a narrower
        // concept belonging to a different collection, so that child is never part of this response —
        // this must not be treated as corrupt data
        String parentUrl = "https://example.org/parent";
        String outsideChildUrl = "https://example.org/outside-child";
        ConceptBranchDTO branch = new ConceptBranchDTO();
        branch.addConceptBranchDTO(parentUrl, conceptWithNarrower(outsideChildUrl));

        Concept parent = conceptWithId(1L);
        when(conceptService.saveOrGetConceptsFromFullDTOs(vocabulary, branch.getData(), null))
                .thenReturn(Map.of(parentUrl, parent));

        assertThatCode(() -> ConceptApiUtils.saveAllConceptsOfBranch(components(), vocabulary, branch))
                .doesNotThrowAnyException();

        verify(conceptHierarchyRepository).saveAll(argThat(links -> !links.iterator().hasNext()));
    }

    @Test
    void saveAllConceptsOfBranch_shouldReturnEveryFetchedConcept_keyedByItsUrl() {
        String url = "https://example.org/only";
        ConceptBranchDTO branch = new ConceptBranchDTO();
        branch.addConceptBranchDTO(url, new FullInfoDTO());
        Concept concept = conceptWithId(3L);
        when(conceptService.saveOrGetConceptsFromFullDTOs(vocabulary, branch.getData(), null)).thenReturn(Map.of(url, concept));

        Map<String, Concept> result = ConceptApiUtils.saveAllConceptsOfBranch(components(), vocabulary, branch);

        assertThat(result).containsEntry(url, concept);
    }
}
