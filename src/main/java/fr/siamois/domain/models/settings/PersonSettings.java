package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.lang.NonNull;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "person_settings")
public class PersonSettings implements Serializable {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_person_id", nullable = false)
    @MapsId("id")
    private Person person;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_default_institution_id")
    private Institution defaultInstitution;

    @Column(name = "default_lang", length = LANG_CODE_LENGTH)
    private String langCode;

    // Expect language codes with ISO 639 format
    public static final int LANG_CODE_LENGTH = 3;

    public PersonSettings() {
        id = null;
        person = null;
        defaultInstitution = null;
        langCode = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonSettings personSettings)) return false;
        return Objects.equals(person, personSettings.person) && Objects.equals(defaultInstitution, personSettings.defaultInstitution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(person, defaultInstitution);
    }

    public void setPerson(@NonNull Person person) {
        this.person = person;
        this.id = person.getId();
    }

}
