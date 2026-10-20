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
 *
 * <p>{@link #filtered()}: at least one filter (not just a sort) is part of the specification — what
 * makes the list "narrowed" for the tree view, like a search. {@link #ordering()} is the sort alone,
 * without the filters: the tree view orders every level by it but must not narrow the context parents
 * by the filters (they are what the closure of ancestors is for).</p>
 */
public record FieldQuery(@Nullable Specification<?> specification, boolean ordered, boolean filtered,
                         @Nullable Specification<?> ordering) {

    public static final FieldQuery NONE = new FieldQuery(null, false, false, null);

    public FieldQuery(@Nullable Specification<?> specification, boolean ordered, boolean filtered) {
        this(specification, ordered, filtered, null);
    }

    @SuppressWarnings("unchecked")
    public <E> Specification<E> specificationFor() {
        return specification == null ? (root, query, cb) -> null : (Specification<E>) specification;
    }

    /** The sort alone (no filter), for a query that applies the filters its own way. */
    @SuppressWarnings("unchecked")
    public <E> Specification<E> orderingSpecificationFor() {
        return ordering == null ? (root, query, cb) -> null : (Specification<E>) ordering;
    }

    public Pageable pageable(Pageable pageable) {
        return ordered && pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : pageable;
    }
}
