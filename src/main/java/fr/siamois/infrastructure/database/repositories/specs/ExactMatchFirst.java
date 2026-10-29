package fr.siamois.infrastructure.database.repositories.specs;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ranks the rows whose text attribute equals the searched text (ignoring case) ahead of the others,
 * the requested sort applying after that — so a search for {@code EXEMPLE} shows {@code EXEMPLE}
 * before {@code EXEMPLE.1}, {@code EXEMPLE.2}… even when the page is too short to hold every match.
 *
 * <p>The ranking has to be done by the database: a page of "contains" matches sorted by creation
 * date may not hold the exact match at all. Spring Data applies a {@link Pageable}'s sort after the
 * specification, replacing any order the specification set, so the sort is moved into the
 * specification instead — behind the ranking and behind the orders an earlier specification already
 * set (the synthetic sorts, the sorts on form fields).</p>
 *
 * <p>A {@code SELECT DISTINCT} query is left unranked: PostgreSQL refuses an ORDER BY expression
 * that is not in its select list.</p>
 */
public final class ExactMatchFirst {

    private ExactMatchFirst() {
        throw new UnsupportedOperationException("Utility class should never be instantiated");
    }

    /**
     * Pages {@code repository} with the exact matches of {@code searched} on {@code attribute} first;
     * a plain {@code findAll} when nothing is searched.
     *
     * @param repository    the repository to query
     * @param specification what the rows must match
     * @param pageable      the page and the sort that applies after the ranking
     * @param attribute     the entity attribute compared to the searched text
     * @param searched      the searched text, null or blank for no ranking
     * @param <T>           the entity type
     * @return the page, exact matches first
     */
    @NonNull
    public static <T> Page<T> findAll(@NonNull JpaSpecificationExecutor<T> repository,
                                      @NonNull Specification<T> specification,
                                      @NonNull Pageable pageable,
                                      @NonNull String attribute,
                                      @Nullable String searched) {
        if (searched == null || searched.isBlank()) {
            return repository.findAll(specification, pageable);
        }
        return repository.findAll(rank(specification, pageable.getSort(), attribute, searched), withoutSort(pageable));
    }

    /**
     * {@code specification}, ordering its rows by exact match on {@code attribute} first, then by the
     * orders already set on the query, then by {@code sort}.
     *
     * @param specification what the rows must match
     * @param sort          the sort that applies after the ranking
     * @param attribute     the entity attribute compared to the searched text
     * @param searched      the searched text
     * @param <T>           the entity type
     * @return the ranking specification
     */
    @NonNull
    static <T> Specification<T> rank(@NonNull Specification<T> specification, @NonNull Sort sort,
                                     @NonNull String attribute, @NonNull String searched) {
        String lowered = searched.toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            Predicate predicate = specification.toPredicate(root, query, cb);
            if (isCountQuery(query)) {
                return predicate;
            }
            List<Order> orders = new ArrayList<>();
            if (!query.isDistinct()) {
                orders.add(cb.asc(cb.selectCase()
                        .when(cb.equal(cb.lower(root.get(attribute)), lowered), 0)
                        .otherwise(1)));
            }
            orders.addAll(query.getOrderList());
            orders.addAll(QueryUtils.toOrders(sort, root, cb));
            query.orderBy(orders);
            return predicate;
        };
    }

    /**
     * Whether {@code query} is the count query Spring Data runs for a page's total, which has no
     * use for an order.
     */
    private static boolean isCountQuery(CriteriaQuery<?> query) {
        return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
    }

    /**
     * The same page as {@code pageable}, without its sort.
     */
    private static Pageable withoutSort(Pageable pageable) {
        return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : Pageable.unpaged();
    }
}
