package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActionCodeSeeder {
    private final ConceptRepository conceptRepository;
    private final ActionCodeRepository actionCodeRepository;

    private void getOrCreateActionCode(String code, Concept type) {
        Optional<ActionCode> optCode = actionCodeRepository.findById(code);
        ActionCode codeGetOrCreated ;
        if(optCode.isEmpty()) {
            codeGetOrCreated = new ActionCode();
            codeGetOrCreated.setCode(code);
            codeGetOrCreated.setType(type);
            actionCodeRepository.save(codeGetOrCreated);
        }
    }


    public record ActionCodeSpec(String code, String typeConceptExternalId, String typeVocabularyExternalId) {}
    public void seed(List<ActionCodeSpec> specs) {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                Concept type = SeederUtils.field("typeConceptExternalId", () -> conceptRepository
                        .findConceptByExternalIdIgnoreCase(s.typeVocabularyExternalId, s.typeConceptExternalId)
                        .orElseThrow(() -> new IllegalStateException("Concept introuvable")));
                getOrCreateActionCode(s.code, type);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Code action ligne " + (i + 1) + "] '" + s.code + "' : " + e.getMessage(), e);
            }
        }
    }
}
