package fr.siamois.utils.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ConceptApiUtils {

    public static final String IDC = "idc=";

    private ConceptApiUtils() {
        throw new UnsupportedOperationException("ConceptApiUtils should never be instantiated");
    }

    /**
     * The external id of a concept, read from its URI in the thesaurus : the ark identifier when the
     * thesaurus is ark based, the {@code idc} query parameter otherwise. This is the identifier concepts
     * are stored and looked up with locally, see {@code Concept.externalId}.
     *
     * @param uri the URI of the concept in its thesaurus
     * @return the external id of the concept, or null when the URI carries none
     */
    @Nullable
    public static String externalIdFromUri(@Nullable String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        if (uri.contains("ark:")) {
            return uri.substring(uri.indexOf("ark:"));
        }
        try {
            int indexStart = uri.indexOf(IDC) + IDC.length();
            int indexEnd = indexStart + 1;
            while (indexEnd < uri.length() && uri.charAt(indexEnd) != '&') {
                indexEnd++;
            }
            return uri.substring(indexStart, indexEnd);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO, @NonNull Map<String, Concept> urlSavedConcept) {
        saveAllConceptFromBranch(components, vocabulary, branchDTO, urlSavedConcept);
        for (Map.Entry<String, FullInfoDTO> info : branchDTO.getData().entrySet()) {
            FullInfoDTO fullInfoDTO = info.getValue();
            if (Objects.nonNull(fullInfoDTO.getNarrower())) {
                createRelations(components, info, fullInfoDTO, urlSavedConcept);
            }
            if (Objects.nonNull(fullInfoDTO.getRelated())) {
                createRelatedConceptsRelations(components, vocabulary, info, urlSavedConcept, fullInfoDTO);
            }
        }
        return urlSavedConcept;
    }

    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO) {
        return saveAllConceptsOfBranch(components, vocabulary, branchDTO, new HashMap<>());
    }

    private static void createRelatedConceptsRelations(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, Map.Entry<String, FullInfoDTO> info, @NonNull Map<String, Concept> urlTosavedConcept, @NonNull FullInfoDTO fullInfoDTO) {
        Concept currentConcept = urlTosavedConcept.get(info.getKey());
        // Only a concept loaded by Hibernate carries the collection, one that was just created has none yet
        Set<Concept> relatedConcepts = Objects.isNull(currentConcept.getRelatedConcepts())
                ? new HashSet<>()
                : currentConcept.getRelatedConcepts();
        for (PurlInfoDTO related : fullInfoDTO.getRelated()) {
            FullInfoDTO relatedInfos = utils.conceptApi.fetchConceptInfoByUri(vocabulary, related.getValue());
            relatedConcepts.add(utils.conceptService.saveOrGetConceptFromFullDTO(vocabulary, relatedInfos, null));
        }
        currentConcept.setRelatedConcepts(relatedConcepts);
        urlTosavedConcept.put(info.getKey(), utils.conceptRepository.save(currentConcept));
    }

    private static void saveAllConceptFromBranch(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO dto, Map<String, Concept> savedConcept) {
        for (Map.Entry<String, FullInfoDTO> info : dto.getData().entrySet()) {
            savedConcept.put(info.getKey(), utils.conceptService.saveOrGetConceptFromFullDTO(vocabulary, info.getValue(), null));
        }
    }

    private static void createRelations(BranchLoadComponents utils, Map.Entry<String, FullInfoDTO> info, @NonNull FullInfoDTO fullInfoDTO, Map<String, Concept> savedConcept) {
        for (PurlInfoDTO purlInfoDTO : fullInfoDTO.getNarrower()) {
            Concept parent = savedConcept.get(info.getKey());
            Concept child = savedConcept.get(purlInfoDTO.getValue());
            if (parent == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + info.getKey());
            }
            if (child == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + purlInfoDTO.getValue());
            }
            if (!parent.equals(child)) {
                ConceptHierarchy relation = new ConceptHierarchy(parent, child, null);
                utils.conceptHierarchyRepository.save(relation);
            }
        }
    }

    public record BranchLoadComponents(ConceptApi conceptApi, ConceptService conceptService,
                                       ConceptRepository conceptRepository,
                                       ConceptHierarchyRepository conceptHierarchyRepository) {

        public BranchLoadComponents(@NonNull ApplicationContext context) {
            this(context.getBean(ConceptApi.class), context.getBean(ConceptService.class), context.getBean(ConceptRepository.class), context.getBean(ConceptHierarchyRepository.class));
        }

    }

}
