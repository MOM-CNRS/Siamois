package fr.siamois.infrastructure.database.initializer.seeder.customfield;


import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;


public record CustomFieldAnswerDTO(
        Class<? extends CustomFieldAnswer> answerClass,
        CustomFieldSeederSpec field,
        ConceptSeeder.ConceptKey valueAsConcept
){

}


