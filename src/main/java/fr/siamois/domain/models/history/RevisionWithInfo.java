package fr.siamois.domain.models.history;

import org.hibernate.envers.RevisionType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

public record RevisionWithInfo<T>(T entity, InfoRevisionEntity revisionEntity,
                                  RevisionType revisionType) implements Comparable<RevisionWithInfo<T>> {
    public OffsetDateTime getDate() {
        Instant instant = Instant.ofEpochMilli(revisionEntity.getEpochTimestamp());
        return OffsetDateTime.ofInstant(instant, OffsetDateTime.now().getOffset());
    }

    /**
     * Compare first by timestamp, then by revision id to avoid equality when two revisions have the same timestamp in descending order.
     * @param o the other revision
     * @return the comparison result
     */
    @Override
    public int compareTo(RevisionWithInfo<T> o) {
        int cmp = Long.compare(revisionEntity.getEpochTimestamp(), o.revisionEntity.getEpochTimestamp());
        if (cmp != 0) return cmp * -1;
        return Long.compare(revisionEntity.getRevId(), o.revisionEntity.getRevId()) * -1;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RevisionWithInfo<?> that)) return false;
        return Objects.equals(revisionEntity, that.revisionEntity) && revisionType == that.revisionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(revisionEntity, revisionType);
    }
}