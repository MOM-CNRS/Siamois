package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a {@link FormLayout} into the panels/rows/columns the clients render: a group is a panel,
 * its active fields one row (the grid wraps them by width). What belongs to a system field itself
 * comes from the catalog: {@code readOnly}, and the {@code hidden} fields (identifiers, owning
 * project), which every form carries — the client reads them from the layout — and which are added
 * to the first section whatever the layout says. A group with no active field is not a section.
 */
public final class FormLayoutComposer {

    private FormLayoutComposer() {
        throw new UnsupportedOperationException();
    }

    /** The columns a group shows: its active fields, once each, less the hidden system ones. */
    private static List<CustomColUiDto> visibleColumns(FormLayout.Group group, List<SystemFieldSpec> specs) {
        List<CustomColUiDto> visible = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (FormLayout.Item item : group.items()) {
            if (!item.active() || !seen.add(item.field().getId())) continue;
            SystemFieldSpec spec = specs.stream()
                    .filter(s -> s.field().getId().equals(item.field().getId()))
                    .findFirst().orElse(null);
            if (spec != null && spec.hidden()) continue;
            visible.add(new CustomColUiDto.Builder()
                    .field(item.field())
                    .width(item.width().toColumnWidth())
                    .readOnly(spec != null && spec.readOnly())
                    .isRequired(item.mandatory())
                    .rules(item.rules())
                    .build());
        }
        return visible;
    }

    public static FormUiDto compose(FormLayout layout, ConfigurableTable table) {
        List<SystemFieldSpec> specs = SystemFieldCatalog.specsOf(table);
        FormUiDto.Builder form = new FormUiDto.Builder();
        boolean hiddenPending = true;
        for (FormLayout.Group group : layout.groups()) {
            List<CustomColUiDto> visible = visibleColumns(group, specs);
            // A group with nothing to show is not a section of the form.
            if (visible.isEmpty()) continue;
            CustomRowUiDto.Builder row = new CustomRowUiDto.Builder();
            if (hiddenPending) {
                specs.stream().filter(SystemFieldSpec::hidden).forEach(spec -> row.addColumn(hiddenColumn(spec)));
                hiddenPending = false;
            }
            visible.forEach(row::addColumn);
            boolean system = group.items().stream().anyMatch(i -> Boolean.TRUE.equals(i.field().getIsSystemField()));
            form.addPanel(new CustomFormPanelUiDto.Builder()
                    .name(group.label())
                    .isSystemPanel(system)
                    .addRow(row.build())
                    .build());
        }
        if (hiddenPending) {
            // The client reads the identifiers from the layout, so they are carried even when no field shows.
            CustomRowUiDto.Builder row = new CustomRowUiDto.Builder();
            specs.stream().filter(SystemFieldSpec::hidden).forEach(spec -> row.addColumn(hiddenColumn(spec)));
            form.addPanel(new CustomFormPanelUiDto.Builder().isSystemPanel(true).addRow(row.build()).build());
        }
        return form.build();
    }

    private static CustomColUiDto hiddenColumn(SystemFieldSpec spec) {
        return new CustomColUiDto.Builder()
                .field(spec.field())
                .width(fr.siamois.domain.models.form.config.FieldWidth.QUARTER.toColumnWidth())
                .hidden(true)
                .readOnly(spec.readOnly())
                .build();
    }

    /** The groups' columns flattened, for callers that only need the fields. */
    public static List<CustomColUiDto> columns(FormUiDto form) {
        List<CustomColUiDto> out = new ArrayList<>();
        form.getLayout().forEach(p -> p.getRows().forEach(r -> out.addAll(r.getColumns())));
        return out;
    }
}
