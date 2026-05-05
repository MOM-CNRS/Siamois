package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class PersonDTO extends AbstractEntityDTO {

    private boolean isSuperAdmin;
    private String name;
    private String email;
    private String lastname;
    private boolean isPassToModify;
    private String username;

    public String displayName() {
        return name + " " + lastname + " ("+email+")";
    }

}
