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

import java.util.*;

@Slf4j
public class ConceptApiUtils {

    public static final String IDC = "idc=";

    /** Values per {@code IN} clause, well below the 32767 bind parameters PostgreSQL accepts per statement. */
    private static final int IN_CLAUSE_CHUNK_SIZE = 1000;

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
     * per narrower/related link processed. Null is a valid, no-op progress tracker : most callers have
     * no progress bar to drive.
     *
     * <p>Everything is read and written in bulk, a few queries for the whole branch : saving concept by
     * concept and link by link made a large collection take a time that grew with the square of its size.
     */
    public static Map<String, Concept> saveAllConceptsOfBranch(@NonNull BranchLoadComponents components, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO, @NonNull Map<String, Concept> urlSavedConcept, @Nullable ProgressWrapper progressWrapper) {
        if (progressWrapper != null) {
            progressWrapper.reset();
            progressWrapper.setTotalSteps(totalStepsOf(branchDTO));
        }
        urlSavedConcept.putAll(components.conceptService.saveOrGetConceptsFromFullDTOs(vocabulary, branchDTO.getData(), null));
        incrementIfTracked(progressWrapper, branchDTO.getData().size());
        createRelations(components, branchDTO, urlSavedConcept, progressWrapper);
        createRelatedConceptsRelations(components, vocabulary, branchDTO, urlSavedConcept, progressWrapper);
        return urlSavedConcept;
    }

