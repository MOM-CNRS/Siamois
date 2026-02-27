package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import lombok.Getter;

import java.io.Serializable;


@Getter
public class UserInfo implements Serializable {

    protected final InstitutionDTO institution;
    protected final PersonDTO user;
    protected final String lang;

    public UserInfo(InstitutionDTO institution, PersonDTO user, String lang) {
        this.institution = institution;
        this.user = user;
        this.lang = lang;
    }

}
