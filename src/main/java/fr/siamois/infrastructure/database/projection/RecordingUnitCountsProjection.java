package fr.siamois.infrastructure.database.projection;

public interface RecordingUnitCountsProjection {
    Long getRelatedSpecimenCount();
    Long getRuParentsCount();
    Long getRuChildrensCount();
    Long getRuRelationshipsCount();
}
