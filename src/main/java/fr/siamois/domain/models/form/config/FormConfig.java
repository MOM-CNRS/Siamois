package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Un "FormConfig" est un ensemble de configuration de champs (FieldFormConfig) associé à une valeur d'un champ.
 * Le champ est défini par fieldConcept (Type, Catégorie), la valeur est définie par valueConcept (Céramique, Dépôt, ...).
 *      valueConcept peut être null si la configuration est la configuration par défaut appliquée dès qu'il n'y a pas de configuration.
 * L'idée est de pouvoir configurer les champs d'un formulaire selon le concept type choisi
 * <p>
 * {@code uk_form_config_scope} garantit qu'un même (actionUnit, fieldConcept, valueConcept) n'a
 * qu'une seule ligne — mais uniquement quand {@code valueConcept} n'est pas null : PostgreSQL (comme
 * la norme SQL) traite deux NULL comme non-égaux dans une contrainte UNIQUE, donc deux configurations
 * par défaut du même champ pour le même projet (valueConcept null des deux côtés) ne violent PAS
 * cette contrainte. C'est pour cette raison que la création paresseuse d'une configuration par défaut
 * (voir {@code TableFieldConfigServiceImpl#createOrGetFormConfig}) doit rester protégée contre la
 * concurrence côté application plutôt que de compter uniquement sur cette contrainte.
 */
@Data
@Entity
@Audited
@Table(name = "form_config", uniqueConstraints = @UniqueConstraint(
        name = "uk_form_config_scope",
        columnNames = {"fk_action_unit_id", "fk_field_concept_id", "fk_value_concept_id"}
))
@AllArgsConstructor
@NoArgsConstructor
public class FormConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long id;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "fk_value_concept_id")
    private Concept valueConcept;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "fk_field_concept_id", nullable = false)
    private Concept fieldConcept;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "fk_institution_id")
    private Institution institution;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    @NotAudited
    @OneToMany(mappedBy = "formConfig", fetch = FetchType.LAZY)
    private List<FieldFormConfig> fieldConfigs;

    @NonNull
    @Column(name = "identifier_format", nullable = false)
    private String identifierFormat;

    @Column(name = "min_code", nullable = false)
    private int minCode;

    @Column(name = "max_code", nullable = false)
    private int maxCode;

}
