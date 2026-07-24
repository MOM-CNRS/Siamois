package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.util.Objects;

/**
 * Un FieldFormConfig est la configuration d'un champ d'une fiche par rapport à un type donnée.
 * Un FieldFormConfig est rattaché à un FormConfig qui indiqué à quelle configuration il est rattaché
 */
@Data
@Entity
@Table(name = "field_form_config", indexes = {
        @Index(name = "idx_field_form_config_config", columnList = "fk_form_config_id")
})
@NoArgsConstructor
@AllArgsConstructor
public class FieldFormConfig {

    @EmbeddedId
    private FieldFormConfigId id = new FieldFormConfigId();

    @NonNull
    @MapsId("customFieldId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_field_id", nullable = false)
    private CustomField field;

    @NonNull
    @MapsId("formsConfigId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isActive = true;

    @Column(name = "is_mandatory", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isMandatory = false;

    @Column(name = "is_institution_locked", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isInstitutionLocked = false;

    public void setField(@NonNull CustomField field) {
        this.field = field;
        this.id.customFieldId = field.getId();
    }

    public void setFormConfig(@NonNull FormConfig formConfig) {
        this.formConfig = formConfig;
        this.id.formsConfigId = formConfig.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldFormConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Data
    @Embeddable
    @EqualsAndHashCode
    public static class FieldFormConfigId {
        private Long customFieldId;
        private Long formsConfigId;
    }

}
