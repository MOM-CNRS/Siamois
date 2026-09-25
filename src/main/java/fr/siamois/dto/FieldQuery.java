package fr.siamois.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * A list's sort and filters on form fields (a column keyed by field id rather than by an entity
 * property), already turned into a JPA {@link Specification} by the API layer — services only AND
 * it onto their own query.
 *
 * <p>When it sorts ({@link #ordered()}), the ordering is part of the specification (a correlated
 * subquery or a label lookup cannot be a {@code Sort} property): the service must then query with
 * an unsorted {@link Pageable} — {@link #pageable(Pageable)} — or the pageable's own sort would
 * override it.</p>
 */
public record FieldQuery(@Nullable Specification<?> specification, boolean ordered) {

    public static final FieldQuery NONE = new FieldQuery(null, false);

    @SuppressWarnings("unchecked")
    public <E> Specification<E> specificationFor(Class<E> entityType) {
        return specification == null ? (root, query, cb) -> null : (Specification<E>) specification;
    }

    public Pageable pageable(Pageable pageable) {
        return ordered && pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : pageable;
    }
}
