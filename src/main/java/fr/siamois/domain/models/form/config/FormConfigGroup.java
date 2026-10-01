package fr.siamois.domain.models.form.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * A group of fields in a form — a section the form displays under a title. The groups of a
 * {@link FormConfig} are ordered by {@code position}; each {@link FieldFormConfig} belongs to one
 * group and is ordered within it.
 * <p>
 * {@code label} is either the message key of one of the original sections (translated on display)
 * or free text typed by whoever configured the form.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Audited
@Table(name = "form_config_group", indexes = {
        @Index(name = "idx_form_config_group_config", columnList = "fk_form_config_id")
})
public class FormConfigGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @NonNull
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "position", nullable = false)
    private int position;

    public FormConfigGroup(@NonNull FormConfig formConfig, String label, int position) {
        this.formConfig = formConfig;
        this.label = label;
        this.position = position;
    }
}
