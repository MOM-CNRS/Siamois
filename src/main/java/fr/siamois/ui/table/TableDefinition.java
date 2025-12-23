package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public List<FormFieldColumn> getFieldColumns() {
        return columns.stream()
                .filter(c -> c instanceof FormFieldColumn)
                .map(c -> (FormFieldColumn) c)
                .collect(Collectors.toList());
    }

    public List<CustomField> getAllFields() {
        return getFieldColumns().stream()
                .map(FormFieldColumn::getField)
                .collect(Collectors.toList());
    }

    public Optional<TableColumn> findColumnById(String id) {
        return columns.stream()
                .filter(c -> Objects.equals(c.getId(), id))
                .findFirst();
    }

    public Optional<TableColumn> findColumnByField(CustomField field) {
        return columns.stream()
                .filter(c -> c instanceof FormFieldColumn f
                        && Objects.equals(f.getField(), field))
                .findFirst();
    }
}
