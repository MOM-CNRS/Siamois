package fr.siamois.domain.services.history;

import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.domain.models.history.RevisionWithInfo;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryAuditService {

    private final AuditReader auditReader;

    @SuppressWarnings("unchecked")
    public <T> List<RevisionWithInfo<T>> findAllRevisionForEntity(Class<T> entityClass, Long entityId) {
        SortedSet<RevisionWithInfo<T>> results = new TreeSet<>();

        List<Object[]> rows = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .getResultList();

        for (Object[] row : rows) {
            RevisionWithInfo<T> info = new RevisionWithInfo<>(
                    (T) row[0],
                    (InfoRevisionEntity) row[1],
                    (RevisionType) row[2]
            );
            results.add(info);
        }

        return results.stream().toList();
    }

    /**
     * Find the last revision of the entity with the given ID. If the revision does not exist, returns a system user
     * @param entityClass The class of the entity
     * @param entityId The ID of the entity
     * @return The revision
     * @param <T> The type of the entity
     */
    @SuppressWarnings("unchecked")
    public <T> RevisionWithInfo<T> findLastRevisionForEntity(Class<T> entityClass, Long entityId) {
        AuditQuery query = auditReader.createQuery().forRevisionsOfEntity(entityClass, false, false);
        query.add(AuditEntity.id().eq(entityId));
        query.add(AuditEntity.revisionNumber().maximize().computeAggregationInInstanceContext());

        try {
            Object[] result = (Object[]) query.getSingleResult();


            return new RevisionWithInfo<>(
                    (T) result[0],
                    (InfoRevisionEntity) result[1],
                    (RevisionType) result[2]
            );
        } catch (NoResultException e) {
            return null;
        }

    }

    /**
     * Restores the non-audited fields of a revision entity by copying their values from the original entity.
     * This method only updates the in-memory state of the revision entity and does not persist it to the database.
     *
     * @param revision   The revision entity whose non-audited fields will be restored
     * @param baseEntity The original entity from which non-audited field values are copied
     * @param <T>        The type of the entity to restore
     * @return The revision entity with non-audited fields set to the values of the original entity
     */

    public <T> T restoreEntity(RevisionWithInfo<T> revision, T baseEntity) {
        for (Field field : baseEntity.getClass().getFields()) {
            try {
                if (field.isAnnotationPresent(NotAudited.class)) {
                    field.setAccessible(true);
                    field.set(revision.entity(), field.get(baseEntity));
                }
            } catch (IllegalAccessException e) {
                log.warn("Could not set field {}", field.getName(), e);
            }

        }
        return revision.entity();
    }
}
