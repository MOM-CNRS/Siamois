package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Un "FormConfig" est un ensemble de configuration de champs (FieldFormConfig) associé à un type d'une table configurable.
 * La table est {@code configuredTable} (UE, Mobilier, Document, ...), le type est {@code valueConcept} (Céramique, Dépôt, ...).
 *      valueConcept peut être null si la configuration est la configuration par défaut appliquée dès qu'il n'y a pas de configuration.
 * L'idée est de pouvoir configurer les champs d'un formulaire selon le concept type choisi.
 * <p>
 * La table est écrite sur la ligne : un type configuré reste configuré quel que soit le thésaurus dans lequel le
 * code du champ type de la table est lu (thésaurus de référence SIAMOIS, du projet ou de l'institution). Ce
 * thésaurus ne sert qu'à proposer les types que l'on peut encore configurer.
 * <p>
 * {@code uk_form_config_scope} garantit qu'un même (actionUnit, configuredTable, valueConcept) n'a
 * qu'une seule ligne — mais uniquement quand {@code valueConcept} n'est pas null : PostgreSQL (comme
 * la norme SQL) traite deux NULL comme non-égaux dans une contrainte UNIQUE, donc deux configurations
 * par défaut de la même table pour le même projet (valueConcept null des deux côtés) ne violent PAS
 * cette contrainte. C'est pour cette raison que la création paresseuse d'une configuration par défaut
 * (voir {@code TableFieldConfigServiceImpl#createOrGetFormConfig}) doit rester protégée contre la
 * concurrence côté application plutôt que de compter uniquement sur cette contrainte.
 */
@Data
@Entity
@Audited
@Table(name = "form_config", uniqueConstraints = @UniqueConstraint(
        name = "uk_form_config_scope",
        columnNames = {"fk_action_unit_id", "configured_table", "fk_value_concept_id"}
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
    @Enumerated(EnumType.STRING)
    @Column(name = "configured_table", nullable = false, length = 32)
    private ConfigurableTable configuredTable;

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
