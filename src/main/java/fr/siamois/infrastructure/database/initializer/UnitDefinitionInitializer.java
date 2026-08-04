package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ThesaurusSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.UnitDefinitionSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Order(0) // after VocabularyTypeInitializer (-1): creating the vocabulary needs the "Thesaurus" type
@RequiredArgsConstructor
public class UnitDefinitionInitializer implements DatabaseInitializer {

    private static final String THESAURUS_BASE_URI = "https://thesaurus.mom.fr";
    private static final String UNITS_VOCABULARY_ID = "th252";

    private final ThesaurusSeeder thesaurusSeeder;
    private final UnitDefinitionSeeder unitDefinitionSeeder;

    /**
     * The units known to every instance — every unit the application measures in, since nothing
     * else creates one: a measurement saved in a unit absent from here fails rather than inserting
     * a copy of it. Keep in step with the units the forms declare ({@code RecordingUnitForm},
     * {@code ContainerForm}, {@code SpecimenForm}), matched on symbol and dimension.
     */
    private static final List<UnitDefinitionDTO> UNITS = List.of(
            unit("4289327", "Mètres", "m", UnitDefinition.Dimension.LENGTH, 1.0, true),
            unit(null, "Centimètre", "cm", UnitDefinition.Dimension.LENGTH, 0.01, false),
            unit(null, "Kilogramme", "kg", UnitDefinition.Dimension.MASS, 1000.0, false)
    );

    @Override
    public void initialize() throws DatabaseDataInitException {
        Vocabulary vocabulary = thesaurusSeeder
                .seed(List.of(new ThesaurusSeeder.ThesaurusSpec(THESAURUS_BASE_URI, UNITS_VOCABULARY_ID)))
                .get(UNITS_VOCABULARY_ID);

        unitDefinitionSeeder.seed(vocabulary, UNITS);
        log.info("Unit definitions initialized: {} known in total", UNITS.size());
    }

    private static UnitDefinitionDTO unit(String conceptExternalId,
                                          String label,
                                          String symbol,
                                          UnitDefinition.Dimension dimension,
                                          double factorToBase,
                                          boolean systemBase) {
        return UnitDefinitionDTO.builder()
                .concept(conceptOf(conceptExternalId))
                .label(label)
                .symbol(symbol)
                .dimension(dimension)
                .factorToBase(factorToBase)
                .systemBase(systemBase)
                .build();
    }

    private static ConceptDTO conceptOf(String externalId) {
        if (externalId == null) return null;

        VocabularyDTO vocabulary = new VocabularyDTO();
        vocabulary.setExternalVocabularyId(UNITS_VOCABULARY_ID);

        ConceptDTO concept = new ConceptDTO();
        concept.setVocabulary(vocabulary);
        concept.setExternalId(externalId);
        return concept;
    }

}
