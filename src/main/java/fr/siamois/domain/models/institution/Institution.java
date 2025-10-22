package fr.siamois.domain.models.institution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import lombok.Data;
import org.hibernate.envers.NotAudited;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "institution")
public class Institution implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "institution_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "institution_name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "institution_description", length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @Column(name = "identifier", nullable = false, length = Integer.MAX_VALUE)
    private String identifier;

    @DefaultValue("NOW()")
    @Column(name = "creation_date", nullable = false)
    @JsonIgnore
    private OffsetDateTime creationDate = OffsetDateTime.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @NotAudited
    @JoinTable(name = "institution_manager",
            joinColumns = @JoinColumn(name = "fk_institution_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    private Set<Person> managers = new HashSet<>();

    @JsonProperty("creationDate")
    private String creationDateString() {
        if (creationDate == null) {
            return "";
        }
        return creationDate.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static final int MAX_NAME_LENGTH = 40;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Institution institution)) return false;
        return Objects.equals(name, institution.name) && Objects.equals(identifier, institution.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, identifier);
    }

    @Override
    public String toString() {
        return name;
    }

}