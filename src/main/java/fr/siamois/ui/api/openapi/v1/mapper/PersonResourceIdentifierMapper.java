package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.mapper.ResourceIdentifierMapper;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

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
    default RelationshipToOne<PersonResourceIdentifier> toAuthorRelationship(PersonDTO personDTO) {
        if (personDTO == null) {
            return null;
        }
        return new RelationshipToOne<>(convert(personDTO));
    }

}
