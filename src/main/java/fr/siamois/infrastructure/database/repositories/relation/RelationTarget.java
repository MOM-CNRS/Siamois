package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationEntityKind;

/**
 * Where the entities at the far end of a relation field are read from, for each kind of entity: their table, key
 * column, the SQL expression of the label shown for them, and the API {@code resourceType} they are served as.
 * Every expression is a constant; the alias of the table is {@code t}.
 */
enum RelationTarget {
    RECORDING_UNIT(RelationEntityKind.RECORDING_UNIT, "recording_unit", "recording_unit_id", "t.full_identifier", "recording-units"),
    SPECIMEN(RelationEntityKind.SPECIMEN, "specimen", "specimen_id", "t.full_identifier", "finds"),
    PHASE(RelationEntityKind.PHASE, "phase", "phase_id", "coalesce(t.title, t.identifier)", "phases"),
    CONTAINER(RelationEntityKind.CONTAINER, "container", "container_id", "t.identifier", "containers"),
    SPATIAL_UNIT(RelationEntityKind.SPATIAL_UNIT, "spatial_unit", "spatial_unit_id", "t.name", "places"),
    ACTION_UNIT(RelationEntityKind.ACTION_UNIT, "action_unit", "action_unit_id", "coalesce(t.name, t.full_identifier)", "projects"),
    PERSON(RelationEntityKind.PERSON, "person", "person_id",
            "trim(coalesce(t.name, '') || ' ' || coalesce(t.lastname, ''))", "persons"),
    DOCUMENT(RelationEntityKind.DOCUMENT, "siamois_document", "document_id",
            "coalesce(t.title, t.identifier, t.file_name)", "documents"),
    STUDY(RelationEntityKind.STUDY, "study", "study_id", "t.identifier", "studies");

    final RelationEntityKind kind;
    final String table;
    final String key;
    final String label;
    final String resourceType;

    RelationTarget(RelationEntityKind kind, String table, String key, String label, String resourceType) {
        this.kind = kind;
        this.table = table;
        this.key = key;
        this.label = label;
        this.resourceType = resourceType;
    }

    static RelationTarget of(RelationEntityKind kind) {
        for (RelationTarget target : values()) {
            if (target.kind == kind) return target;
        }
        throw new IllegalArgumentException("No relation target for " + kind);
    }
}
