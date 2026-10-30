package fr.siamois.domain.services.form;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldOnTheFly;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields added on the fly to a record's fiche ("Ajouter une mesure"). They are plain custom
 * fields ({@link CustomFieldOnTheFly}, measurement being the first kind) linked to one record of any
 * of the six configurable tables through its {@code onTheFlyFields}, and each knows the panel it was
 * added to. Their answers are stored like any additional field's, in the record's answer sets.
 */
@Service
public class OnTheFlyFieldService {

    @PersistenceContext
    private EntityManager entityManager;

    /** The record's on-the-fly fields, oldest first. */
    @Transactional(readOnly = true)
    public List<CustomFieldOnTheFly> fieldsOf(ConfigurableTable table, Long entityId) {
        return loadFields(table, entityId);
    }

    private List<CustomFieldOnTheFly> loadFields(ConfigurableTable table, Long entityId) {
        if (table == null || entityId == null) return List.of();
        Set<CustomFieldOnTheFly> fields = fieldSet(table, entityId);
        return fields == null ? List.of()
                : fields.stream().sorted(Comparator.comparing(CustomFieldOnTheFly::getId)).toList();
    }

    /**
     * Saves the freshly built field and links it to the record.
     *
     * @param panelName the panel of the fiche the field is added to
     */
    @Transactional
    public CustomFieldOnTheFly add(ConfigurableTable table, Long entityId, String panelName, CustomFieldOnTheFly field) {
        Set<CustomFieldOnTheFly> fields = requireFieldSet(table, entityId);
        field.setPanelName(panelName);
        entityManager.persist(field);
        fields.add(field);
        return field;
    }

    /**
     * The measurements already added to other records of the same table in the project, one per
     * distinct definition (name and unit), whose name matches {@code query}: what a user may reuse
     * instead of defining the same measure again.
     */
    @Transactional(readOnly = true)
    public List<CustomFieldMeasurement> findReusableMeasurements(ConfigurableTable table, Long projectId, String query,
                                                                 int limit) {
        Class<?> entity = entityClass(table);
        if (entity == null || projectId == null) return List.of();
        String like = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        List<CustomFieldOnTheFly> found = entityManager.createQuery(
                        "SELECT DISTINCT f FROM " + entity.getSimpleName() + " e JOIN e.onTheFlyFields f "
                                + "WHERE e.actionUnit.id = :pid AND lower(f.label) LIKE :like ORDER BY f.label",
                        CustomFieldOnTheFly.class)
                .setParameter("pid", projectId).setParameter("like", like).getResultList();
        Map<String, CustomFieldMeasurement> byDefinition = new LinkedHashMap<>();
        for (CustomFieldOnTheFly field : found) {
            if (field instanceof CustomFieldMeasurement m) {
                byDefinition.putIfAbsent(m.getLabel() + "|" + (m.getUnit() == null ? "" : m.getUnit().getSymbol()), m);
            }
        }
        return byDefinition.values().stream().limit(limit).toList();
    }

    /** A copy of the measurement for another record: same name, type, nature and units, none of its answers. */
    public CustomFieldMeasurement copyOf(CustomFieldMeasurement source) {
        CustomFieldMeasurement copy = CustomFieldMeasurement.builder().build();
        copy.setLabel(source.getLabel());
        copy.setHint(source.getHint());
        copy.setIsSystemField(false);
        copy.setConcept(source.getConcept());
        copy.setMeasurementType(source.getMeasurementType());
        copy.setMeasurementNature(source.getMeasurementNature());
        copy.setMinValue(source.getMinValue());
        copy.setMaxValue(source.getMaxValue());
        copy.setUnit(source.getUnit());
        copy.setAllowedUnits(new java.util.LinkedHashSet<>(source.getAllowedUnits()));
        copy.setAuthor(source.getAuthor());
        return copy;
    }

    private static Class<?> entityClass(ConfigurableTable table) {
        return switch (table) {
            case UE -> RecordingUnit.class;
            case MOBILIER -> Specimen.class;
            case PHASE -> Phase.class;
            case CONTENANT -> Container.class;
            case DOCUMENT -> Document.class;
            case STUDY -> Study.class;
            case PROJECT -> null;
        };
    }

