package fr.siamois.dto.entity;

import fr.siamois.domain.models.auth.AccountStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class MemberDTO extends AbstractEntityDTO {

    protected PersonDTO person;
    protected transient List<ProfileDTO> profiles;
    protected AccountStatus accountStatus = AccountStatus.ACTIVE;

    public String displayName() {
        return person.displayName();
    }

}
