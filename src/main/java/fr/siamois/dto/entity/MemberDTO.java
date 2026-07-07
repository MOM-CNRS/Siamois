package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class MemberDTO extends AbstractEntityDTO {

    protected PersonDTO person;
    protected List<ProfileDTO> profiles;

    public String displayName() {
        return person.displayName();
    }

}
