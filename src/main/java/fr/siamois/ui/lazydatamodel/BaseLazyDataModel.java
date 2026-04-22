package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Getter
@Setter
public abstract class BaseLazyDataModel<T> extends LazyDataModel<T> implements LazyModel {

    // Page, Sort and Filter state
    protected int first = 0;
    protected int pageSizeState = 10;
    protected transient Set<SortMeta> sortBy = new HashSet<>();

    // Cache
    protected transient Map<String, FilterMeta> cachedFilterBy = new HashMap<>() ;
    protected int cachedFirst ;
    protected int cachedPageSize ;
    protected transient Map<String, SortMeta> cachedSortBy = new HashMap<>() ;
    protected transient List<T> queryResult ; // cache for the result of the query
    protected int cachedRowCount;

    protected abstract String getDefaultSortField();

    protected abstract Page<T> loadData(FilterDTO filter, Pageable pageable);

    // Filters
    private String globalFilter;
    // Filters
    protected transient List<ConceptLabel> selectedTypes = new ArrayList<>();
    protected transient List<ConceptLabel> selectedAuthors = new ArrayList<>();
    protected String nameFilter;
    // selection
    protected transient List<T> selectedUnits ;

    // Base implementation returns empty; override in child class
    protected Map<String, String> getFieldMapping() {
        return Collections.emptyMap();
    }

    protected Sort buildSort(Map<String, SortMeta> sortBy, String tieBreaker) {

        if (sortBy == null || sortBy.isEmpty()) {
            return Sort.unsorted();
        }

        Map<String, String> fieldMapping = getFieldMapping();
        List<Sort.Order> orders = new ArrayList<>();

        for (Map.Entry<String, SortMeta> entry : sortBy.entrySet()) {
            String field = fieldMapping.getOrDefault(entry.getKey(), entry.getKey());
            SortMeta meta = entry.getValue();
            Sort.Order order = new Sort.Order(
                    meta.getOrder() == SortOrder.ASCENDING ? Sort.Direction.ASC : Sort.Direction.DESC,
                    field
            );
            orders.add(order);
        }

        // Add tie breaker to make it deterministic
        orders.add(new Sort.Order(Sort.Direction.ASC, tieBreaker));

        return Sort.by(orders);
    }

    public static Map<String, FilterMeta> deepCopyFilterMetaMap(Map<String, FilterMeta> originalMap) {
        Map<String, FilterMeta> copiedMap = new HashMap<>();
        for (Map.Entry<String, FilterMeta> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            FilterMeta originalMeta = entry.getValue();

            FilterMeta copiedMeta = FilterMeta.builder()
                    .field(originalMeta.getField())
                    .filterValue(originalMeta.getFilterValue())
                    .matchMode(originalMeta.getMatchMode())
                    .build();

            copiedMap.put(key, copiedMeta);
        }
        return copiedMap;
    }

    protected void updateCache(Page<T> result, Map<String, FilterMeta> filterBy, Map<String, SortMeta> sortBy, int first, int pageSize) {
        // Update cache
        this.queryResult = result.getContent();
        this.cachedFilterBy = BaseLazyDataModel.deepCopyFilterMetaMap(filterBy);
        this.cachedSortBy = BaseLazyDataModel.deepCopySortMetaMap(sortBy);
        this.cachedFirst = first;
        this.cachedPageSize = pageSize;
        this.cachedRowCount = (int) result.getTotalElements();
    }

    public static Map<String, SortMeta> deepCopySortMetaMap(Map<String, SortMeta> originalMap) {
        Map<String, SortMeta> copiedMap = new HashMap<>();
        for (Map.Entry<String, SortMeta> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            SortMeta originalMeta = entry.getValue();

            SortMeta copiedMeta = SortMeta.builder()
                    .field(originalMeta.getField())
                    .order(originalMeta.getOrder())
                    .build();

            copiedMap.put(key, copiedMeta);
        }
        return copiedMap;
    }

    // Deep comparison method for sort criteria
    public boolean isSortCriteriaSame(Map<String, SortMeta> existingSorts, Map<String, SortMeta> newSorts) {



        if (existingSorts == null && newSorts == null) return true;
        if (existingSorts == null || newSorts == null) return false;

        if (existingSorts.size() != newSorts.size()) return false;



        for (Map.Entry<String, SortMeta> existingEntry : existingSorts.entrySet()) {
            SortMeta newSortMeta = newSorts.get(existingEntry.getKey());
            if (newSortMeta == null) return false;

            // Compare filter metadata details
            if (!areSortMetaOrderEqual(existingEntry.getValue(), newSortMeta)) {
                return false;
            }
        }
        return true;
    }

    // Deep comparison method for filter criteria
    public boolean isFilterCriteriaSame(Map<String, FilterMeta> existingFilters, Map<String, FilterMeta> newFilters) {
        if (existingFilters == null && newFilters == null) return true;
        if (existingFilters == null || newFilters == null) return false;

        if (existingFilters.size() != newFilters.size()) return false;

        for (Map.Entry<String, FilterMeta> existingEntry : existingFilters.entrySet()) {
            FilterMeta newFilterMeta = newFilters.get(existingEntry.getKey());
            if (newFilterMeta == null) return false;

            // Compare filter metadata details
            if (!areFilterMetaValueEqual(existingEntry.getValue(), newFilterMeta)) {
                return false;
            }
        }
        return true;
    }

