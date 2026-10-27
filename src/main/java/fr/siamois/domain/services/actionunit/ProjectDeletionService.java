package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.infrastructure.database.repositories.*;
import fr.siamois.infrastructure.database.repositories.form.config.*;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes a project together with everything inside it (recording units, finds, documents, phases,
 * containers, studies and the project's own configuration). Refuses when something inside the project
 * is linked to an entity of another project: those links must be removed first.
 */
@Service
@RequiredArgsConstructor
public class ProjectDeletionService {

    private static final String RU = "SELECT recording_unit_id FROM recording_unit WHERE fk_action_unit_id = :pid";
    private static final String SP = "SELECT specimen_id FROM specimen WHERE fk_action_unit_id = :pid "
            + "OR fk_recording_unit_id IN (" + RU + ")";
    private static final String ST = "SELECT study_id FROM study WHERE fk_action_unit_id = :pid";
    private static final String PH = "SELECT phase_id FROM phase WHERE fk_action_unit_id = :pid";
    private static final String CO = "SELECT container_id FROM container WHERE fk_action_unit_id = :pid";
    private static final String DOC = "SELECT document_id FROM siamois_document WHERE fk_action_unit_id = :pid";

    private static final String T_RU_HIERARCHY = "recording_unit_hierarchy";
    private static final String T_STRATI = "stratigraphic_relationship";
    private static final String T_SP_HIERARCHY = "specimen_hierarchy";
    private static final String C_PARENT = "fk_parent_id";
    private static final String C_CHILD = "fk_child_id";
    private static final String C_RU = "fk_recording_unit_id";
    private static final String C_PHASE = "fk_phase_id";
    private static final String C_SPECIMEN = "fk_specimen_id";
    private static final String C_CONTAINER = "fk_container_id";
    private static final String C_STUDY = "fk_study_id";
    private static final String C_DOCUMENT = "fk_document_id";
    private static final String IN_OPEN = " IN (";

    /** A link table whose rows must not straddle the project boundary. */
    private record Cross(String label, String table, String colA, String setA, String colB, String setB) {
        String countSql() {
            return "SELECT COUNT(*) FROM " + table + " WHERE (" + colA + IN_OPEN + setA + ")) <> (" + colB
                    + IN_OPEN + setB + "))";
        }
    }

    private static final List<Cross> CROSS_LINKS = List.of(
            new Cross("la hiérarchie des unités d'enregistrement", T_RU_HIERARCHY,
                    C_PARENT, RU, C_CHILD, RU),
            new Cross("des relations stratigraphiques", T_STRATI,
                    "fk_recording_unit_1_id", RU, "fk_recording_unit_2_id", RU),
            new Cross("la hiérarchie des mobiliers", T_SP_HIERARCHY, C_PARENT, SP, C_CHILD, SP),
            new Cross("la hiérarchie des contenants", "container", "container_id", CO, C_PARENT, CO),
            new Cross("des phases", "recording_unit_phase", C_RU, RU, C_PHASE, PH),
            new Cross("des phases (mobiliers)", "specimen_phase", C_SPECIMEN, SP, C_PHASE, PH),
            new Cross("des contenants (mobiliers)", "specimen_container", C_SPECIMEN, SP, C_CONTAINER, CO),
            new Cross("des études (unités d'enregistrement)", "study_recording_unit",
                    C_STUDY, ST, C_RU, RU),
            new Cross("des études (mobiliers)", "study_specimen", C_STUDY, ST, C_SPECIMEN, SP),
            new Cross("des documents (unités d'enregistrement)", "recording_unit_document",
                    C_RU, RU, C_DOCUMENT, DOC),
            new Cross("des documents (mobiliers)", "specimen_document", C_SPECIMEN, SP, C_DOCUMENT, DOC),
            new Cross("des documents (phases)", "phase_document", C_PHASE, PH, C_DOCUMENT, DOC),
            new Cross("des documents (contenants)", "container_document", C_CONTAINER, CO, C_DOCUMENT, DOC),
            new Cross("des documents (études)", "study_document", C_STUDY, ST, C_DOCUMENT, DOC)
    );

    /** Counts that must be zero: links of project content to things that are not part of the project. */
    private static final List<Cross> OTHER_BLOCKERS = List.of(
            new Cross("des mouvements de mobiliers", "specimen_movement", C_SPECIMEN, SP, C_SPECIMEN, SP),
            new Cross("des groupes de mobiliers", "specimen_group_attribution",
                    C_SPECIMEN, SP, C_SPECIMEN, SP)
    );

    private record LinkDeletion(String table, String column, String set) {
        String sql() {
            return "DELETE FROM " + table + " WHERE " + column + IN_OPEN + set + ")";
        }
    }

    private static final List<LinkDeletion> LINK_DELETIONS = List.of(
            new LinkDeletion(T_RU_HIERARCHY, C_PARENT, RU),
            new LinkDeletion(T_RU_HIERARCHY, C_CHILD, RU),
            new LinkDeletion(T_STRATI, "fk_recording_unit_1_id", RU),
            new LinkDeletion(T_STRATI, "fk_recording_unit_2_id", RU),
            new LinkDeletion("recording_unit_contributors", C_RU, RU),
            new LinkDeletion("recording_unit_phase", C_RU, RU),
            new LinkDeletion("recording_unit_document", C_RU, RU),
            new LinkDeletion("recording_unit_on_the_fly_fields", C_RU, RU),
            new LinkDeletion("study_recording_unit", C_RU, RU),
            new LinkDeletion(T_SP_HIERARCHY, C_PARENT, SP),
            new LinkDeletion(T_SP_HIERARCHY, C_CHILD, SP),
            new LinkDeletion("specimen_document", C_SPECIMEN, SP),
            new LinkDeletion("specimen_container", C_SPECIMEN, SP),
            new LinkDeletion("specimen_phase", C_SPECIMEN, SP),
            new LinkDeletion("study_specimen", C_SPECIMEN, SP),
            new LinkDeletion("phase_document", C_PHASE, PH),
            new LinkDeletion("container_document", C_CONTAINER, CO),
            new LinkDeletion("study_document", C_STUDY, ST),
            new LinkDeletion("spatial_unit_document", C_DOCUMENT, DOC)
    );

    @PersistenceContext
    private EntityManager entityManager;

    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final PhaseRepository phaseRepository;
    private final ContainerRepository containerRepository;
    private final StudyRepository studyRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final FormConfigAnswerRepository formConfigAnswerRepository;
    private final FormConfigRepository formConfigRepository;
    private final FormConfigGroupRepository formConfigGroupRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final ConceptFieldStateRepository conceptFieldStateRepository;
    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final ProjectCodeSourceRepository projectCodeSourceRepository;
    private final ArkRepository arkRepository;

    /**
     * Labels of what keeps the project content tied to other projects (empty when it can be deleted).
     */
    @Transactional(readOnly = true)
    public List<String> crossProjectLinks(long projectId) {
        return findCrossProjectLinks(projectId);
    }

    private List<String> findCrossProjectLinks(long projectId) {
        List<String> found = new ArrayList<>();
        for (Cross cross : CROSS_LINKS) {
            if (count(cross.countSql(), projectId) > 0) {
                found.add(cross.label());
            }
        }
        if (count("SELECT COUNT(*) FROM specimen WHERE fk_recording_unit_id IN (" + RU + ") "
                + "AND fk_action_unit_id IS NOT NULL AND fk_action_unit_id <> :pid", projectId) > 0) {
            found.add("des mobiliers rattachés à un autre projet");
        }
        for (Cross blocker : OTHER_BLOCKERS) {
            String sql = "SELECT COUNT(*) FROM " + blocker.table() + " WHERE " + blocker.colA() + IN_OPEN + SP + ")";
            if (count(sql, projectId) > 0) {
                found.add(blocker.label());
            }
        }
        return found;
    }

    /**
     * Deletes the project content (not the project row itself, nor its members and hierarchy: the caller
     * does that).
     *
     * @throws IllegalStateException when content is linked to another project
     */
    @Transactional
    public List<Long> deleteContent(long projectId) {
        List<String> links = findCrossProjectLinks(projectId);
        if (!links.isEmpty()) {
            throw new IllegalStateException("Impossible de supprimer : le contenu du projet est lié à d'autres projets ("
                    + String.join(", ", links) + "). Retirez d'abord ces relations.");
        }

        List<Long> arkIds = new ArrayList<>();
        arkIds.addAll(ids("SELECT fk_ark_id FROM recording_unit WHERE fk_action_unit_id = :pid AND fk_ark_id IS NOT NULL", projectId));
        arkIds.addAll(ids("SELECT fk_ark_id FROM specimen WHERE specimen_id IN (" + SP + ") AND fk_ark_id IS NOT NULL", projectId));

        List<Long> specimenIds = ids(SP, projectId);
        List<Long> recordingUnitIds = ids(RU, projectId);
        List<Long> phaseIds = ids(PH, projectId);
        List<Long> containerIds = ids(CO, projectId);
        List<Long> studyIds = ids(ST, projectId);
        List<Long> documentIds = ids(DOC, projectId);

        deleteAnswerSets(recordingUnitIds, specimenIds, phaseIds, containerIds, studyIds);

        for (LinkDeletion deletion : LINK_DELETIONS) {
            entityManager.createNativeQuery(deletion.sql()).setParameter("pid", projectId).executeUpdate();
        }
        entityManager.createNativeQuery("UPDATE container SET fk_parent_id = NULL WHERE container_id IN (" + CO + ")")
                .setParameter("pid", projectId).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        specimenRepository.deleteAllById(specimenIds);
        studyRepository.deleteAllById(studyIds);
        containerRepository.deleteAllById(containerIds);
        phaseRepository.deleteAllById(phaseIds);
        recordingUnitRepository.deleteAllById(recordingUnitIds);
        entityManager.flush();

        for (Long documentId : documentIds) {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                documentService.deleteDocument(document);
            }
        }

        deleteProjectConfiguration(projectId);
        return arkIds;
    }

    /** Removes the ARK identifiers once the rows that pointed at them are gone. */
    @Transactional
    public void deleteArks(List<Long> arkIds) {
        if (arkIds != null && !arkIds.isEmpty()) {
            arkRepository.deleteAllById(arkIds);
        }
    }

    private void deleteAnswerSets(List<Long> recordingUnitIds, List<Long> specimenIds, List<Long> phaseIds,
                                  List<Long> containerIds, List<Long> studyIds) {
        List<FormConfigAnswer> sets = new ArrayList<>();
        recordingUnitIds.forEach(id -> sets.addAll(formConfigAnswerRepository
                .findByRecordingUnit(entityManager.getReference(RecordingUnit.class, id))));
        specimenIds.forEach(id -> sets.addAll(formConfigAnswerRepository
                .findBySpecimen(entityManager.getReference(Specimen.class, id))));
        phaseIds.forEach(id -> sets.addAll(formConfigAnswerRepository
                .findByPhase(entityManager.getReference(Phase.class, id))));
        containerIds.forEach(id -> sets.addAll(formConfigAnswerRepository
                .findByContainer(entityManager.getReference(Container.class, id))));
        studyIds.forEach(id -> sets.addAll(formConfigAnswerRepository
                .findByStudy(entityManager.getReference(Study.class, id))));
        customFieldAnswerService.deleteAnswerSets(sets);
    }

    private void deleteProjectConfiguration(long projectId) {
        for (FormConfig formConfig : formConfigRepository.findAllByActionUnitId(projectId)) {
            Long id = formConfig.getId();
            entityManager.createNativeQuery("DELETE FROM identifier_counter WHERE fk_form_config_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM institution_identifier_counter WHERE fk_form_config_id = :id")
                    .setParameter("id", id).executeUpdate();
            conceptFieldStateRepository.deleteAll(conceptFieldStateRepository.findAllByFormConfigId(id));
            fieldFormConfigRepository.deleteAll(fieldFormConfigRepository.findAllByFormConfigId(id));
            entityManager.flush();
            formConfigGroupRepository.deleteAll(formConfigGroupRepository.findAllByFormConfigIdOrderByPosition(id));
            entityManager.flush();
            formConfigRepository.delete(formConfig);
        }
        entityManager.createNativeQuery("DELETE FROM identifier_counter WHERE fk_action_unit_id = :pid")
                .setParameter("pid", projectId).executeUpdate();
        documentationThesaurusRepository.deleteAll(documentationThesaurusRepository.findAllByActionUnitId(projectId));
        projectCodeSourceRepository.deleteAll(projectCodeSourceRepository.findAllByActionUnitId(projectId));
        entityManager.flush();
    }

    private long count(String sql, long projectId) {
        Number n = (Number) entityManager.createNativeQuery(sql).setParameter("pid", projectId).getSingleResult();
        return n == null ? 0 : n.longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(String sql, long projectId) {
        List<Number> rows = entityManager.createNativeQuery(sql).setParameter("pid", projectId).getResultList();
        return rows.stream().map(Number::longValue).toList();
    }
}
