package fr.siamois.ui.table;

import jakarta.faces.component.ContextCallback;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.context.FacesContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the concrete client ids of a single row's cells for a targeted PrimeFaces AJAX
 * {@code update}, without going through PrimeFaces' {@code @row(n)} search-expression keyword.
 * <p>
 * {@code @row(n)} validates the requested index against the live {@code UIData.getRowCount()}
 * before resolving anything, and throws a {@code FacesException} whenever that count is out of
 * sync with the table's actual cached data (which happens in this app: lazy models are only
 * refreshed when the table itself renders/paginates, so an unrelated AJAX request — e.g. saving
 * an entity from a preview panel — can see a stale count). The caller has already confirmed the
 * row's presence via {@link fr.siamois.ui.table.viewmodel.EntityTableViewModel#getRowIndexInCurrentPage},
 * so that check is not repeated here.
 * <p>
 * The table is located via {@link UIComponent#invokeOnComponent} rather than
 * {@link UIComponent#findComponent}: the table's {@code value} is bound through a composite
 * component ({@code #{cc.attrs.tableModel}} in {@code entityTable.xhtml}), and only
 * {@code invokeOnComponent}'s tree walk pushes each ancestor's EL context (including composite
 * ancestors) as it descends. {@code findComponent} doesn't, so {@code #{cc}} resolves to
 * nothing and {@code getValue()} returns {@code null}.
 * <p>
 * Rather than guessing how many Facelets structural wrappers (e.g. {@code <ui:fragment>}, which
 * emit no DOM element of their own) sit between a column and its actual rendered content — which
 * differs per column type — every updatable cell root in {@code entityDataTable.xhtml},
 * {@code entityTreeTable.xhtml} and their included templates carries the marker CSS class
 * {@value #ROW_UPDATE_MARKER_CLASS}. This resolver walks each column's subtree and collects the
 * client id of the first marked component found on each branch (not descending further once one
 * is matched, since its own re-render covers its descendants).
 */
public final class RowAjaxUpdateResolver {

    /** Marker CSS class identifying a component as a valid row-scoped AJAX update target. */
    public static final String ROW_UPDATE_MARKER_CLASS = "siamois-row-ajax-target";

    private RowAjaxUpdateResolver() {
    }

    /**
     * @return the client ids of every marked cell in row {@code rowIndex} of the table
     * identified by {@code tableClientId}, or an empty list if the table can't be found.
     */
    public static List<String> resolveRowChildIds(String tableClientId, int rowIndex) {
        if (tableClientId == null || rowIndex < 0) {
            return List.of();
        }

        FacesContext context = FacesContext.getCurrentInstance();
        List<String> targets = new ArrayList<>();
        ContextCallback callback = (ctx, target) -> {
            if (!(target instanceof UIData table)) {
                return;
            }
            int previousRowIndex = table.getRowIndex();
            try {
                table.setRowIndex(rowIndex);
                for (UIComponent column : table.getChildren()) {
                    for (UIComponent child : column.getChildren()) {
                        collectMarkedIds(child, ctx, targets);
                    }
                }
            } finally {
                table.setRowIndex(previousRowIndex);
            }
        };

        boolean found = context.getViewRoot().invokeOnComponent(context, tableClientId, callback);
        return found ? targets : List.of();
    }

    private static void collectMarkedIds(UIComponent component, FacesContext context, List<String> targets) {
        if (hasMarkerClass(component)) {
            targets.add(component.getClientId(context));
            return;
        }
        for (UIComponent child : component.getChildren()) {
            collectMarkedIds(child, context, targets);
        }
    }

    private static boolean hasMarkerClass(UIComponent component) {
        Object styleClass = component.getAttributes().get("styleClass");
        if (!(styleClass instanceof String classes)) {
            return false;
        }
        for (String token : classes.split("\\s+")) {
            if (ROW_UPDATE_MARKER_CLASS.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
