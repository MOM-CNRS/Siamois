package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.*;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.config.ConceptFieldStateRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigGroupRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Copies the configuration of an existing project (templates {@code project:<id>}): the project's thesaurus
 * overrides, then for each of its form configurations the layout groups, field settings (state, order, width,
 * rules, units, vocabulary restrictions) and concept states, with groups and form configs remapped to the copies.
 * Runtime data (identifier counters, answers) and project members are not copied: the new project starts at 1.
 * Runs in the creation transaction, so a failed copy cancels the project.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCopyTemplateApplier implements ProjectTemplateApplier {

    private final ActionUnitRepository actionUnitRepository;
    private final FormConfigRepository formConfigRepository;
    private final FormConfigGroupRepository groupRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final ConceptFieldStateRepository conceptFieldStateRepository;
    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final ProjectCodeSourceRepository projectCodeSourceRepository;
    private final ProjectThesaurusAssigner thesaurusAssigner;

    @Override
    public boolean supports(String templateId) {
        return templateId != null && templateId.startsWith(ProjectTemplate.PROJECT_PREFIX);
    }

    @Override
    public boolean transactional() {
        return true;
    }

    /** The project a {@code project:<id>} template id designates. */
    public static long sourceProjectId(String templateId) {
        try {
            return Long.parseLong(templateId.substring(ProjectTemplate.PROJECT_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid project template id: " + templateId, e);
        }
    }

    @Override
    public void apply(UserInfo info, String templateId, Long newProjectId) {
        long sourceId = sourceProjectId(templateId);
        ActionUnit source = actionUnitRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Unknown template project: " + sourceId));
        ActionUnit target = actionUnitRepository.findById(newProjectId)
                .orElseThrow(() -> new NoSuchElementException("Unknown project: " + newProjectId));
        if (!source.getCreatedByInstitution().getId().equals(target.getCreatedByInstitution().getId())) {
            throw new IllegalArgumentException("A project template must belong to the same organization");
        }

        copyThesaurusOverrides(source, target);
        thesaurusAssigner.assignInstitutionOrCommonCore(target);
        for (FormConfig sourceConfig : formConfigRepository.findAllByActionUnitId(sourceId)) {
            copyFormConfig(sourceConfig, target);
        }
        log.info("Project {} configured from project {}", newProjectId, sourceId);
    }

    private void copyThesaurusOverrides(ActionUnit source, ActionUnit target) {
        documentationThesaurusRepository.findByActionUnitId(source.getId()).ifPresent(original -> {
            DocumentationThesaurus copy = new DocumentationThesaurus();
            copy.setInstitution(original.getInstitution());
            copy.setActionUnit(target);
            copy.setVocabulary(original.getVocabulary());
            documentationThesaurusRepository.save(copy);
        });
        for (ProjectCodeSource original : projectCodeSourceRepository.findAllByActionUnitId(source.getId())) {
            ProjectCodeSource copy = new ProjectCodeSource();
            copy.setActionUnit(target);
            copy.setFieldCode(original.getFieldCode());
            copy.setVocabulary(original.getVocabulary());
            copy.setSourceFieldCode(original.getSourceFieldCode());
            copy.setBranchTopTerm(original.getBranchTopTerm());
            projectCodeSourceRepository.save(copy);
        }
    }

    private void copyFormConfig(FormConfig original, ActionUnit target) {
        FormConfig copy = new FormConfig();
        copy.setActionUnit(target);
        copy.setInstitution(original.getInstitution());
        copy.setFieldConcept(original.getFieldConcept());
        copy.setValueConcept(original.getValueConcept());
        copy.setIdentifierFormat(original.getIdentifierFormat());
        copy.setMinCode(original.getMinCode());
        copy.setMaxCode(original.getMaxCode());
        copy.setFieldConfigs(new ArrayList<>());
        copy = formConfigRepository.save(copy);

        Map<Long, FormConfigGroup> groups = new HashMap<>();
        for (FormConfigGroup group : groupRepository.findAllByFormConfigIdOrderByPosition(original.getId())) {
            groups.put(group.getId(), groupRepository.save(
                    new FormConfigGroup(copy, group.getLabel(), group.getPosition())));
        }

        List<FieldFormConfig> fields = fieldFormConfigRepository.findAllByFormConfigId(original.getId());
        for (FieldFormConfig field : fields) {
            fieldFormConfigRepository.save(copyField(field, copy, groups));
        }

        for (ConceptFieldState state : conceptFieldStateRepository.findAllByFormConfigId(original.getId())) {
            ConceptFieldState stateCopy = new ConceptFieldState();
            stateCopy.setField(state.getField());
            stateCopy.setFormConfig(copy);
            stateCopy.setConcept(state.getConcept());
            stateCopy.setState(state.getState());
            conceptFieldStateRepository.save(stateCopy);
        }
    }

    private static FieldFormConfig copyField(FieldFormConfig original, FormConfig newConfig,
                                             Map<Long, FormConfigGroup> groups) {
        FieldFormConfig copy;
        if (original instanceof ConceptFieldFormConfig concept) {
            ConceptFieldFormConfig conceptCopy = new ConceptFieldFormConfig();
            conceptCopy.setBranchTopTerm(concept.getBranchTopTerm());
            conceptCopy.setCollection(concept.getCollection());
            conceptCopy.setBranchName(concept.getBranchName());
            conceptCopy.setCollectionName(concept.getCollectionName());
            conceptCopy.setVocabulary(concept.getVocabulary());
            conceptCopy.setFieldCode(concept.getFieldCode());
            conceptCopy.setVocabularyMode(concept.getVocabularyMode());
            copy = conceptCopy;
        } else {
            copy = new FieldFormConfig();
        }
        copy.setField(original.getField());
        copy.setFormConfig(newConfig);
        copy.setActive(original.isActive());
        copy.setMandatory(original.isMandatory());
        copy.setInstitutionLocked(original.isInstitutionLocked());
        copy.setPosition(original.getPosition());
        copy.setGroup(original.getGroup() == null ? null : groups.get(original.getGroup().getId()));
        copy.setWidth(original.getWidth());
        copy.setRules(original.getRules());
        copy.setUnits(original.getUnits());
        copy.setDefaultValue(original.getDefaultValue());
        return copy;
    }
}
