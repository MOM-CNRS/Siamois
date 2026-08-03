package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void seed(Vocabulary vocabulary, List<UnitDefinitionDTO> specs) {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                UnitDefinition unitDefinition = mapper.invertConvert(s);
                unitDefinition.setConcept(seedConceptOf(vocabulary, s));

                if (!alreadyKnown(unitDefinition)) {
                    unitDefinitionRepository.save(unitDefinition);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Unité ligne " + (i + 1) + "] '" + s.getSymbol() + "' : " + e.getMessage(), e);
            }
        }
    }

    private Concept seedConceptOf(Vocabulary vocabulary, UnitDefinitionDTO spec) {
        if (spec.getConcept() == null) return null;

        String vocabularyId = spec.getConcept().getVocabulary().getExternalVocabularyId();
        String externalId = spec.getConcept().getExternalId();

        if (conceptSeeder.findConceptOrReturnNull(vocabularyId, externalId) == null) {
            conceptSeeder.seed(vocabulary,
                    List.of(new ConceptSeeder.ConceptSpec(vocabularyId, externalId, spec.getLabel(), "fr")));
        }
        return conceptSeeder.findConceptOrReturnNull(vocabularyId, externalId);
    }

    private boolean alreadyKnown(UnitDefinition unitDefinition) {
        if (unitDefinition.getConcept() != null) {
            return findUnitOrReturnNull(unitDefinition.getConcept()) != null;
        }
        return unitDefinitionRepository
                .findFirstBySymbolAndDimensionOrderByIdAsc(unitDefinition.getSymbol(), unitDefinition.getDimension())
                .isPresent();
    }
}
