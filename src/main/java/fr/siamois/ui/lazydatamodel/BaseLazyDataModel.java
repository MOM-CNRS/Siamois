package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Getter
@Setter
public abstract class BaseLazyDataModel<T> extends LazyDataModel<T> implements LazyModel {



    @Override
    public int count(Map<String, FilterMeta> map) {
        return 0;
    }

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

    protected abstract Page<T> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable);



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

        Pageable pageable = PageRequest.of(pageNumber, pageSizeState, buildSort(sortBy, getDefaultSortField()));

        // Filter extraction
        String localNameFilter = null;
        Long[] categoryIds = null;
        Long[] personIds = null;
        String localGlobalFilter = null;

        if (filterBy != null) {
            FilterMeta nameMeta = filterBy.get("name");
            if (nameMeta != null && nameMeta.getFilterValue() != null) {
                localNameFilter = nameMeta.getFilterValue().toString();
            }

            FilterMeta categoryMeta = filterBy.get("category");
            if (categoryMeta != null && categoryMeta.getFilterValue() != null) {
                @SuppressWarnings("unchecked")
                List<LocalizedConceptData> selectedCategoryLabels = (List<LocalizedConceptData>) categoryMeta.getFilterValue();
                List<Concept> selectedCategories = selectedCategoryLabels.stream()
                        .map(LocalizedConceptData::getConcept)
                        .toList();
                categoryIds = selectedCategories.stream()
                        .filter(Objects::nonNull)
                        .map(Concept::getId)
                        .filter(Objects::nonNull)
                        .toArray(Long[]::new);
            }

            FilterMeta personMeta = filterBy.get("author");
            if (personMeta != null && personMeta.getFilterValue() != null) {
                @SuppressWarnings("unchecked")
                List<Person> selectedPerson = (List<Person>) personMeta.getFilterValue();
                personIds = selectedPerson.stream()
                        .filter(Objects::nonNull)
                        .map(Person::getId)
                        .filter(Objects::nonNull)
                        .toArray(Long[]::new);
            }

            FilterMeta globalMeta = filterBy.get("globalFilter");
            if (globalMeta != null && globalMeta.getFilterValue() != null) {
                localGlobalFilter = globalMeta.getFilterValue().toString();
            }
        }

        Page<T> result = loadData(localNameFilter, categoryIds, personIds, localGlobalFilter, pageable);
        setRowCount((int) result.getTotalElements());
        updateCache(result, filterBy, sortBy, first, pageSize);
        this.sortBy = new HashSet<>(sortBy.values());

        return result.getContent();
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

    public void handleRowSelect(SelectEvent<T> event) {
        FacesMessage msg = new FacesMessage("Row Selected");
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }

    public void handleRowUnselect(UnselectEvent<T> event) {
        FacesMessage msg = new FacesMessage("Row Unselected");
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }
}
