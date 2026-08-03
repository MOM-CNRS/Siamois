package fr.siamois.ui.form.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomRowUiDto implements Serializable {

    private List<CustomColUiDto> columns;

    public static class Builder {

        private final CustomRowUiDto row = new CustomRowUiDto();
        private final List<CustomColUiDto> columns = new ArrayList<>();

        public Builder addColumn(CustomColUiDto col) {
            this.columns.add(col);
            return this;
        }

        public Builder addColumns(CustomColUiDto... cols) {
            this.columns.addAll(List.of(cols));
            return this;
        }

        public Builder addColumns(List<CustomColUiDto> cols) {
            this.columns.addAll(cols);
            return this;
        }

        public CustomRowUiDto build() {
            row.setColumns(columns);
            return row;
        }
    }

}
