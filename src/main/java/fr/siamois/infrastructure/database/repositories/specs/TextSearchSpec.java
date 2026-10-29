package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.dto.FilterDTO;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A case-insensitive text search on one attribute, whatever the entity: "contains" or, for a
 * {@link FilterDTO.FilterType#START_WITH} filter, "starts with".
 */
public final class TextSearchSpec {

    private TextSearchSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    /**
     * The rows whose {@code attribute} matches {@code text} the way {@code type} says — starting with
     * it for {@link FilterDTO.FilterType#START_WITH}, containing it otherwise; no restriction when
     * {@code text} is blank.
     */
    @NonNull
    public static <T> Specification<T> matching(@NonNull String attribute, @Nullable String text,
                                                @Nullable FilterDTO.FilterType type) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String lowered = text.toLowerCase();
            String pattern = type == FilterDTO.FilterType.START_WITH ? lowered + "%" : "%" + lowered + "%";
            return cb.like(cb.lower(root.get(attribute)), pattern);
        };
    }

    /**
     * Same, with the text and the type of {@code filter}; no restriction when there is no filter.
     */
    @NonNull
    public static <T> Specification<T> matching(@NonNull String attribute, @Nullable FilterDTO.FilterInfo filter) {
        if (filter == null) {
            return (root, query, cb) -> null;
        }
        return matching(attribute, filter.valueAsString(), filter.getType());
    }
}
