package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonResourceIdentifierMapperTest {

  private final PersonResourceIdentifierMapper mapper = new PersonResourceIdentifierMapper() {
    @Override
    public PersonResourceIdentifier convert(PersonDTO personDTO) {
      if (personDTO == null) {
        return null;
      }
      String id = personDTO.getId() == null ? null : String.valueOf(personDTO.getId());
      return new PersonResourceIdentifier("persons", id);
    }
  };

  @Test
  void convert_mapsPersonDtoToResourceIdentifier() {
    PersonDTO dto = new PersonDTO();
    dto.setId(42L);

    PersonResourceIdentifier result = mapper.convert(dto);

    assertThat(result.getResourceType()).isEqualTo("persons");
    assertThat(result.getId()).isEqualTo("42");
  }

  @Test
  void convert_nullDto_returnsNull() {
    assertThat(mapper.convert(null)).isNull();
  }

  @Test
  void toAuthorRelationship_nullDto_returnsNull() {
    assertThat(mapper.toAuthorRelationship(null)).isNull();
  }

  @Test
  void toAuthorRelationship_nonNullDto_wrapsConvertedIdentifier() {
    PersonDTO dto = new PersonDTO();
    dto.setId(7L);

    PersonResourceIdentifier relationship = mapper.toAuthorRelationship(dto);

    assertThat(relationship).isNotNull();
    assertThat(relationship.getResourceType()).isEqualTo("persons");
    assertThat(relationship.getId()).isEqualTo("7");
  }
}
