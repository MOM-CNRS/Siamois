package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.ui.api.openapi.v1.generic.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.ResourceIdentifier;
import org.springframework.core.convert.converter.Converter;

import java.util.List;

public interface ResourceIdentifierMapper<D,E extends ResourceIdentifier>
        extends Converter<D, E> {

    default RelationshipToOne<E> dtoToResourceIdentifierRelationship(D dto) {
        return new RelationshipToOne<>(convert(dto));
    }

    default RelationshipToMany<E> dtoSetToResourceIdentifierRelationship(
            List<D> dtoSet) {
        if (dtoSet == null) {
            return null;
        }
        List<E> identifiers = dtoSet.stream()
                .map(this::convert) // Assuming `convert` maps dto to resource identifier
                .toList();
        return new RelationshipToMany<>(identifiers);
    }

}
