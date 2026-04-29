package fr.siamois.ui.lazydatamodel;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.faces.context.FacesContext;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.MatchMode;
import org.primefaces.model.SortMeta;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.*;

public class FilterAndSortUtils {

    public static final String GLOBAL_FILTER = "globalFilter";

    private FilterAndSortUtils() {
        throw new UnsupportedOperationException("FilterAndSortUtils can't be instantiated");
    }

    static FilterMeta normalizeFilterMeta(@NonNull FilterMeta meta) {
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

    static Map<String, FilterMeta> prepareLoadFilters(@Nullable Map<String, FilterMeta> rawFilterMap) {
        Map<String, FilterMeta> activeFilters = new HashMap<>();
        FacesContext context = FacesContext.getCurrentInstance();
        String globalVal = null;

        if (rawFilterMap == null || rawFilterMap.isEmpty()) {
            return activeFilters;
        }

        for (Map.Entry<String, FilterMeta> entry : rawFilterMap.entrySet()) {
            if (GLOBAL_FILTER.equals(entry.getKey())) {
                globalVal = (String) entry.getValue().getFilterValue();
                continue;
            }

            FilterMeta meta = entry.getValue();
            Object val = meta.getFilterValue();

            if (shouldKeepFilter(val)) {
                String resolvedKey = (context != null && meta.getFilterBy() != null)
                        ? meta.getFilterBy().getValue(context.getELContext())
                        : entry.getKey();
                activeFilters.put(resolvedKey, normalizeFilterMeta(meta));
            }
        }

        globalVal = searchMissingFiltersInHttpRequest(globalVal, context);

        if (globalVal != null && globalVal.trim().length() >= 3) {
            FilterMeta globalMeta = FilterMeta.builder()
                    .field(GLOBAL_FILTER)
                    .filterValue(globalVal.trim())
                    .matchMode(MatchMode.GLOBAL)
                    .build();
            activeFilters.put(GLOBAL_FILTER, globalMeta);
        }

        return activeFilters;
    }

    private static @Nullable String searchMissingFiltersInHttpRequest(String globalVal, FacesContext context) {
        if (globalVal != null && !globalVal.trim().isEmpty() || context == null) {
            return globalVal;
        }

        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (param.getKey().endsWith(":globalFilter")) {
                globalVal = param.getValue();
                break;
            }
        }
        return globalVal;
    }

    private static boolean shouldKeepFilter(Object val) {
        boolean keep;
        if (val instanceof String strVal) {
            keep = !strVal.trim().isEmpty();
        } else if (val instanceof Collection<?> col) {
            keep = !col.isEmpty();
        } else {
            keep = val != null;
        }
        return keep;
    }

    static Map<String, SortMeta> prepareSorts(@Nullable Map<String, SortMeta> rawSortMap) {
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

}
