package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.generic.mapper.ResourceIdentifierMapper;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PersonResourceIdentifierMapper
        extends ResourceIdentifierMapper<PersonDTO,
        PersonResourceIdentifier> {

    @Override
    @Mapping(target = "resourceType", constant = "persons")
    PersonResourceIdentifier convert(PersonDTO personDTO);

    @Named("toAuthorRelationship")
    default PersonResourceIdentifier toAuthorRelationship(PersonDTO personDTO) {
        if (personDTO == null) {
            return null;
        }
        return convert(personDTO);
    }

}
