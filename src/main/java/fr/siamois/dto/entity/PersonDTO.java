package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
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
