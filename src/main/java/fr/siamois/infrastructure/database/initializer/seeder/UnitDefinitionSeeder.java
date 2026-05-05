package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitDefinitionSeeder {
    private final ConceptSeeder conceptSeeder;
    private final UnitDefinitionRepository unitDefinitionRepository;
    private final UnitDefinitionMapper mapper;

    public UnitDefinition findUnitOrReturnNull(Concept concept) {
        return unitDefinitionRepository.findByConcept(concept)
                .orElse(null);
    }

    public void seed(Vocabulary vocabulary, List<UnitDefinitionDTO> specs) {
        for (var s : specs) {
            Concept concept = conceptSeeder.findConceptOrReturnNull(s.getConcept().getVocabulary().getExternalVocabularyId(),
                    s.getConcept().getExternalId());

            if (concept == null) {
                conceptSeeder.seed(vocabulary,
                        List.of(new ConceptSeeder.ConceptSpec(
                                s.getConcept().getVocabulary().getExternalVocabularyId(),
                                s.getConcept().getExternalId(),
                                s.getLabel(),
                                "fr"
                        )));
            }

            UnitDefinition unitDefinition = mapper.invertConvert(s);

            unitDefinition.setConcept(
                    conceptSeeder.findConceptOrReturnNull(
                            s.getConcept().getVocabulary().getExternalVocabularyId(),
                            s.getConcept().getExternalId()
                    )
            );


            UnitDefinition found = findUnitOrReturnNull(unitDefinition.getConcept());

            if (found == null) {
                unitDefinitionRepository.save(unitDefinition);
            }

        }
    }
}
