package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class ContainerSpec {

    public static final String GLOBAL_FILTER = "global";
    public static final String NAME_FILTER = "name";
    public static final String ID_FILTER = "id";

    private ContainerSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static Specification<Container> belongsToInstitution(long institutionId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId));
    }

    @NonNull
    public static Specification<Container> nameContaining(@Nullable String name) {
        return ((root, query, criteriaBuilder) -> {
            if (name == null || name.isBlank())
                return null;
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        });
    }

    public static Specification<Container> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("parent")));
    }

    public static Specification<Container> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

}
