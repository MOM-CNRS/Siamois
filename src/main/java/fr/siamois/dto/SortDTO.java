package fr.siamois.dto;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SortDTO {

    public boolean isEmpty() {
        return sorts.isEmpty();
    }

    public enum SortOrder {
        ASC,
        DESC
    }

    private final Map<String, SortOrder> sorts;

    public SortDTO() {
        this.sorts = new HashMap<>();
    }

    public void add(String attributeName, SortOrder sortOrder) {
        if (sorts.containsKey(attributeName)) {
            throw new InvalidParameterException("Attribute name already exists");
        }
        sorts.put(attributeName, sortOrder);
    }

    public void add(String attributeName, org.primefaces.model.SortOrder sortOrder) {
        if (sortOrder == org.primefaces.model.SortOrder.ASCENDING) {
            add(attributeName, SortOrder.ASC);
        } else {
            add(attributeName, SortOrder.DESC);
        }
    }

    public List<String> getAttributeNames() {
        return sorts.keySet().stream().toList();
    }

    public SortOrder orderOf(String attributeName) {
        return sorts.get(attributeName);
    }
}
