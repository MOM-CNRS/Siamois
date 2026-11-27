package fr.siamois.domain.services.relation;

import org.springframework.lang.Nullable;

/**
 * Where a rule set applies: the platform default (no institution, no project), an institution (no project), or a
 * project (with the institution it belongs to).
 */
public record RelationScope(@Nullable Long institutionId, @Nullable Long projectId) {

    public RelationScope {
        if (projectId != null && institutionId == null) {
            throw new IllegalArgumentException("A project scope names the project's institution");
        }
    }

    public static RelationScope platform() {
        return new RelationScope(null, null);
    }

    public static RelationScope institution(long institutionId) {
        return new RelationScope(institutionId, null);
    }

    public static RelationScope project(long institutionId, long projectId) {
        return new RelationScope(institutionId, projectId);
    }

    public boolean isPlatform() {
        return institutionId == null;
    }
}
