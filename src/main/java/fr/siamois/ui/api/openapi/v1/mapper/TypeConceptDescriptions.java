package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import org.springframework.lang.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The descriptions of the concepts of a project's types (label, alt labels, definition, parents, link to
 * the thesaurus): what the type picker of a creation form shows for each type, as the concept
 * suggestions of any other concept field do.
 */
public final class TypeConceptDescriptions {

    private final Map<Long, ConceptAutocompleteDTO> descriptionsByConceptId;

    private TypeConceptDescriptions(Map<Long, ConceptAutocompleteDTO> descriptionsByConceptId) {
        this.descriptionsByConceptId = Map.copyOf(descriptionsByConceptId);
    }

    /**
     * Wraps the descriptions of the types' concepts.
     *
     * @param descriptionsByConceptId the description of each type concept, by concept id
     * @return the descriptions
     */
    @NonNull
    public static TypeConceptDescriptions of(@NonNull Map<Long, ConceptAutocompleteDTO> descriptionsByConceptId) {
        return new TypeConceptDescriptions(descriptionsByConceptId);
    }

    /**
     * The resource of a type concept: its full description when there is one, else the bare resource
     * built by {@code withoutDescription}.
     *
     * @param concept            the type concept
     * @param withoutDescription builds the resource of a concept that has no description
     * @return the resource of the concept
     */
    @NonNull
    public ResolvedConceptResource resourceOf(@NonNull ConceptDTO concept,
                                              @NonNull Function<ConceptDTO, ResolvedConceptResource> withoutDescription) {
        return Optional.ofNullable(descriptionsByConceptId.get(concept.getId()))
                .map(ResolvedConceptResource::from)
                .orElseGet(() -> withoutDescription.apply(concept));
    }
}
