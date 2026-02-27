package fr.siamois.dto.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class PersonDTO extends AbstractEntityDTO {

    private boolean isSuperAdmin;
    private String name;
    private String email;
    private String lastname;
    private boolean isPassToModify;
    private String username;

    public String displayName() {
        return name + " " + lastname;
    }

}
