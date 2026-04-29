package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BaseLazyDataModel.class);

    // Page, Sort and Filter state
    protected int first = 0;
    protected int pageSizeState = 10;
    protected transient Set<SortMeta> sortBy = new HashSet<>();

    // Cache
    protected transient Map<String, FilterMeta> cachedFilterBy = new HashMap<>();
    protected int cachedFirst;
    protected int cachedPageSize;
    protected transient Map<String, SortMeta> cachedSortBy = new HashMap<>();
    protected transient List<T> queryResult;
    protected int cachedRowCount;

    @Getter
    @Setter
    protected TreeNode<T> lazyRoot;

    @Setter
    protected boolean rootOnly;

    /**
     * Mirrors the table-toolbar toggle. When {@code false}, {@link #load(int, int, Map, Map)} drops
     * any column FilterMeta the dataTable still carries from a previous render
     * (PrimeFaces preserves them across the toggle, only their inputs are CSS-
     * hidden). The {@link fr.siamois.ui.custom.LazyTreeTable} also honors this flag, but the plain
     * dataTable has no equivalent layer and would otherwise keep applying
     * stale filters.
     */
    @Setter
    protected boolean columnFilteringEnabled = true;

    protected transient Set<Long> ancestorClosure;
    protected transient Set<Long> matchIds;

    protected SortDTO getDefaultSortDTO() {
        return new SortDTO();
    }

    protected abstract Page<T> loadData(FilterDTO filter, Pageable pageable);

    // Filters & Selection
    private String globalFilter;
    protected transient List<ConceptLabel> selectedTypes = new ArrayList<>();
    protected transient List<ConceptLabel> selectedAuthors = new ArrayList<>();
    protected String nameFilter;
    protected transient List<T> selectedUnits;

    protected Map<String, String> getFieldMapping() {
        return Collections.emptyMap();
    }

    // --- UTILITY METHODS FOR CACHE & CLONING ---

    public static Map<String, FilterMeta> deepCopyFilterMetaMap(Map<String, FilterMeta> originalMap) {
        Map<String, FilterMeta> copiedMap = new HashMap<>();
        for (Map.Entry<String, FilterMeta> entry : originalMap.entrySet()) {
            FilterMeta originalMeta = entry.getValue();
            FilterMeta copiedMeta = FilterMeta.builder()
                    .field(originalMeta.getField())
                    .filterValue(originalMeta.getFilterValue())
                    .matchMode(originalMeta.getMatchMode())
                    .build();
            copiedMap.put(entry.getKey(), copiedMeta);
        }
        return copiedMap;
    }

    public static Map<String, SortMeta> deepCopySortMetaMap(Map<String, SortMeta> originalMap) {
        Map<String, SortMeta> copiedMap = new HashMap<>();
        for (Map.Entry<String, SortMeta> entry : originalMap.entrySet()) {
            SortMeta originalMeta = entry.getValue();
            SortMeta copiedMeta = SortMeta.builder()
                    .field(originalMeta.getField())
                    .order(originalMeta.getOrder())
                    .build();
            copiedMap.put(entry.getKey(), copiedMeta);
        }
        return copiedMap;
    }

    protected void updateCache(Page<T> result, Map<String, FilterMeta> filterBy, Map<String, SortMeta> sortBy, int first, int pageSize) {
        this.queryResult = result.getContent();
        this.cachedFilterBy = BaseLazyDataModel.deepCopyFilterMetaMap(filterBy);
        this.cachedSortBy = BaseLazyDataModel.deepCopySortMetaMap(sortBy);
        this.cachedFirst = first;
        this.cachedPageSize = pageSize;
        this.cachedRowCount = (int) result.getTotalElements();
    }

    public boolean isSortCriteriaSame(Map<String, SortMeta> existingSorts, Map<String, SortMeta> newSorts) {
        if (existingSorts == null && newSorts == null) return true;
        if (existingSorts == null || newSorts == null) return false;
        if (existingSorts.size() != newSorts.size()) return false;

        for (Map.Entry<String, SortMeta> existingEntry : existingSorts.entrySet()) {
            SortMeta newSortMeta = newSorts.get(existingEntry.getKey());
            if (newSortMeta == null || existingEntry.getValue().getOrder() != newSortMeta.getOrder()) {
                return false;
            }
        }
        return true;
    }

    public boolean isFilterCriteriaSame(Map<String, FilterMeta> existingFilters, Map<String, FilterMeta> newFilters) {
        if (existingFilters == null && newFilters == null) return true;
        if (existingFilters == null || newFilters == null) return false;
        if (existingFilters.size() != newFilters.size()) return false;

        for (Map.Entry<String, FilterMeta> existingEntry : existingFilters.entrySet()) {
            FilterMeta newFilterMeta = newFilters.get(existingEntry.getKey());
            if (newFilterMeta == null) return false;

            Object value1 = existingEntry.getValue().getFilterValue();
            Object value2 = newFilterMeta.getFilterValue();

            if (value1 instanceof Collection<?> col1 && value2 instanceof Collection<?> col2) {
                if (!new HashSet<>(col1).equals(new HashSet<>(col2))) return false;
            } else if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    public void resetCache() {
        this.queryResult = null;
    }

    // --- PREPARATION AND CLEANING (Moved from LazyTreeTable) ---

    protected Map<String, SortMeta> prepareSorts(Map<String, SortMeta> rawSortMap) {
        Map<String, SortMeta> activeSorts = new HashMap<>();
        FacesContext context = FacesContext.getCurrentInstance();

        if (rawSortMap != null) {
            for (Map.Entry<String, SortMeta> entry : rawSortMap.entrySet()) {
                if (!entry.getValue().getOrder().isUnsorted()) {
                    String resolvedKey = (context != null && entry.getValue().getSortBy() != null)
                            ? (String) entry.getValue().getSortBy().getValue(context.getELContext())
                            : entry.getKey();
                    activeSorts.put(resolvedKey, entry.getValue());
                }
            }
        }
        return activeSorts;
    }

    protected Map<String, FilterMeta> prepareFilters(Map<String, FilterMeta> rawFilterMap) {
        Map<String, FilterMeta> activeFilters = new HashMap<>();
        FacesContext context = FacesContext.getCurrentInstance();
        String globalVal = null;

        if (rawFilterMap != null) {
            for (Map.Entry<String, FilterMeta> entry : rawFilterMap.entrySet()) {
                if ("globalFilter".equals(entry.getKey())) {
                    globalVal = (String) entry.getValue().getFilterValue();
                    continue;
                }

                FilterMeta meta = entry.getValue();
                Object val = meta.getFilterValue();
                boolean keep = false;

                if (val instanceof String strVal) {
                    keep = !strVal.trim().isEmpty();
                } else if (val instanceof Collection<?> col) {
                    keep = !col.isEmpty();
                } else {
                    keep = val != null;
                }

                if (keep) {
                    String resolvedKey = (context != null && meta.getFilterBy() != null)
                            ? meta.getFilterBy().getValue(context.getELContext())
                            : entry.getKey();
                    activeFilters.put(resolvedKey, normalizeFilterMeta(meta));
                }
            }
        }

        // Recherche du filtre global dans les requêtes HTTP si manquant de la map
        if ((globalVal == null || globalVal.trim().isEmpty()) && context != null) {
            Map<String, String> params = context.getExternalContext().getRequestParameterMap();
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (param.getKey().endsWith(":globalFilter")) {
                    globalVal = param.getValue();
                    break;
                }
            }
        }

        if (globalVal != null && globalVal.trim().length() >= 3) {
            FilterMeta globalMeta = FilterMeta.builder()
                    .field("globalFilter")
                    .filterValue(globalVal.trim())
                    .matchMode(MatchMode.GLOBAL)
                    .build();
            activeFilters.put("globalFilter", globalMeta);
        }

        return activeFilters;
    }

    protected FilterMeta normalizeFilterMeta(FilterMeta meta) {
        Object val = meta.getFilterValue();
        if (!(val instanceof Collection<?> col) || col.isEmpty()) {
            return meta;
        }

        Object firstItem = col.iterator().next();
        List<Long> ids = null;

        if (firstItem instanceof ConceptAutocompleteDTO) {
            ids = new ArrayList<>(col.size());
            for (Object o : col) {
                ConceptDTO concept = ((ConceptAutocompleteDTO) o).concept();
                if (concept != null) ids.add(concept.getId());
            }
        } else if (firstItem instanceof AbstractEntityDTO) {
            ids = new ArrayList<>(col.size());
            for (Object o : col) {
                Long id = ((AbstractEntityDTO) o).getId();
                if (id != null) ids.add(id);
            }
        }

        if (ids == null) return meta;

        return FilterMeta.builder()
                .field(meta.getField())
                .filterBy(meta.getFilterBy())
                .filterValue(ids)
                .matchMode(meta.getMatchMode())
                .build();
    }

    // --- CORE LOAD AND COUNT METHODS ---

    protected abstract int countWithFilter(FilterDTO filters);

    @Override
    public int count(Map<String, FilterMeta> map) {
        FilterDTO filterDTO = new FilterDTO(rootOnly);
        if (!columnFilteringEnabled) {
            map = new HashMap<>();
        }
        Map<String, FilterMeta> activeFilters = prepareFilters(map);

        for (Map.Entry<String, FilterMeta> entry : activeFilters.entrySet()) {
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
        // 1. Nettoyage et préparation des maps PrimeFaces brutes
        Map<String, SortMeta> activeSorts = prepareSorts(sortBy);
        if (!columnFilteringEnabled) {
            filterBy = new HashMap<>();
        }
        Map<String, FilterMeta> activeFilters = prepareFilters(filterBy);

        // 2. Évaluation du cache avec les maps propres
        boolean isSortSame = isSortCriteriaSame(this.cachedSortBy, activeSorts);
        boolean isFilterSame = isFilterCriteriaSame(this.cachedFilterBy, activeFilters);

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

        // 3. Traduction en DTO métier
        FilterDTO filterDTO = new FilterDTO(rootOnly);
        SortDTO sortDTO = new SortDTO();

        prepareFilterDTO(activeFilters, filterDTO);
        prepareSortDTO(activeSorts, sortDTO);

        Pageable pageable = PageRequest.of(pageNumber, pageSizeState, buildSort(sortDTO));

        // 4. Exécution de la requête
        Page<T> result = loadData(filterDTO, pageable);
        captureClosureSnapshot(filterDTO);
        setRowCount((int) result.getTotalElements());
        updateCache(result, activeFilters, activeSorts, first, pageSize);

        return result.getContent();
    }

    @SuppressWarnings("unchecked")
    private void captureClosureSnapshot(FilterDTO filterDTO) {
        Collection<Long> closure = filterDTO.getAncestorClosure();
        if (closure instanceof Set<?> set) {
            this.ancestorClosure = (Set<Long>) set;
        } else if (closure != null) {
            this.ancestorClosure = new HashSet<>(closure);
        } else {
            this.ancestorClosure = null;
        }

        this.matchIds  = filterDTO.getMatchIds();
    }

    @NonNull
    private Sort buildSort(SortDTO sortDTO) {
        if (sortDTO.isEmpty()) {
            sortDTO = getDefaultSortDTO();
        }

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
     * This method should take the sortBy provided by PrimeFaces and add all relevant sorts to the sortDTO
     * @param sortBy The sorts provided by PrimeFaces
     * @param sortDTO The domain sort DTO
     */
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        throw new UnsupportedOperationException("prepareSortDTO not implemented yet in " + this.getClass().getSimpleName());
    }

    /**
     * This method should take the filterBy provided by PrimeFaces and add all relevant sorts to the filterDTO
     * @param filterBy The filters provided by PrimeFaces
     * @param filterDTO The domain filter DTO
     */
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        throw new UnsupportedOperationException("prepareFilterDTO not implemented yet in " + this.getClass().getSimpleName());
    }

    public int getFirstIndexOnPage() {
        return first + 1;
    }

    public int getLastIndexOnPage() {
        int last = first + pageSizeState;
        int total = this.getRowCount();
        return Math.min(last, total);
    }

    public void addRowToModel(T newUnit) {
        // Increment the total against the previously known total — using the
        // wrappedData size would replace the total with the page size and break
        // the paginator after duplications/bulk creates.
        int newTotal = getRowCount() + 1;
        setRowCount(newTotal);
        setCachedRowCount(newTotal);

        List<T> modifiableCopy = new ArrayList<>();
        if (getWrappedData() != null) {
            modifiableCopy = new ArrayList<>(getWrappedData());
        }
        modifiableCopy.add(0, newUnit);
        setWrappedData(modifiableCopy);
        setQueryResult(modifiableCopy);

        if (modifiableCopy.size() > getPageSizeState()) {
            modifiableCopy.remove(modifiableCopy.size() - 1);
        }
    }
}