    // Helper method to compare SortMeta objects
    private boolean areSortMetaOrderEqual(SortMeta sort1, SortMeta sort2) {
        return (sort1.getOrder() == sort2.getOrder());
    }

    // Helper method to compare FilterMeta objects
    private boolean areFilterMetaValueEqual(FilterMeta filter1, FilterMeta filter2) {
        Object value1 = filter1.getFilterValue();
        Object value2 = filter2.getFilterValue();

        if (value1 instanceof Collection<?> col1 && value2 instanceof Collection<?> col2) {
            // Compare as sets to ignore order and duplicates
            return new HashSet<>(col1).equals(new HashSet<>(col2));
        }

        // Fallback to standard equality
        return Objects.equals(value1, value2);
    }

    protected abstract int countWithFilter(FilterDTO filters);

    @Override
    public int count(Map<String, FilterMeta> map) {
        FilterDTO filterDTO = new FilterDTO();
        for (Map.Entry<String, FilterMeta> entry : map.entrySet()) {
            if (entry.getKey().equals("globalFilter") && entry.getValue() != null) {
                filterDTO.add(FilterDTO.GLOBAL_FILTER_KEY, entry.getValue().getFilterValue(), FilterDTO.FilterType.CONTAINS);
            } else if (entry.getValue() != null) {
                filterDTO.add(entry.getKey(), entry.getValue().getFilterValue(), FilterDTO.FilterType.CONTAINS);
            }
        }
        return countWithFilter(filterDTO);
    }

    @Override
    @Transactional
    public List<T> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        boolean isSortSame = isSortCriteriaSame(this.cachedSortBy, sortBy);
        boolean isFilterSame = isFilterCriteriaSame(this.cachedFilterBy, filterBy);

        if (this.cachedFirst == first &&
                this.cachedPageSize == pageSize &&
                isSortSame &&
                isFilterSame &&
                this.queryResult != null) {
            setRowCount(this.cachedRowCount);
            return this.queryResult;
        }

        this.first = first;
        this.pageSizeState = pageSize;
        int pageNumber = first / pageSize;



        // Filter extraction
        FilterDTO filterDTO = new FilterDTO();
        SortDTO sortDTO = new SortDTO();

        prepareFilterDTO(filterBy, filterDTO);
        prepareSortDTO(sortBy, sortDTO);

        Pageable pageable = PageRequest.of(pageNumber, pageSizeState, buildSort(sortDTO));

        Page<T> result = loadData(filterDTO, pageable);
        setRowCount((int) result.getTotalElements());
        updateCache(result, filterBy, sortBy, first, pageSize);

        return result.getContent();
    }

    @NonNull
    private Sort buildSort(SortDTO sortDTO) {
        for (String attribute : sortDTO.getAttributeNames()) {
            switch (sortDTO.orderOf(attribute)) {
                case ASC -> {
                    return Sort.by(Sort.Direction.ASC, attribute);
                }
                case DESC -> {
                    return Sort.by(Sort.Direction.DESC, attribute);
                }
            }
        }

        return Sort.unsorted();
    }

    /**
     * This method is called by the {@link BaseLazyDataModel#load(int, int, Map, Map)} method.
     * It allows you to transfer the elements in the sortBy provided by PrimeFaces to a SortDTO
     * @param sortBy The sort list provided by PrimeFaces
     * @param sortDTO The domain DTO sort
     */
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * This method is called by the {@link BaseLazyDataModel#load(int, int, Map, Map)} method.
     * It allows you to transfer the elements in the filterBy provided by PrimeFaces to a FilterDTO
     * @param filterBy The filter list provided by PrimeFaces
     * @param filterDTO The domain DTO filters
     */
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getFirstIndexOnPage() {
        return first + 1; // Adding 1 because indexes are zero-based
    }

    public int getLastIndexOnPage() {
        int last = first + pageSizeState;
        int total = this.getRowCount();
        return Math.min(last, total); // Ensure it doesn’t exceed total records
    }

    public void addRowToModel(T newUnit) {
        // Create modifiable copy
        List<T> modifiableCopy = new ArrayList<>();

        if(getWrappedData()!=null) {
            modifiableCopy  = new ArrayList<>(getWrappedData());
        }

        // Insert new record at the top
        modifiableCopy.add(0, newUnit);

        // Adjust row count
        setRowCount(modifiableCopy.size());
        setCachedRowCount(modifiableCopy.size());
        // Update data
        setWrappedData(modifiableCopy);
        setQueryResult(modifiableCopy);

        // Optional: remove last item if too many (pagination bound)
        if (modifiableCopy.size() > getPageSizeState()) {
            modifiableCopy.remove(modifiableCopy.size() - 1);
        }
    }
}