    /** Unlinks the field from the record; false when the record has no such field. */
    @Transactional
    public boolean remove(ConfigurableTable table, Long entityId, Long fieldId) {
        Set<CustomFieldOnTheFly> fields = requireFieldSet(table, entityId);
        return fields.removeIf(field -> fieldId.equals(field.getId()));
    }

    /**
     * {@code baseForm} with the record's on-the-fly fields added to their panels (the last panel for
     * a field without one, or whose panel is gone). The form is never changed in place: it may be a
     * shared singleton, so only the panels that get a field are copied.
     */
    @Transactional(readOnly = true)
    public FormUiDto withOnTheFlyFields(FormUiDto baseForm, ConfigurableTable table, Long entityId) {
        List<CustomFieldOnTheFly> fields = loadFields(table, entityId);
        if (fields.isEmpty() || baseForm == null || baseForm.getLayout() == null || baseForm.getLayout().isEmpty()) {
            return baseForm;
        }
        List<CustomFormPanelUiDto> layout = baseForm.getLayout();
        Map<String, List<CustomFieldOnTheFly>> byPanel = fields.stream().collect(
                Collectors.groupingBy(field -> panelOf(layout, field.getPanelName()), LinkedHashMap::new,
                        Collectors.toList()));
        List<CustomFormPanelUiDto> panels = layout.stream()
                .map(panel -> byPanel.containsKey(panel.getName()) ? withFields(panel, byPanel.get(panel.getName())) : panel)
                .collect(Collectors.toCollection(ArrayList::new));
        return new FormUiDto.Builder().addPanels(panels).build();
    }

    /** The panel a field is shown in: the one it was added to, else the fiche's last panel. */
    public static String panelOf(List<CustomFormPanelUiDto> layout, String wanted) {
        boolean known = wanted != null && layout.stream().anyMatch(panel -> wanted.equals(panel.getName()));
        return known ? wanted : layout.get(layout.size() - 1).getName();
    }

    private static CustomFormPanelUiDto withFields(CustomFormPanelUiDto panel, List<CustomFieldOnTheFly> fields) {
        List<CustomRowUiDto> rows = new ArrayList<>();
        if (panel.getRows() != null) {
            panel.getRows().forEach(row -> {
                CustomRowUiDto copy = new CustomRowUiDto();
                copy.setColumns(row.getColumns() == null ? new ArrayList<>() : new ArrayList<>(row.getColumns()));
                rows.add(copy);
            });
        }
        List<CustomColUiDto> columns = fields.stream().map(field -> {
            CustomColUiDto col = new CustomColUiDto.Builder().field(field).width(ColumnWidth.STANDARD)
                    .isRequired(false).build();
            col.setCanBeRemoved(true);
            return col;
        }).toList();
        rows.add(new CustomRowUiDto.Builder().addColumns(columns).build());

        CustomFormPanelUiDto copy = new CustomFormPanelUiDto();
        copy.setName(panel.getName());
        copy.setClassName(panel.getClassName());
        copy.setIsSystemPanel(panel.getIsSystemPanel());
        copy.setCanUserAddFields(panel.getCanUserAddFields());
        copy.setRows(rows);
        return copy;
    }

    private Set<CustomFieldOnTheFly> requireFieldSet(ConfigurableTable table, Long entityId) {
        Set<CustomFieldOnTheFly> fields = fieldSet(table, entityId);
        if (fields == null) {
            throw new IllegalArgumentException("No " + table + " record " + entityId);
        }
        return fields;
    }

    /** The record's managed link set, null when the record does not exist (or the table has none). */
    private Set<CustomFieldOnTheFly> fieldSet(ConfigurableTable table, Long entityId) {
        return switch (table) {
            case UE -> {
                RecordingUnit e = entityManager.find(RecordingUnit.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case MOBILIER -> {
                Specimen e = entityManager.find(Specimen.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case PHASE -> {
                Phase e = entityManager.find(Phase.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case CONTENANT -> {
                Container e = entityManager.find(Container.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case DOCUMENT -> {
                Document e = entityManager.find(Document.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case STUDY -> {
                Study e = entityManager.find(Study.class, entityId);
                yield e == null ? null : e.getOnTheFlyFields();
            }
            case PROJECT -> null;
        };
    }
}
