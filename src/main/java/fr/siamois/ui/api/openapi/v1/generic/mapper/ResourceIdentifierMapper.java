package fr.siamois.ui.api.openapi.v1.generic.mapper;

import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import org.springframework.core.convert.converter.Converter;

public interface ResourceIdentifierMapper<D,E extends ResourceIdentifier>
        extends Converter<D, E> {

}
