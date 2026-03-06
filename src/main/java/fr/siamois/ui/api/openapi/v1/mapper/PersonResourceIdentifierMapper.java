package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.jsonapi.Relationship;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PersonResourceIdentifierMapper extends ResourceIdentifierMapper<PersonDTO,
        PersonResourceIdentifier> {

    @Mapping(target = "type", constant = "person")
    PersonResourceIdentifier convert(PersonDTO personDTO);

}
