package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class PersonDTO extends AbstractEntityDTO {

    private String name;
    private String email;
    private String lastname;
    private boolean isPassToModify;
    private String username;
    private boolean isEnabled;

    public String displayName() {
        return name + " " + lastname ;
    }

    public String initials() {
        String first = (name == null || name.isBlank()) ? "" : name.substring(0, 1);
        String last = (lastname == null || lastname.isBlank()) ? "" : lastname.substring(0, 1);
        return (first + last).toUpperCase();
    }

}
