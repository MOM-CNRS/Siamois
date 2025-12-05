package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Décrit la configuration globale d'un tableau :
 * simplement une liste de TableColumn.
 */
@Data
@AllArgsConstructor
public class TableDefinition {

    private List<TableColumn> columns;

    public TableDefinition() {
        this.columns = new ArrayList<>();
    }

    public void addColumn(TableColumn column) {
        if (column != null) {
            columns.add(column);
        }
    }

    public List<TableColumn> getVisibleColumns() {
        return columns.stream()
                .filter(TableColumn::isVisible)
                .collect(Collectors.toList());
    }

    /**
     * Retourne tous les CustomField associés aux colonnes qui en ont un.
     */
    public List<CustomField> getAllFields() {
        return columns.stream()
                .map(TableColumn::getField)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Optional<TableColumn> findColumnById(String id) {
        return columns.stream()
                .filter(c -> Objects.equals(c.getId(), id))
                .findFirst();
    }

    public Optional<TableColumn> findColumnByField(CustomField field) {
        return columns.stream()
                .filter(c -> Objects.equals(c.getField(), field))
                .findFirst();
    }
}
