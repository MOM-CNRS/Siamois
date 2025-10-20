package fr.siamois.domain.models.form.formscope;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
        name = "form_scopes",
        uniqueConstraints = {
                // Un seul ORG_WIDE par type d’UE et organisation
                @UniqueConstraint(
                        name = "uq_form_scope_org_wide",
                        columnNames = {"fk_type_id", "fk_institution_id"}
                )
        }
)
public class FormScope {

    public enum ScopeLevel {
        GLOBAL_DEFAULT,  // Formulaire par défaut pour un type d’UE
        ORG_WIDE         // Formulaire spécifique à une organisation
    }

    @Id
    @GeneratedValue
    private Long form_scope_id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_form_id", nullable = false)
    private CustomForm form;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_type_id")
    private Concept type;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false, length = 20)
    private ScopeLevel scopeLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution_id")
    private Institution institution;

}
