package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Gives a new project the thesaurus it documents its fields and types in ({@link DocumentationThesaurus}).
 * The default and empty templates rest on the common-core thesaurus (th332); a project copied from another takes the
 * copied project's, and failing that its institution's, and failing that th332.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectThesaurusAssigner {

    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final VocabularyRepository vocabularyRepository;
    private final DefaultProjectDefinition definition;

    /** The project documents its fields in the common-core thesaurus (default and empty templates). */
    public void assignCommonCore(Long projectId) {
        ActionUnit project = load(projectId);
        commonCore().ifPresent(vocabulary -> assign(project, vocabulary));
    }

    /** The project keeps the thesaurus it already has, else takes its institution's, else the common-core one. */
    public void assignInstitutionOrCommonCore(ActionUnit project) {
        if (documentationThesaurusRepository.findByActionUnitId(project.getId()).isPresent()) {
            return;
        }
        Optional<Vocabulary> institutionThesaurus = documentationThesaurusRepository
                .findOfInstitution(project.getCreatedByInstitution().getId())
                .map(DocumentationThesaurus::getVocabulary);
        Optional<Vocabulary> chosen = institutionThesaurus.isPresent() ? institutionThesaurus : commonCore();
        chosen.ifPresent(vocabulary -> assign(project, vocabulary));
    }

    private Optional<Vocabulary> commonCore() {
        Optional<Vocabulary> vocabulary = vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(
                definition.baseUri(), definition.externalVocabularyId());
        if (vocabulary.isEmpty()) {
            log.warn("The common-core thesaurus {} is not in the database: the new project has no thesaurus of its own",
                    definition.thesaurusUrl());
        }
        return vocabulary;
    }

    private void assign(ActionUnit project, Vocabulary vocabulary) {
        if (documentationThesaurusRepository.findByActionUnitId(project.getId()).isPresent()) {
            return;
        }
        DocumentationThesaurus thesaurus = new DocumentationThesaurus();
        thesaurus.setInstitution(project.getCreatedByInstitution());
        thesaurus.setActionUnit(project);
        thesaurus.setVocabulary(vocabulary);
        documentationThesaurusRepository.save(thesaurus);
    }

    private ActionUnit load(Long projectId) {
        return actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Unknown project: " + projectId));
    }
}