    /**
     * Splits values to look up with an {@code IN} clause, whose number of parameters the database bounds.
     */
    @NonNull
    public static <T> List<List<T>> chunksOf(@NonNull Collection<T> values) {
        List<T> list = new ArrayList<>(values);
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < list.size(); start += IN_CLAUSE_CHUNK_SIZE) {
            chunks.add(list.subList(start, Math.min(start + IN_CLAUSE_CHUNK_SIZE, list.size())));
        }
        return chunks;
    }

    private static int totalStepsOf(@NonNull ConceptBranchDTO branchDTO) {
        int steps = branchDTO.getData().size();
        for (FullInfoDTO info : branchDTO.getData().values()) {
            if (info.getNarrower() != null) steps += info.getNarrower().length;
            if (info.getRelated() != null) steps += info.getRelated().length;
        }
        return steps;
    }

    private static void incrementIfTracked(@Nullable ProgressWrapper progressWrapper, int steps) {
        if (progressWrapper != null) {
            progressWrapper.incrementStep(steps);
        }
    }

    /**
     * A "related" link almost always points at a concept the branch/collection already saved for another
     * reason — either it's one of the branch's own concepts ({@code urlTosavedConcept} already holds it),
     * or another "related" link elsewhere in the same branch points at it too. Reusing
     * {@code urlTosavedConcept} as the cache for both cases keeps a large collection, where the same
     * handful of concepts are related from many sides, down to one lookup per distinct related concept —
     * and those lookups are made together, see {@link #findOrCreateStubs}.
     */
    private static void createRelatedConceptsRelations(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull ConceptBranchDTO branchDTO, @NonNull Map<String, Concept> urlTosavedConcept, @Nullable ProgressWrapper progressWrapper) {
        Set<String> unknownUris = new LinkedHashSet<>();
        for (FullInfoDTO info : branchDTO.getData().values()) {
            if (info.getRelated() == null) continue;
            for (PurlInfoDTO related : info.getRelated()) {
                String relatedUri = related.getValue();
                if (relatedUri != null && !relatedUri.isBlank() && !urlTosavedConcept.containsKey(relatedUri)) {
                    unknownUris.add(relatedUri);
                }
            }
        }
        findOrCreateStubs(utils, vocabulary, unknownUris, urlTosavedConcept);

        List<Long> conceptIds = new ArrayList<>();
        List<Long> relatedIds = new ArrayList<>();
        for (Map.Entry<String, FullInfoDTO> info : branchDTO.getData().entrySet()) {
            if (info.getValue().getRelated() == null) continue;
            Concept currentConcept = urlTosavedConcept.get(info.getKey());
            for (PurlInfoDTO related : info.getValue().getRelated()) {
                String relatedUri = related.getValue();
                if (relatedUri == null || relatedUri.isBlank()) {
                    // Nothing identifies the concept on the other end of the link : there is no local row to
                    // create for it, and no URI a later fetch could use to resolve it.
                    log.debug("Skipping a related link of {} : the thesaurus returned no URI for it", info.getKey());
                    continue;
                }
                conceptIds.add(currentConcept.getId());
                relatedIds.add(urlTosavedConcept.get(relatedUri).getId());
            }
            incrementIfTracked(progressWrapper, info.getValue().getRelated().length);
        }
        if (!conceptIds.isEmpty()) {
            utils.conceptRepository.addRelatedConceptsIfAbsent(conceptIds.toArray(Long[]::new), relatedIds.toArray(Long[]::new));
        }
    }

    /**
     * Looks the related concepts up by external id — the key every concept is stored under — so that a
     * concept already imported by another branch or collection is reused rather than duplicated, with one
     * query for all of them. Only a URI carrying no external id at all falls back to the URI itself as
     * the lookup key, see {@link #findOrCreateStub}.
     *
     * <p>A concept new to this instance is saved as a stub : its vocabulary, URI and external id, no
     * labels, {@code isLoaded = false}. Fetching what fills it in is deferred until something actually
     * reads it, see {@code ConceptService#loadUnloadedRelatedConceptsOf}.
     */
    private static void findOrCreateStubs(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull Set<String> uris, @NonNull Map<String, Concept> urlTosavedConcept) {
        Map<String, List<String>> urisByLowerExternalId = new LinkedHashMap<>();
        for (String uri : uris) {
            String externalId = externalIdFromUri(uri);
            if (externalId == null) {
                urlTosavedConcept.put(uri, findOrCreateStub(utils, vocabulary, uri));
            } else {
                urisByLowerExternalId.computeIfAbsent(externalId.toLowerCase(Locale.ROOT), id -> new ArrayList<>()).add(uri);
            }
        }
        Map<String, Concept> existing = new HashMap<>();
        for (List<String> chunk : chunksOf(urisByLowerExternalId.keySet())) {
            for (Concept concept : utils.conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(vocabulary.getExternalVocabularyId(), chunk)) {
                existing.putIfAbsent(concept.getExternalId().toLowerCase(Locale.ROOT), concept);
            }
        }
        for (Map.Entry<String, List<String>> entry : urisByLowerExternalId.entrySet()) {
            Concept concept = existing.get(entry.getKey());
            if (concept == null) {
                String uri = entry.getValue().get(0);
                Concept stub = new Concept();
                stub.setVocabulary(vocabulary);
                stub.setUri(uri);
                stub.setExternalId(externalIdFromUri(uri));
                stub.setLoaded(false);
                concept = utils.conceptRepository.save(stub);
            }
            for (String uri : entry.getValue()) {
                urlTosavedConcept.put(uri, concept);
            }
        }
    }

    /** The lookup of a related concept whose URI carries no external id : by the URI itself. */
    private static Concept findOrCreateStub(BranchLoadComponents utils, @NonNull Vocabulary vocabulary, @NonNull String uri) {
        return utils.conceptRepository.findByUri(uri).orElseGet(() -> {
            Concept stub = new Concept();
            stub.setVocabulary(vocabulary);
            stub.setUri(uri);
            stub.setLoaded(false);
            return utils.conceptRepository.save(stub);
        });
    }

    /**
     * Records the narrower links of the branch that are not recorded yet : the existing ones are read with
     * one query, so importing a branch again — or a collection sharing concepts with a branch — does not
     * duplicate them.
     */
    private static void createRelations(BranchLoadComponents utils, @NonNull ConceptBranchDTO branchDTO, Map<String, Concept> savedConcept, @Nullable ProgressWrapper progressWrapper) {
        Set<Concept> parents = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, FullInfoDTO> info : branchDTO.getData().entrySet()) {
            if (info.getValue().getNarrower() == null) continue;
            Concept parent = savedConcept.get(info.getKey());
            if (parent == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + info.getKey());
            }
            parents.add(parent);
        }
        Set<List<Long>> existingLinks = new HashSet<>();
        for (List<Concept> chunk : chunksOf(parents)) {
            for (Object[] link : utils.conceptHierarchyRepository.findLinkIdsBelowOutsideFieldContext(chunk)) {
                existingLinks.add(List.of((Long) link[0], (Long) link[1]));
            }
        }

        List<ConceptHierarchy> toSave = new ArrayList<>();
        for (Map.Entry<String, FullInfoDTO> info : branchDTO.getData().entrySet()) {
            if (info.getValue().getNarrower() == null) continue;
            Concept parent = savedConcept.get(info.getKey());
            for (PurlInfoDTO purlInfoDTO : info.getValue().getNarrower()) {
                Concept child = savedConcept.get(purlInfoDTO.getValue());
                if (child == null) {
                    // The thesaurus's hierarchy can point outside the fetched set : a collection is a
                    // curated subset of the thesaurus, and one of its concepts can have a narrower concept
                    // that belongs to a different collection, so it isn't part of this response at all.
                    // There's nothing local to attach the relation to — that's expected, not corrupt data.
                    log.debug("Skipping narrower relation {} -> {} : the child concept was not returned by this branch/collection fetch",
                            info.getKey(), purlInfoDTO.getValue());
                    continue;
                }
                if (!parent.equals(child) && existingLinks.add(List.of(parent.getId(), child.getId()))) {
                    toSave.add(new ConceptHierarchy(parent, child, null));
                }
            }
            incrementIfTracked(progressWrapper, info.getValue().getNarrower().length);
        }
        utils.conceptHierarchyRepository.saveAll(toSave);
    }

    public record BranchLoadComponents(ConceptApi conceptApi, ConceptService conceptService,
                                       ConceptRepository conceptRepository,
                                       ConceptHierarchyRepository conceptHierarchyRepository) {

        public BranchLoadComponents(@NonNull ApplicationContext context) {
            this(context.getBean(ConceptApi.class), context.getBean(ConceptService.class), context.getBean(ConceptRepository.class), context.getBean(ConceptHierarchyRepository.class));
        }

    }

}
