package fr.siamois.infrastructure.database.repositories.recordingunit;

/**
 * Flat projection for displaying a {@code RecordingUnit} as a parent/child hierarchy chip
 * ({@code RecordingUnitSummaryDTO}) without hydrating the full entity — which, through
 * {@link fr.siamois.domain.models.recordingunit.RecordingUnitParent}'s eight EAGER {@code Concept}
 * associations plus its EAGER {@code ActionUnit} and {@code SpatialUnit}, otherwise pulls in a huge
 * join graph (confirmed the single most expensive query pair when opening a RecordingUnit panel,
 * ~20-35ms each, rerun on every ajax round-trip including plain autocomplete keystrokes) for data the
 * chip never displays.
 */
public interface RecordingUnitSummaryProjection {

    Long getId();

    Integer getIdentifier();

    String getFullIdentifier();

    Long getTypeId();

    String getTypeExternalId();

    boolean isTypeDeleted();

    Long getVocabularyId();

    String getVocabularyBaseUri();

    String getVocabularyExternalId();

    Long getVocabularyTypeId();

    String getVocabularyTypeLabel();
}
