package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class InstitutionMemberDTO extends AbstractEntityDTO {

    private PersonDTO person;
    private List<ProfileDTO> profiles;

    public String displayName() {
        return person.displayName();
    }

}