package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntitySource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ProjectSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Source;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.TechnicalSource;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Lit les lignes d'une source de feuille pour un projet : les entités du projet (filtrées par type), le
 * projet lui-même, ou une table technique du registre {@link ExportTechnicalSource}. Les lignes sont
 * toujours bornées au projet. À appeler dans une transaction (les relations sont chargées à la demande).
 */
@Service
@RequiredArgsConstructor
public class ExportSourceReader {

    private final ActionUnitRepository actionUnitRepository;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final PhaseRepository phaseRepository;
    private final DocumentRepository documentRepository;
    private final ContainerRepository containerRepository;
    private final StratigraphicRelationshipRepository stratigraphicRelationshipRepository;

    /**
     * @throws NoSuchElementException   si le projet n'existe pas
     * @throws IllegalArgumentException si la clé d'une source technique n'est pas dans le registre
     */
    @Transactional(readOnly = true)
    public List<ExportRow> read(Source source, Long projectId) {
        if (source instanceof ProjectSource) {
            return List.of(ExportRow.of(ExportSubject.PROJECT, project(projectId)));
        }
        if (source instanceof EntitySource entitySource) {
            return readEntities(entitySource, projectId);
        }
        TechnicalSource technical = (TechnicalSource) source;
        ExportTechnicalSource key = ExportTechnicalSource.ofKey(technical.key())
                .orElseThrow(() -> new IllegalArgumentException("Unknown technical source: " + technical.key()));
        return readTechnical(key, projectId);
    }

    // ------------------------------------------------------------------ entités

    private List<ExportRow> readEntities(EntitySource source, Long projectId) {
        ExportSubject subject = ExportSubject.of(source.entity());
        List<?> entities = switch (subject) {
            case RECORDING_UNIT -> recordingUnitRepository.findAllByActionUnitId(projectId);
            case SPECIMEN -> specimenRepository.findAllByActionUnitId(projectId);
            case PHASE -> phaseRepository.findAllByActionUnitId(projectId);
            case DOCUMENT -> documentRepository.findAllByActionUnitId(projectId);
            case CONTAINER -> containerRepository.findAllByActionUnitId(projectId);
            case SPATIAL_UNIT -> spatialUnitsOf(project(projectId));
            case PROJECT -> throw new IllegalArgumentException("Use the PROJECT source for the project itself");
        };
        return entities.stream()
                .filter(e -> matchesTypes(typeOf(subject, e), source.types()))
                .map(e -> ExportRow.of(subject, e))
                .toList();
    }

    /** Lieu principal et contexte spatial du projet, avec tous leurs descendants. */
    private static List<SpatialUnit> spatialUnitsOf(ActionUnit project) {
        Map<Long, SpatialUnit> found = new LinkedHashMap<>();
        Deque<SpatialUnit> toVisit = new ArrayDeque<>();
        if (project.getMainLocation() != null) toVisit.add(project.getMainLocation());
        if (project.getSpatialContext() != null) toVisit.addAll(project.getSpatialContext());
        while (!toVisit.isEmpty()) {
            SpatialUnit unit = toVisit.poll();
            if (found.putIfAbsent(unit.getId(), unit) == null && unit.getChildren() != null) {
                toVisit.addAll(unit.getChildren());
            }
        }
        return List.copyOf(found.values());
    }

    @Nullable
    private static Concept typeOf(ExportSubject subject, Object entity) {
        return switch (subject) {
            case RECORDING_UNIT -> ((RecordingUnit) entity).getType();
            case SPECIMEN -> ((Specimen) entity).getCategory();
            case PHASE -> ((Phase) entity).getType();
            case DOCUMENT -> ((Document) entity).getCategory();
            case CONTAINER -> ((Container) entity).getType();
            case SPATIAL_UNIT -> ((SpatialUnit) entity).getCategory();
            case PROJECT -> null;
        };
    }

    /** Liste de types vide = tous les types ; sinon le type de l'entité doit en faire partie. */
    static boolean matchesTypes(@Nullable Concept type, List<ConceptRef> types) {
        if (types.isEmpty()) {
            return true;
        }
        if (type == null || type.getVocabulary() == null || type.getExternalId() == null) {
            return false;
        }
        return types.stream().anyMatch(ref ->
                type.getExternalId().equalsIgnoreCase(ref.conceptId())
                        && ref.thesaurusId().equalsIgnoreCase(type.getVocabulary().getExternalVocabularyId()));
    }

    // ------------------------------------------------------------------ tables techniques

    private List<ExportRow> readTechnical(ExportTechnicalSource key, Long projectId) {
        List<RecordingUnit> units = recordingUnitRepository.findAllByActionUnitId(projectId);
        return switch (key) {
            case RECORDING_UNIT_HIERARCHY -> hierarchyRows(units);
            case STRATIGRAPHIC_RELATIONSHIP -> stratigraphicRows(units);
        };
    }

    private static List<ExportRow> hierarchyRows(List<RecordingUnit> units) {
        return units.stream()
                .flatMap(parent -> parent.getChildren().stream().map(child -> ExportRow.technical(
                        Map.of("parent", ExportRow.of(ExportSubject.RECORDING_UNIT, parent),
                                "child", ExportRow.of(ExportSubject.RECORDING_UNIT, child)),
                        Map.of())))
                .toList();
    }

    private List<ExportRow> stratigraphicRows(List<RecordingUnit> units) {
        List<Long> ids = units.stream().map(RecordingUnit::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return stratigraphicRelationshipRepository.findAllByUnit1IdIn(ids).stream()
                .map(ExportSourceReader::toRow)
                .toList();
    }

    private static ExportRow toRow(StratigraphicRelationship rel) {
        Map<String, Object> values = new HashMap<>();
        if (rel.getConcept() != null) values.put("relationType", rel.getConcept());
        if (rel.getIsAsynchronous() != null) values.put("asynchronous", rel.getIsAsynchronous());
        if (rel.getUncertain() != null) values.put("uncertain", rel.getUncertain());
        return ExportRow.technical(
                Map.of("unit1", ExportRow.of(ExportSubject.RECORDING_UNIT, rel.getUnit1()),
                        "unit2", ExportRow.of(ExportSubject.RECORDING_UNIT, rel.getUnit2())),
                values);
    }

    private ActionUnit project(Long projectId) {
        return actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project " + projectId + " not found"));
    }
}
