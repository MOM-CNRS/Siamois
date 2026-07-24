package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Un "FormConfig" est un ensemble de configuration de champs (FieldFormConfig) associé à une valeur d'un champ.
 * Le champ est défini par fieldConcept (Type, Catégorie), la valeur est définie par valueConcept (Céramique, Dépôt, ...).
 *      valueConcept peut être null si la configuration est la configuration par défaut appliquée dès qu'il n'y a pas de configuration.
 * L'idée est de pouvoir configurer les champs d'un formulaire selon le concept type choisi
 */
@Data
@Entity
@Table(name = "form_config")
@AllArgsConstructor
@NoArgsConstructor
public class FormConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long id;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_value_concept_id")
    private Concept valueConcept;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_field_concept_id", nullable = false)
    private Concept fieldConcept;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution_id")
    private Institution institution;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    @OneToMany(mappedBy = "formConfig", fetch = FetchType.LAZY)
    private Set<FieldFormConfig> fieldConfigs;

}
