package fr.siamois.domain.models.form.config;

import fr.siamois.ui.form.dto.ColumnWidth;

/**
 * The width a field takes in a form, on the 12-column grid: a quarter, a half, three quarters of
 * the row, or the whole row. The narrower breakpoints widen the field (a quarter becomes a half,
 * then the whole row), so a form stays readable on small screens.
 */
public enum FieldWidth {
    QUARTER(new ColumnWidth(12, 6, 3)),
    HALF(new ColumnWidth(12, 6, 6)),
    THREE_QUARTERS(new ColumnWidth(12, 12, 9)),
    FULL(new ColumnWidth(12, 12, 12));

    private final ColumnWidth columnWidth;

    FieldWidth(ColumnWidth columnWidth) {
        this.columnWidth = columnWidth;
    }

    public ColumnWidth toColumnWidth() {
        return columnWidth;
    }
}
