package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "institution_settings")
public class InstitutionSettings implements Serializable {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    @MapsId
    private Institution institution;

    @Column(name = "ark_naan")
    private String arkNaan;

    @Column(name = "ark_prefix")
    private String arkPrefix;

    @DefaultValue("FALSE")
    @Column(name = "ark_is_enabled")
    private Boolean arkIsEnabled = false;

    @DefaultValue("16")
    @Column(name = "ark_size")
    private Integer arkSize = 16;

    @DefaultValue("FALSE")
    @Column(name = "ark_is_uppercase")
    private Boolean arkIsUppercase = false;

    public boolean hasEnabledArkConfig() {
        return arkIsEnabled && arkNaan != null && arkPrefix != null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        InstitutionSettings that = (InstitutionSettings) o;
        return Objects.equals(institution, that.institution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institution);
    }
}
