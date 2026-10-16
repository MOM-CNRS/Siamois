package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Optional;

/**
 * One link between two entities, stored once; the other side of the relation is a view of this row
 * (see {@link RelationMirror}). Each side has one nullable real FK per {@link RelationEntityKind}, and
 * exactly one of them is set (check constraint). Entities are linked by {@code ON DELETE CASCADE}, so
 * deleting an entity drops its links.
 * <p>
 * {@code sourceType} / {@code targetType} are the types of the link as seen from each side ("cuts" /
 * "is cut by"); both are optional, and the pair is tied by the definition's {@link RelationTypeMapping}s.
 * The link's shared attributes (uncertain, comment...) are ordinary form answers: a
 * {@code form_config_answer} row owned by the link.
 * <p>
 * The pair is unique per definition ({@code uk_relation_link_pair}, an expression index created at start-up
 * since a plain constraint ignores NULL columns); a symmetric relation stores the lower id as source.
 */
@Entity
@Table(name = "relation_link", indexes = {
        @Index(name = "idx_relation_link_src_recording_unit", columnList = "fk_source_recording_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_specimen", columnList = "fk_source_specimen_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_phase", columnList = "fk_source_phase_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_container", columnList = "fk_source_container_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_spatial_unit", columnList = "fk_source_spatial_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_action_unit", columnList = "fk_source_action_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_document", columnList = "fk_source_document_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_src_study", columnList = "fk_source_study_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_recording_unit", columnList = "fk_target_recording_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_specimen", columnList = "fk_target_specimen_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_phase", columnList = "fk_target_phase_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_container", columnList = "fk_target_container_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_spatial_unit", columnList = "fk_target_spatial_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_action_unit", columnList = "fk_target_action_unit_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_person", columnList = "fk_target_person_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_document", columnList = "fk_target_document_id, fk_relation_definition_id"),
        @Index(name = "idx_relation_link_tgt_study", columnList = "fk_target_study_id, fk_relation_definition_id")
})
@Check(name = "ck_relation_link_one_end_per_side", constraints = "(CASE WHEN fk_source_recording_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_specimen_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_phase_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_container_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_spatial_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_action_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_document_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_source_study_id IS NULL THEN 0 ELSE 1 END) = 1 AND (CASE WHEN fk_target_recording_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_specimen_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_phase_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_container_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_spatial_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_action_unit_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_person_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_document_id IS NULL THEN 0 ELSE 1 END + CASE WHEN fk_target_study_id IS NULL THEN 0 ELSE 1 END) = 1")
@Getter
@Setter
public class RelationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_link_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_relation_definition_id", nullable = false)
    private RelationDefinition definition;

    /** The type of the link seen from the source side, if the definition is typed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_source_type_concept_id")
    private Concept sourceType;

    /** The type of the link seen from the target side, if the definition is typed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_target_type_concept_id")
    private Concept targetType;

    // ---- source end: exactly one of these is set ----

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_recording_unit_id")
    private RecordingUnit sourceRecordingUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_specimen_id")
    private Specimen sourceSpecimen;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_phase_id")
    private Phase sourcePhase;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_container_id")
    private Container sourceContainer;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_spatial_unit_id")
    private SpatialUnit sourceSpatialUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_action_unit_id")
    private ActionUnit sourceActionUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_document_id")
    private Document sourceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_source_study_id")
    private Study sourceStudy;

    // ---- target end: exactly one of these is set ----

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_recording_unit_id")
    private RecordingUnit targetRecordingUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_specimen_id")
    private Specimen targetSpecimen;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_phase_id")
    private Phase targetPhase;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_container_id")
    private Container targetContainer;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_spatial_unit_id")
    private SpatialUnit targetSpatialUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_action_unit_id")
    private ActionUnit targetActionUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_person_id")
    private Person targetPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_document_id")
    private Document targetDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_target_study_id")
    private Study targetStudy;

    /** Sets the source end to {@code entity} (clearing the others); false if its kind is not supported on this side. */
    public boolean attachSource(Object entity) {
        clearSource();
        if (entity instanceof RecordingUnit e) {
            sourceRecordingUnit = e;
            return true;
        }
        if (entity instanceof Specimen e) {
            sourceSpecimen = e;
            return true;
        }
        if (entity instanceof Phase e) {
            sourcePhase = e;
            return true;
        }
        if (entity instanceof Container e) {
            sourceContainer = e;
            return true;
        }
        if (entity instanceof SpatialUnit e) {
            sourceSpatialUnit = e;
            return true;
        }
        if (entity instanceof ActionUnit e) {
            sourceActionUnit = e;
            return true;
        }
        if (entity instanceof Document e) {
            sourceDocument = e;
            return true;
        }
        if (entity instanceof Study e) {
            sourceStudy = e;
            return true;
        }
        return false;
    }

    private void clearSource() {
        sourceRecordingUnit = null;
        sourceSpecimen = null;
        sourcePhase = null;
        sourceContainer = null;
        sourceSpatialUnit = null;
        sourceActionUnit = null;
        sourceDocument = null;
        sourceStudy = null;
    }

    /** The kind of the source end, empty on an unsaved link whose end is not set yet. */
    public Optional<RelationEntityKind> sourceKind() {
        if (sourceRecordingUnit != null) return Optional.of(RelationEntityKind.RECORDING_UNIT);
        if (sourceSpecimen != null) return Optional.of(RelationEntityKind.SPECIMEN);
        if (sourcePhase != null) return Optional.of(RelationEntityKind.PHASE);
        if (sourceContainer != null) return Optional.of(RelationEntityKind.CONTAINER);
        if (sourceSpatialUnit != null) return Optional.of(RelationEntityKind.SPATIAL_UNIT);
        if (sourceActionUnit != null) return Optional.of(RelationEntityKind.ACTION_UNIT);
        if (sourceDocument != null) return Optional.of(RelationEntityKind.DOCUMENT);
        if (sourceStudy != null) return Optional.of(RelationEntityKind.STUDY);
        return Optional.empty();
    }

    /** The id of the source end's entity, empty if the end is not set. */
    public Optional<Long> sourceId() {
        if (sourceRecordingUnit != null) return Optional.ofNullable(sourceRecordingUnit.getId());
        if (sourceSpecimen != null) return Optional.ofNullable(sourceSpecimen.getId());
        if (sourcePhase != null) return Optional.ofNullable(sourcePhase.getId());
        if (sourceContainer != null) return Optional.ofNullable(sourceContainer.getId());
        if (sourceSpatialUnit != null) return Optional.ofNullable(sourceSpatialUnit.getId());
        if (sourceActionUnit != null) return Optional.ofNullable(sourceActionUnit.getId());
        if (sourceDocument != null) return Optional.ofNullable(sourceDocument.getId());
        if (sourceStudy != null) return Optional.ofNullable(sourceStudy.getId());
        return Optional.empty();
    }

    /** Sets the target end to {@code entity} (clearing the others); false if its kind is not supported on this side. */
    public boolean attachTarget(Object entity) {
        clearTarget();
        if (entity instanceof RecordingUnit e) {
            targetRecordingUnit = e;
            return true;
        }
        if (entity instanceof Specimen e) {
            targetSpecimen = e;
            return true;
        }
        if (entity instanceof Phase e) {
            targetPhase = e;
            return true;
        }
        if (entity instanceof Container e) {
            targetContainer = e;
            return true;
        }
        if (entity instanceof SpatialUnit e) {
            targetSpatialUnit = e;
            return true;
        }
        if (entity instanceof ActionUnit e) {
            targetActionUnit = e;
            return true;
        }
        if (entity instanceof Person e) {
            targetPerson = e;
            return true;
        }
        if (entity instanceof Document e) {
            targetDocument = e;
            return true;
        }
        if (entity instanceof Study e) {
            targetStudy = e;
            return true;
        }
        return false;
    }

    private void clearTarget() {
        targetRecordingUnit = null;
        targetSpecimen = null;
        targetPhase = null;
        targetContainer = null;
        targetSpatialUnit = null;
        targetActionUnit = null;
        targetPerson = null;
        targetDocument = null;
        targetStudy = null;
    }

    /** The kind of the target end, empty on an unsaved link whose end is not set yet. */
    public Optional<RelationEntityKind> targetKind() {
        if (targetRecordingUnit != null) return Optional.of(RelationEntityKind.RECORDING_UNIT);
        if (targetSpecimen != null) return Optional.of(RelationEntityKind.SPECIMEN);
        if (targetPhase != null) return Optional.of(RelationEntityKind.PHASE);
        if (targetContainer != null) return Optional.of(RelationEntityKind.CONTAINER);
        if (targetSpatialUnit != null) return Optional.of(RelationEntityKind.SPATIAL_UNIT);
        if (targetActionUnit != null) return Optional.of(RelationEntityKind.ACTION_UNIT);
        if (targetPerson != null) return Optional.of(RelationEntityKind.PERSON);
        if (targetDocument != null) return Optional.of(RelationEntityKind.DOCUMENT);
        if (targetStudy != null) return Optional.of(RelationEntityKind.STUDY);
        return Optional.empty();
    }

    /** The id of the target end's entity, empty if the end is not set. */
    public Optional<Long> targetId() {
        if (targetRecordingUnit != null) return Optional.ofNullable(targetRecordingUnit.getId());
        if (targetSpecimen != null) return Optional.ofNullable(targetSpecimen.getId());
        if (targetPhase != null) return Optional.ofNullable(targetPhase.getId());
        if (targetContainer != null) return Optional.ofNullable(targetContainer.getId());
        if (targetSpatialUnit != null) return Optional.ofNullable(targetSpatialUnit.getId());
        if (targetActionUnit != null) return Optional.ofNullable(targetActionUnit.getId());
        if (targetPerson != null) return Optional.ofNullable(targetPerson.getId());
        if (targetDocument != null) return Optional.ofNullable(targetDocument.getId());
        if (targetStudy != null) return Optional.ofNullable(targetStudy.getId());
        return Optional.empty();
    }

}
