package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@Order(0)
public class DefaultFormsInitializer implements DatabaseInitializer {

    private final ConceptSeeder conceptSeeder;
    private final ThesaurusSeeder thesaurusSeeder;
    private final CustomFieldSeeder customFieldSeeder;

    static final String DEFAULT_VOCABULARY_INSTANCE_URI = "https://thesaurus.mom.fr";
    static final String DEFAULT_VOCABULARY_ID = "th230";

    // Default Siamois Thesaurus
    List<ThesaurusSeeder.ThesaurusSpec> thesauri = List.of(
            new ThesaurusSeeder.ThesaurusSpec(DEFAULT_VOCABULARY_INSTANCE_URI, DEFAULT_VOCABULARY_ID)
    );

    // Default Siamois field concept
    List<ConceptSeeder.ConceptSpec> concepts = List.of(
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID,"4287605","Type d'unité", "fr")
    );

    // Default Siamois field
    List<CustomFieldSeeder.CustomFieldSeederSpec> fields = List.of(
            new CustomFieldSeeder.CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "spatialunit.field.type",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID,"4287605"),
                    "type",
                    "bi bi-pencil-square",
                    "mr-2 recording-unit-type-chip",
                    "SIARU.TYPE"
            )
    );

    public DefaultFormsInitializer(ConceptSeeder conceptSeeder, ThesaurusSeeder thesaurusSeeder, CustomFieldSeeder customFieldSeeder) {
        this.conceptSeeder = conceptSeeder;
        this.thesaurusSeeder = thesaurusSeeder;
        this.customFieldSeeder = customFieldSeeder;
    }

    @Override
    public void initialize() throws DatabaseDataInitException {
        Map<String, Vocabulary> result = thesaurusSeeder.seed(thesauri);
        conceptSeeder.seed(result.get(DEFAULT_VOCABULARY_ID), concepts);
        customFieldSeeder.seed(fields);
    }
}