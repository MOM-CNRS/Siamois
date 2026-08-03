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
@Inheritance(strategy = InheritanceType.JOINED)
public class FieldFormConfig {

    @EmbeddedId
    protected FieldFormConfigId id = new FieldFormConfigId();

    @NonNull
    @MapsId("customFieldId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_field_id", nullable = false)
    protected CustomField field;

    @NonNull
    @MapsId("formsConfigId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    protected FormConfig formConfig;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    protected boolean isActive = true;

    @Column(name = "is_mandatory", columnDefinition = "BOOLEAN DEFAULT FALSE")
    protected boolean isMandatory = false;

    @Column(name = "is_institution_locked", columnDefinition = "BOOLEAN DEFAULT FALSE")
    protected boolean isInstitutionLocked = false;

    /**
     * Defines the position of the field in the additional fields. 0 if no position set, order may be random if position = 0
     */
    @Column(name = "position", columnDefinition = "INT DEFAULT 0")
    protected int position = 0;

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
        protected Long customFieldId;
        protected Long formsConfigId;
    }

}
