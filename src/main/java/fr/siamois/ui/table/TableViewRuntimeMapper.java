package fr.siamois.ui.table;

import fr.siamois.dto.view.ColumnState;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class TableViewRuntimeMapper {

    public TableViewState extract(EntityTableViewModel<?, ?> model) {

        TableViewState state = new TableViewState();

        state.setTreeMode(model.isTreeMode());

        // columns
        state.setColumns(
                model.getColumns().stream()
                        .map(c -> {
                            ColumnState cs = new ColumnState();
                            cs.setColumnId(c.getId());
                            cs.setVisible(c.isVisible());
                            return cs;
                        })
                        .collect(Collectors.toList())
        );

        // sorting
        //state.setSorting(model.getLazyDataModel().getSortStates());

        // filters
        //state.setFilters(model.getLazyDataModel().getFilterStates());

        return state;
    }

    public void apply(EntityTableViewModel<?,?> model, TableViewState state) {

        if (state == null) return;

        model.setTreeMode(state.isTreeMode());
        model.setColumnFilteringEnabled(state.isColumnFilteringEnabled());

        // columns
        if (state.getColumns() != null) {
            var byId = state.getColumns().stream()
                    .collect(Collectors.toMap(ColumnState::getColumnId, c -> c));

            model.getColumns().forEach(col -> {
                ColumnState cs = byId.get(col.getId());
                if (cs == null) {col.setVisible(false); return;}

                col.setVisible(cs.isVisible());
            });
        }

        // filters
        if (state.getFilters() != null) {
            model.applyFilterStates(state.getFilters());
        }

        // sorting
        if (state.getSorting() != null) {
            //model.getLazyDataModel().setSortStates(state.getSorting());
        }

    }
}
