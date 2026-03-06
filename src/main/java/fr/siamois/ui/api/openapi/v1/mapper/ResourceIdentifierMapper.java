package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.jsonapi.Relationship;
import fr.siamois.ui.api.openapi.v1.jsonapi.ResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import org.springframework.core.convert.converter.Converter;

import java.util.List;

public interface ResourceIdentifierMapper<D,E extends ResourceIdentifier>
        extends Converter<D, E> {

    default Relationship<E> conceptDTOToConceptResourceIdentifierRelationship(D dto) {
        return new Relationship<>(convert(dto));
    }

    default Relationship<List<E>> conceptDTOSetToPersonResourceIdentifierRelationship(
            List<D> dtoSet) {
        if (dtoSet == null) {
            return null;
        }
        List<E> identifiers = dtoSet.stream()
                .map(this::convert) // Assuming `convert` maps PersonDTO to PersonResourceIdentifier
                .toList();
        return new Relationship<>(identifiers);
    }
}
