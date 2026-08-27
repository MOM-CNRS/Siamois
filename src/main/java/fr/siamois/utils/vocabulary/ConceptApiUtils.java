package fr.siamois.utils.vocabulary;

import fr.siamois.domain.models.misc.ProgressWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
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
        int idcIndex = uri.indexOf(IDC);
        if (idcIndex < 0) {
            return null;
        }
        int indexStart = idcIndex + IDC.length();
        int indexEnd = indexStart;
        while (indexEnd < uri.length() && uri.charAt(indexEnd) != '&') {
            indexEnd++;
        }
        String externalId = uri.substring(indexStart, indexEnd);
        return externalId.isBlank() ? null : externalId;
    }

    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO, @NonNull Map<String, Concept> urlSavedConcept) {
        return saveAllConceptsOfBranch(components, vocabulary, branchDTO, urlSavedConcept, null);
    }

    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO) {
        return saveAllConceptsOfBranch(components, vocabulary, branchDTO, new HashMap<>(), null);
    }

    /**
     * Same as {@link #saveAllConceptsOfBranch(BranchLoadComponents, Vocabulary, ConceptBranchDTO, Map)},
     * reporting progress on {@code progressWrapper} as it goes — one step per concept saved, plus one
     * per narrower/related link processed (whether or not it actually results in a fetch or a save),
     * since those links, not the initial per-concept save, are what a large collection spends most of
     * its time on. Null is a valid, no-op progress tracker : most callers have no progress bar to
     * drive.
     */
    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO, @NonNull Map<String, Concept> urlSavedConcept, @Nullable ProgressWrapper progressWrapper) {
        if (progressWrapper != null) {
            progressWrapper.reset();
            progressWrapper.setTotalSteps(totalStepsOf(branchDTO));
        }
        saveAllConceptFromBranch(components, vocabulary, branchDTO, urlSavedConcept, progressWrapper);
        for (Map.Entry<String, FullInfoDTO> info : branchDTO.getData().entrySet()) {
            FullInfoDTO fullInfoDTO = info.getValue();
            if (Objects.nonNull(fullInfoDTO.getNarrower())) {
                createRelations(components, info, fullInfoDTO, urlSavedConcept, progressWrapper);
            }
            if (Objects.nonNull(fullInfoDTO.getRelated())) {
                createRelatedConceptsRelations(components, vocabulary, info, urlSavedConcept, fullInfoDTO, progressWrapper);
            }
        }
        return urlSavedConcept;
    }

    private static int totalStepsOf(@NonNull ConceptBranchDTO branchDTO) {
        int steps = branchDTO.getData().size();
        for (FullInfoDTO info : branchDTO.getData().values()) {
            if (info.getNarrower() != null) steps += info.getNarrower().length;
            if (info.getRelated() != null) steps += info.getRelated().length;
        }
        return steps;
    }

    private static void incrementIfTracked(@Nullable ProgressWrapper progressWrapper) {
        if (progressWrapper != null) {
            progressWrapper.incrementStep();
        }
    }

    /**
     * A "related" link almost always points at a concept the branch/collection already saved for another
     * reason — either it's one of the branch's own concepts ({@code urlTosavedConcept} already holds it),
     * or an earlier "related" link elsewhere in the same branch already resolved it. Reusing
     * {@code urlTosavedConcept} as the cache for both cases keeps a large collection, where the same
     * handful of concepts are related from many sides, down to one lookup per distinct related concept.
     *
     * <p>A related concept the import has never seen is recorded as a stub rather than fetched, see
     * {@link #findOrCreateStub}.
     */
    private static void createRelatedConceptsRelations(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, Map.Entry<String, FullInfoDTO> info, @NonNull Map<String, Concept> urlTosavedConcept, @NonNull FullInfoDTO fullInfoDTO, @Nullable ProgressWrapper progressWrapper) {
        Concept currentConcept = urlTosavedConcept.get(info.getKey());
        for (PurlInfoDTO related : fullInfoDTO.getRelated()) {
            String relatedUri = related.getValue();
            incrementIfTracked(progressWrapper);
            if (relatedUri == null || relatedUri.isBlank()) {
                // Nothing identifies the concept on the other end of the link : there is no local row to
                // create for it, and no URI a later fetch could use to resolve it.
                log.debug("Skipping a related link of {} : the thesaurus returned no URI for it", info.getKey());
                continue;
            }
            Concept relatedConcept = urlTosavedConcept.computeIfAbsent(relatedUri, url -> findOrCreateStub(utils, vocabulary, url));
            utils.conceptRepository.addRelatedConceptIfAbsent(currentConcept.getId(), relatedConcept.getId());
        }
    }

    /**
     * Looks the related concept up by external id — the key every concept is stored under — so that a
     * concept already imported by another branch or collection is reused rather than duplicated. Only a
     * URI carrying no external id at all falls back to the URI itself as the lookup key.
     *
     * <p>A concept new to this instance is saved as a stub : its vocabulary, URI and external id, no
     * labels, {@code isLoaded = false}. Fetching what fills it in is deferred until something actually
     * reads it, see {@code ConceptService#loadUnloadedRelatedConceptsOf}.
     */
    private static Concept findOrCreateStub(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull String uri) {
        String externalId = externalIdFromUri(uri);
        Optional<Concept> existing = externalId == null
                ? utils.conceptRepository.findByUri(uri)
                : utils.conceptRepository.findConceptByExternalIdIgnoreCase(vocabulary.getExternalVocabularyId(), externalId);
        return existing.orElseGet(() -> {
            Concept stub = new Concept();
            stub.setVocabulary(vocabulary);
            stub.setUri(uri);
            stub.setExternalId(externalId);
            stub.setLoaded(false);
            return utils.conceptRepository.save(stub);
        });
    }

    private static void saveAllConceptFromBranch(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO dto, Map<String, Concept> savedConcept, @Nullable ProgressWrapper progressWrapper) {
        for (Map.Entry<String, FullInfoDTO> info : dto.getData().entrySet()) {
            savedConcept.put(info.getKey(), utils.conceptService.saveOrGetConceptFromFullDTO(vocabulary, info.getValue(), null));
            incrementIfTracked(progressWrapper);
        }
    }

    private static void createRelations(BranchLoadComponents utils, Map.Entry<String, FullInfoDTO> info, @NonNull FullInfoDTO fullInfoDTO, Map<String, Concept> savedConcept, @Nullable ProgressWrapper progressWrapper) {
        for (PurlInfoDTO purlInfoDTO : fullInfoDTO.getNarrower()) {
            incrementIfTracked(progressWrapper);
            Concept parent = savedConcept.get(info.getKey());
            Concept child = savedConcept.get(purlInfoDTO.getValue());
            if (parent == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + info.getKey());
            }
            if (child == null) {
                // The thesaurus's hierarchy can point outside the fetched set : a collection is a
                // curated subset of the thesaurus, and one of its concepts can have a narrower concept
                // that belongs to a different collection, so it isn't part of this response at all.
                // There's nothing local to attach the relation to — that's expected, not corrupt data.
                log.debug("Skipping narrower relation {} -> {} : the child concept was not returned by this branch/collection fetch",
                        info.getKey(), purlInfoDTO.getValue());
                continue;
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
