package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

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

    @Data
    @Embeddable
    public static class FieldFormConfigId {
        private Long customFieldId;
        private Long formsConfigId;
    }

}
