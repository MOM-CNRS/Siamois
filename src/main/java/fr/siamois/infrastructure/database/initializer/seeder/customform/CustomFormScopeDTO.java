package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;

public record CustomFormScopeDTO(
    String scope_level, // scope level
    ConceptSeeder.ConceptKey type, // applicable type
    CustomFormDTO form // form to associate
) {
}
