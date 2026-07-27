package fr.siamois.infrastructure.database.initializer.seeder.customfield;


import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerLegacy;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;


public record CustomFieldAnswerDTO(
        Class<? extends CustomFieldAnswerLegacy> answerClass,
        CustomFieldSeederSpec field,
        ConceptSeeder.ConceptKey valueAsConcept
){

}


