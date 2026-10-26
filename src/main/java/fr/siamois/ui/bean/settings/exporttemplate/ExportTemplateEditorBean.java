package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntityKind;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.OutputType;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver.FieldOption;
import fr.siamois.domain.services.exporttemplate.ExportNavigations;
import fr.siamois.domain.services.exporttemplate.ExportTemplateChecker;
import fr.siamois.domain.services.exporttemplate.ExportEngine;
import fr.siamois.domain.services.exporttemplate.ExportService;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader.SourceCounts;
import fr.siamois.domain.services.exporttemplate.ExportTemplateService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditColumn;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditField;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditPart;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditRule;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSheet;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSource;
import fr.siamois.utils.MessageUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.faces.model.SelectItem;
import jakarta.faces.model.SelectItemGroup;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Éditeur de mapping d'un modèle d'export (paramètres, JSF) : feuilles, sources, colonnes, règles.
 * Travaille sur une copie modifiable ({@link ExportTemplateEditModel}) ; l'enregistrement vérifie la
 * grammaire (codec) puis les registres ({@link ExportTemplateChecker}) avant d'écrire.
 */
@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ExportTemplateEditorBean implements Serializable {

    /** Option d'une liste déroulante : libellé affiché et valeur stockée. */
    public record Item(String label, String value) implements Serializable {
    }

    private final transient ExportTemplateService exportTemplateService;
    private final transient ExportService exportService;
    private final transient ExportFieldResolver exportFieldResolver;
    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient ActionUnitService actionUnitService;
    private final transient ExportSourceReader exportSourceReader;
    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final LabelBean labelBean;
    private final RedirectBean redirectBean;

    private Long templateId;
    private String templateName;
    private ExportTemplateEditModel model;
    private int selectedSheetIndex;
    private EditColumn selectedColumn;
    /** Nom de la colonne sélectionnée tel qu'il était à la sélection, pour suivre un renommage dans le tri. */
    private String selectedColumnHeader;
    private Long referenceProjectId;
    /** Modèle publié : tout est en lecture seule (le service refuse aussi toute écriture). */
    @Setter(AccessLevel.NONE)
    private boolean published;
    private List<Item> projectItems = new ArrayList<>();

    private transient Map<ExportSubject, List<FieldOption>> fieldCache = new EnumMap<>(ExportSubject.class);
    private transient Map<ConfigurableTable, List<Item>> typeCache = new EnumMap<>(ConfigurableTable.class);
    private transient Map<ExportSubject, SourceCounts> countCache = new EnumMap<>(ExportSubject.class);
    private transient Map<ExportTechnicalSource, Integer> technicalCountCache = new EnumMap<>(ExportTechnicalSource.class);

    // ------------------------------------------------------------------ ouverture

    /** Ouvre l'éditeur sur un modèle de l'institution ; retourne la navigation vers la page. */
    public String open(Long id) {
        if (!canManage()) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
            return null;
        }
        try {
            load(id);
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
            return null;
        } catch (InvalidExportTemplateException e) {
            // Modèle enregistré dans un ancien format ou corrompu : on le signale, la liste permet de le supprimer.
            log.warn("Export template {} cannot be read: {}", id, e.getMessage());
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.unreadable", e.getMessage());
            return null;
        }
        return "/pages/settings/exportTemplateEditor.xhtml?faces-redirect=true";
    }

    private void load(Long id) {
        ExportTemplateDTO dto = exportTemplateService.find(userInfoUser(), id);
        templateId = dto.id();
        templateName = dto.name();
        referenceProjectId = dto.referenceProjectId();
        published = dto.published();
        model = ExportTemplateEditModel.from(exportTemplateService.getDefinition(userInfoUser(), id));
        selectedSheetIndex = 0;
        selectedColumn = null;
        sourceDialog.reset();
        addColumnOpen = false;
        closePreview();
        clearCaches();
        loadProjects();
    }

    private fr.siamois.domain.models.UserInfo userInfoUser() {
        return sessionSettingsBean.getUserInfo();
    }

    private void loadProjects() {
        projectItems = actionUnitService.findAllByInstitution(sessionSettingsBean.getSelectedInstitution()).stream()
                .sorted(Comparator.comparing(a -> a.getFullIdentifier() == null ? "" : a.getFullIdentifier()))
                .map(a -> new Item(projectLabel(a), String.valueOf(a.getId())))
                .toList();
    }

    private static String projectLabel(ActionUnitDTO a) {
        String id = a.getFullIdentifier();
        return id == null || id.isBlank() ? a.getName() : id + " — " + a.getName();
    }

    public boolean canManage() {
        return profilePermissionService.hasInstancePermission(
                userInfoUser().getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(
                userInfoUser(), PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
    }

    /** Garde de page : retour à la liste sans modèle ouvert ou sans droit de gestion. */
    public void checkOrRedirect() {
        if (model == null || !canManage()) {
            redirectBean.redirectTo("/settings/export-templates");
        }
    }

    /** Retour à la liste des modèles (la liste se recharge à l'ouverture de la page). */
    public void backToList() {
        redirectBean.redirectTo("/settings/export-templates");
    }

    // ------------------------------------------------------------------ projet de référence

    /** Valeur texte du projet de référence, pour la liste déroulante (évite un convertisseur). */
    public String getReferenceProject() {
        return referenceProjectId == null ? "" : String.valueOf(referenceProjectId);
    }

    /** Libellé du projet de référence, ou un tiret s'il n'y en a pas. */
    public String getReferenceProjectLabel() {
        String value = getReferenceProject();
        return projectItems.stream().filter(i -> i.value().equals(value)).map(Item::label).findFirst().orElse("—");
    }

    public void setReferenceProject(String value) {
        referenceProjectId = value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    public void onReferenceProjectChange() {
        try {
            exportTemplateService.setReferenceProject(userInfoUser(), templateId, referenceProjectId);
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        }
        clearCaches();
    }

    private void clearCaches() {
        fieldCache = new EnumMap<>(ExportSubject.class);
        typeCache = new EnumMap<>(ConfigurableTable.class);
        countCache = new EnumMap<>(ExportSubject.class);
        technicalCountCache = new EnumMap<>(ExportTechnicalSource.class);
        sample = null;
        sampleKey = null;
    }

    // ------------------------------------------------------------------ feuilles

    public EditSheet getSelectedSheet() {
        if (model == null || model.getSheets().isEmpty()) {
            return null;
        }
        selectedSheetIndex = Math.max(0, Math.min(selectedSheetIndex, model.getSheets().size() - 1));
        return model.getSheets().get(selectedSheetIndex);
    }

    /** Feuille sélectionnée, pour les actions qui n'ont de sens que sur une feuille. */
    private EditSheet requireSelectedSheet() {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null) {
            throw new IllegalStateException("No sheet is selected");
        }
        return sheet;
    }

    public void selectSheet(EditSheet sheet) {
        selectedSheetIndex = model.getSheets().indexOf(sheet);
        selectedColumn = null;
        gapSource = -1;
        sourceDialog.reset();
    }

    public boolean isSelected(EditSheet sheet) {
        return sheet == getSelectedSheet();
    }

    /** Ajoute une feuille vide « Feuille N » et ouvre aussitôt le choix de sa source de donnée. */
    public void addSheet() {
        if (published) {
            return;
        }
        EditSheet sheet = new EditSheet();
        sheet.setName(nextSheetName());
        model.getSheets().add(sheet);
        selectedSheetIndex = model.getSheets().size() - 1;
        selectedColumn = null;
        gapSource = -1;
        sourceDialog.reset();
    }

    private String nextSheetName() {
        int n = model.getSheets().size() + 1;
        String name = langBean.msg("exportTemplates.editor.sheet.defaultName", String.valueOf(n));
        while (nameTaken(name)) {
            n++;
            name = langBean.msg("exportTemplates.editor.sheet.defaultName", String.valueOf(n));
        }
        return name;
    }

    private boolean nameTaken(String name) {
        return model.getSheets().stream().anyMatch(s -> name.equals(s.getName()));
    }

    public void removeSheet(EditSheet sheet) {
        if (published) {
            return;
        }
        model.getSheets().remove(sheet);
        selectedColumn = null;
    }

    public void moveSheet(EditSheet sheet, int delta) {
        if (published) {
            return;
        }
        move(model.getSheets(), sheet, delta);
    }

    // ------------------------------------------------------------------ sources

    /** Retire une source et recale les indices de source des règles ; une règle qui ne visait que elle disparaît. */
    public void removeSource(EditSource source) {
        if (published) {
            return;
        }
        EditSheet sheet = requireSelectedSheet();
        int removed = sheet.getSources().indexOf(source);
        if (removed < 0) {
            return;
        }
        sheet.getSources().remove(removed);
        gapSource = -1;
        for (EditColumn column : sheet.getColumns()) {
            column.getRules().removeIf(rule -> reindexAfterRemoval(rule, removed));
        }
    }

    /** @return true si la règle n'avait que la source retirée (elle doit être supprimée) */
    private static boolean reindexAfterRemoval(EditRule rule, int removed) {
        if (rule.getSources().isEmpty()) {
            return false;
        }
        List<String> reindexed = new ArrayList<>();
        for (String s : rule.getSources()) {
            int index = Integer.parseInt(s);
            if (index != removed) {
                reindexed.add(String.valueOf(index > removed ? index - 1 : index));
            }
        }
        rule.setSources(reindexed);
        return reindexed.isEmpty();
    }

    // ------------------------------------------------------------------ colonnes

    public void removeColumn(EditColumn column) {
        if (published) {
            return;
        }
        EditSheet sheet = requireSelectedSheet();
        sheet.getColumns().remove(column);
        sheet.getSortBy().remove(column.getHeader());
        if (selectedColumn == column) {
            selectedColumn = null;
        }
    }

    public void moveColumn(EditColumn column, int delta) {
        if (published) {
            return;
        }
        move(requireSelectedSheet().getColumns(), column, delta);
    }

    public void selectColumn(EditColumn column) {
        selectedColumn = column;
        selectedColumnHeader = column == null ? null : column.getHeader();
    }

    public boolean isColumnSelected(EditColumn column) {
        return column == selectedColumn;
    }

    // ------------------------------------------------------------------ règles

    public void removeRule(EditRule rule) {
        if (published) {
            return;
        }
        selectedColumn.getRules().remove(rule);
    }

    public void addPart(EditRule rule) {
        if (published) {
            return;
        }
        rule.getParts().add(new EditPart());
    }

    public void addLiteralPart(EditRule rule) {
        if (published) {
            return;
        }
        EditPart part = new EditPart();
        part.setLiteralPart(true);
        part.setLiteral("");
        rule.getParts().add(part);
    }

    public void removePart(EditRule rule, EditPart part) {
        if (published) {
            return;
        }
        rule.getParts().remove(part);
    }

    private static <T> void move(List<T> list, T item, int delta) {
        int from = list.indexOf(item);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= list.size()) {
            return;
        }
        list.remove(from);
        list.add(to, item);
    }

    // ------------------------------------------------------------------ aperçu

    /** Lignes lues par source dans l'aperçu. */
    static final int PREVIEW_ROWS = 20;

    private boolean previewOpen;
    private transient ExportEngine.Preview preview;
    private List<String> previewWarnings = new ArrayList<>();

    /** Ouvre ou ferme l'aperçu ; à l'ouverture, il est calculé sur le modèle en cours d'édition. */
    public void togglePreview() {
        if (previewOpen) {
            closePreview();
        } else {
            refreshPreview();
        }
    }

    public static final String VIEW_MAPPING = "MAPPING";
    public static final String VIEW_PREVIEW = "PREVIEW";

    /** Vue demandée par la bascule Mapping / Aperçu, appliquée par {@link #applyViewMode()}. */
    private String requestedView;

    /** Vue affichée : l'aperçu n'est « ouvert » que s'il a pu être calculé. */
    public String getViewMode() {
        return previewOpen ? VIEW_PREVIEW : VIEW_MAPPING;
    }

    public void setViewMode(String mode) {
        this.requestedView = mode;
    }

    public void applyViewMode() {
        if (VIEW_PREVIEW.equals(requestedView)) {
            refreshPreview();
        } else {
            closePreview();
        }
    }

    public void closePreview() {
        previewOpen = false;
        preview = null;
        previewWarnings = new ArrayList<>();
    }

    /**
     * Calcule l'aperçu sur le projet de référence avec le modèle en cours d'édition (même enregistré ou non) :
     * il passe par les mêmes contrôles que l'enregistrement, sans rien écrire.
     */
    public void refreshPreview() {
        if (model == null) {
            return;
        }
        if (referenceProjectId == null) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.preview.noProject");
            return;
        }
        normalizeFieldKinds();
        try {
            ExportTemplateDefinition definition = ExportTemplateJson.parse(ExportTemplateJson.toJson(model.toDefinition()));
            preview = exportService.preview(userInfoUser(), definition, referenceProjectId, PREVIEW_ROWS);
            previewWarnings = preview.warnings().stream().map(w -> ExportWarningMessages.of(langBean, w)).toList();
            previewOpen = true;
        } catch (IllegalArgumentException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_INVALID, e.getMessage());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
        }
    }

    /** Aperçu de la feuille sélectionnée ; nul s'il n'est pas ouvert. */
    public ExportEngine.SheetPreview getPreviewOfSelectedSheet() {
        EditSheet selected = getSelectedSheet();
        if (!previewOpen || preview == null || selected == null) {
            return null;
        }
        return preview.sheets().stream().filter(s -> s.name().equals(selected.getName())).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ fenêtre de source et menus

    /** Popover « Réglages du modèle » et menu « Feuille » ; le nom, la version, le patron et le projet sont gardés pour « Annuler ». */
    private String modelNameBefore;
    private String modelVersionBefore;
    private String modelPatternBefore;
    private Long referenceProjectBefore;

    /** À l'ouverture du popover « Réglages du modèle » : garde les valeurs pour « Annuler ». */
    public void snapshotModelSettings() {
        modelNameBefore = model.getName();
        modelVersionBefore = model.getVersion();
        modelPatternBefore = model.getFileNamePattern();
        referenceProjectBefore = referenceProjectId;
    }

    /** « Annuler » : remet le nom, la version, le patron et le projet de référence tels qu'à l'ouverture. */
    public void cancelModelSettings() {
        model.setName(modelNameBefore);
        model.setVersion(modelVersionBefore);
        model.setFileNamePattern(modelPatternBefore);
        referenceProjectId = referenceProjectBefore;
    }

    /** « Enregistrer » : le nom ne peut pas être vide ; le projet de référence est une donnée locale, écrite tout de suite. */
    public void applyModelSettings() {
        if (published) {
            cancelModelSettings();
            return;
        }
        if (model.getName() == null || model.getName().isBlank()) {
            model.setName(modelNameBefore);
        }
        if (!Objects.equals(referenceProjectId, referenceProjectBefore)) {
            onReferenceProjectChange();
        }
    }

    /** Première colonne de tri de la feuille ({@code ""} = aucun tri). */
    public String getSortColumn() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null || sheet.getSortBy().isEmpty() ? "" : sheet.getSortBy().get(0);
    }

    public void setSortColumn(String header) {
        if (published) {
            return;
        }
        EditSheet sheet = requireSelectedSheet();
        sheet.getSortBy().clear();
        if (header != null && !header.isBlank()) {
            sheet.getSortBy().add(header);
        }
    }

    public void duplicateSheet() {
        if (published) {
            return;
        }
        EditSheet copy = ExportTemplateEditModel.copyOf(requireSelectedSheet());
        copy.setName(copy.getName() + langBean.msg("exportTemplates.editor.sheet.copySuffix"));
        model.getSheets().add(model.getSheets().indexOf(getSelectedSheet()) + 1, copy);
        selectedSheetIndex = model.getSheets().indexOf(copy);
        selectedColumn = null;
    }

    public void removeSelectedSheet() {
        if (published) {
            return;
        }
        removeSheet(getSelectedSheet());
    }


    // ------------------------------------------------------------------ sources : puces et fenêtre de choix

    private ExportSourceDialog sourceDialog = new ExportSourceDialog();
    /** Source de la feuille dont on affiche les colonnes non couvertes ; -1 = pas de filtre. */
    private int gapSource = -1;

    private record TableDef(String key, String label, ExportEntityStyle style, ExportSubject subject, EntityKind entity,
                            ExportTechnicalSource technical) {
    }

    /** Tables proposées : le projet, les entités, puis les tables techniques. */
    private List<TableDef> tableDefs() {
        List<TableDef> defs = new ArrayList<>();
        defs.add(new TableDef(ExportSourceDialog.TABLE_PROJECT, langBean.msg("exportTemplates.editor.source.PROJECT"),
                ExportEntityStyle.project(), ExportSubject.PROJECT, null, null));
        for (EntityKind kind : List.of(EntityKind.SPATIAL_UNIT, EntityKind.RECORDING_UNIT, EntityKind.SPECIMEN, EntityKind.PHASE,
                EntityKind.CONTAINER, EntityKind.DOCUMENT, EntityKind.STUDY)) {
            defs.add(new TableDef(kind.name(), langBean.msg("exportTemplates.editor.entity." + kind.name()),
                    ExportEntityStyle.of(kind), ExportSubject.of(kind), kind, null));
        }
        for (ExportTechnicalSource technical : ExportTechnicalSource.values()) {
            defs.add(new TableDef(ExportSourceDialog.TABLE_TECHNICAL_PREFIX + technical.key(),
                    langBean.msg("exportTemplates.editor.technical." + technical.name()),
                    ExportEntityStyle.neutral(), null, null, technical));
        }
        return defs;
    }

    private Optional<TableDef> tableDef(String key) {
        return tableDefs().stream().filter(d -> d.key().equals(key)).findFirst();
    }

    /** Clé de table d'une source ; nulle pour une table du cœur commun, que cette fenêtre ne sait pas modifier. */
    private static String tableKeyOf(EditSource source) {
        return switch (source.getKind()) {
            case ExportTemplateEditModel.KIND_ENTITY -> entityOf(source).map(Enum::name).orElse(null);
            case ExportTemplateEditModel.KIND_TECHNICAL -> source.getTechnicalKey() == null ? null
                    : ExportSourceDialog.TABLE_TECHNICAL_PREFIX + source.getTechnicalKey();
            case ExportTemplateEditModel.KIND_TABLE -> null;
            default -> ExportSourceDialog.TABLE_PROJECT;
        };
    }

    private SourceCounts countsOf(ExportSubject subject) {
        if (referenceProjectId == null || subject == null) {
            return null;
        }
        if (countCache == null) {
            clearCaches();
        }
        try {
            return countCache.computeIfAbsent(subject, s -> exportSourceReader.count(s, referenceProjectId));
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private Integer technicalCountOf(ExportTechnicalSource technical) {
        if (referenceProjectId == null) {
            return null;
        }
        if (technicalCountCache == null) {
            clearCaches();
        }
        try {
            return technicalCountCache.computeIfAbsent(technical, t -> exportSourceReader.countTechnical(t, referenceProjectId));
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static ConceptRef conceptRefOf(String typeKey) {
        String[] p = typeKey.split("\\|", 3);
        return new ConceptRef(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : null);
    }

    private static String countKeyOf(String typeKey) {
        return ExportSourceReader.countKey(conceptRefOf(typeKey));
    }

    private String recordsLabel(int n) {
        return langBean.msg("exportTemplates.editor.source.records", String.valueOf(n));
    }

    /** Les sources de la feuille, en puces : table, types, lignes du projet de référence et couverture des colonnes. */
    public List<ExportSourceDialog.Chip> getSourceChips() {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null) {
            return List.of();
        }
        List<ExportSourceDialog.Chip> chips = new ArrayList<>();
        for (int i = 0; i < sheet.getSources().size(); i++) {
            EditSource source = sheet.getSources().get(i);
            String key = tableKeyOf(source);
            Optional<TableDef> def = key == null ? Optional.empty() : tableDef(key);
            ExportEntityStyle style = def.map(TableDef::style).orElse(ExportEntityStyle.neutral());
            int covered = coveredColumns(sheet, i);
            chips.add(new ExportSourceDialog.Chip(source, i, describe(source), style, sourceTypesLabel(source), sourceRowsLabel(source),
                    covered, sheet.getColumns().size(), key != null, gapSource == i));
        }
        return chips;
    }

    private int coveredColumns(EditSheet sheet, int sourceIndex) {
        return (int) sheet.getColumns().stream().filter(c -> ruleAt(c, sourceIndex).isPresent()).count();
    }

    private String sourceTypesLabel(EditSource source) {
        if (!ExportTemplateEditModel.KIND_ENTITY.equals(source.getKind())) {
            return "";
        }
        return source.getTypeKeys().isEmpty() ? langBean.msg("exportTemplates.editor.source.allTypes") : sourceTypes(source);
    }

    private String sourceRowsLabel(EditSource source) {
        Integer rows = switch (source.getKind()) {
            case ExportTemplateEditModel.KIND_ENTITY -> entityOf(source)
                    .map(k -> countsOf(ExportSubject.of(k)))
                    .map(c -> c.rowsOf(source.getTypeKeys().stream().map(ExportTemplateEditorBean::conceptRefOf).toList()))
                    .orElse(null);
            case ExportTemplateEditModel.KIND_TECHNICAL -> ExportTechnicalSource.ofKey(source.getTechnicalKey())
                    .map(this::technicalCountOf).orElse(null);
            case ExportTemplateEditModel.KIND_TABLE -> null;
            default -> countsOf(ExportSubject.PROJECT) == null ? null : 1;
        };
        if (rows == null) {
            return "";
        }
        return langBean.msg(rows > 1 ? "exportTemplates.editor.source.rows.many" : "exportTemplates.editor.source.rows.one", String.valueOf(rows));
    }

    /** Pas de source : la recherche et le tableau restent grisés. */
    public boolean isNoSources() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null || sheet.getSources().isEmpty();
    }

    /** Badge de couverture : passe en Mapping et ne garde que les colonnes que la source ne couvre pas (un second clic retire le filtre). */
    public void showGaps(int sourceIndex) {
        closePreview();
        gapSource = gapSource == sourceIndex ? -1 : sourceIndex;
    }

    public void clearGap() {
        gapSource = -1;
    }

    public boolean isGapFilter() {
        EditSheet sheet = getSelectedSheet();
        return sheet != null && gapSource >= 0 && gapSource < sheet.getSources().size();
    }

    public String getGapLabel() {
        EditSheet sheet = getSelectedSheet();
        if (!isGapFilter()) {
            return "";
        }
        EditSource source = sheet.getSources().get(gapSource);
        String types = sourceTypes(source);
        return describe(source) + (types.isBlank() ? "" : " / " + types);
    }

    // ------------------------------------------------------------------ fenêtre « source de donnée »

    /** Ouvre la fenêtre pour une nouvelle source. */
    public void openSourceDialog() {
        if (published) {
            return;
        }
        sourceDialog.reset();
        sourceDialog.setExpanded(null);
    }

    /** Ouvre la fenêtre sur une source existante. */
    public void editSource(EditSource source) {
        if (published) {
            return;
        }
        sourceDialog.reset();
        sourceDialog.setEditIndex(requireSelectedSheet().getSources().indexOf(source));
        String key = tableKeyOf(source);
        if (key == null) {
            return;
        }
        sourceDialog.setTable(key);
        tableDef(key).map(TableDef::entity).ifPresent(kind -> {
            if (source.getTypeKeys().isEmpty()) {
                typeItemsFor(kind, List.of()).forEach(i -> sourceDialog.getTypes().add(i.value()));
            } else {
                sourceDialog.getTypes().addAll(source.getTypeKeys());
            }
        });
        rebuildFieldRows();
    }

    private List<String> typeKeysOf(String tableKey) {
        return tableDef(tableKey).map(TableDef::entity)
                .map(kind -> typeItemsFor(kind, sourceDialog.getTypes()).stream().map(Item::value).toList())
                .orElse(List.of());
    }

    /** L'arbre de la fenêtre : tables dépliables, types à cocher, nombre d'enregistrements du projet de référence. */
    public List<ExportSourceDialog.Table> getSourceTree() {
        String q = sourceDialog.getQuery() == null ? "" : sourceDialog.getQuery().trim().toLowerCase();
        List<ExportSourceDialog.Table> tree = new ArrayList<>();
        for (TableDef def : tableDefs()) {
            boolean tableMatch = q.isEmpty() || def.label().toLowerCase().contains(q);
            List<Item> items = def.entity() == null ? List.of() : typeItemsFor(def.entity(), sourceDialog.getTypes());
            boolean current = def.key().equals(sourceDialog.getTable());
            SourceCounts counts = countsOf(def.subject());
            List<ExportSourceDialog.Type> types = items.stream()
                    .filter(i -> tableMatch || i.label().toLowerCase().contains(q))
                    .map(i -> typeNode(i, current, counts))
                    .toList();
            if (!tableMatch && types.isEmpty()) {
                continue;
            }
            String state = !current ? ExportSourceDialog.STATE_NONE
                    : items.isEmpty() || sourceDialog.getTypes().containsAll(items.stream().map(Item::value).toList())
                    ? ExportSourceDialog.STATE_ALL : ExportSourceDialog.STATE_SOME;
            String expandedKey = sourceDialog.getExpanded();
            boolean expanded = !types.isEmpty() && (!q.isEmpty() || def.key().equals(expandedKey) || (expandedKey == null && current));
            tree.add(new ExportSourceDialog.Table(def.key(), def.label(), def.style(), totalLabel(def, counts), expanded, state, types));
        }
        return tree;
    }

    private ExportSourceDialog.Type typeNode(Item item, boolean tablePicked, SourceCounts counts) {
        boolean picked = tablePicked && sourceDialog.getTypes().contains(item.value());
        if (counts == null) {
            return new ExportSourceDialog.Type(item.value(), item.label(), "", false, picked);
        }
        int n = counts.byType().getOrDefault(countKeyOf(item.value()), 0);
        return new ExportSourceDialog.Type(item.value(), item.label(),
                n == 0 ? langBean.msg("exportTemplates.editor.source.empty") : recordsLabel(n), n == 0, picked);
    }

    private String totalLabel(TableDef def, SourceCounts counts) {
        if (def.technical() != null) {
            Integer n = technicalCountOf(def.technical());
            return n == null ? "" : recordsLabel(n);
        }
        return counts == null ? "" : recordsLabel(counts.total());
    }

    public void toggleExpand(ExportSourceDialog.Table table) {
        sourceDialog.setExpanded(table.isExpanded() ? "" : table.getKey());
    }

    /** Case d'une table : tout cocher, ou tout décocher si tout l'est déjà. */
    public void toggleTable(ExportSourceDialog.Table table) {
        boolean changed = !table.getKey().equals(sourceDialog.getTable());
        if (table.isAll()) {
            sourceDialog.setTable(null);
            sourceDialog.getTypes().clear();
        } else {
            sourceDialog.setTable(table.getKey());
            sourceDialog.setTypes(new java.util.LinkedHashSet<>(typeKeysOf(table.getKey())));
            sourceDialog.setExpanded(table.getKey());
        }
        rebuildFieldRowsIf(changed || sourceDialog.getTable() == null);
    }

    /** Case d'un type : choisir un type d'une autre table remplace le choix (une source = une table). */
    public void toggleType(String tableKey, String typeKey) {
        if (!tableKey.equals(sourceDialog.getTable())) {
            sourceDialog.setTable(tableKey);
            sourceDialog.setTypes(new java.util.LinkedHashSet<>(List.of(typeKey)));
            rebuildFieldRows();
            return;
        }
        if (!sourceDialog.getTypes().remove(typeKey)) {
            sourceDialog.getTypes().add(typeKey);
        }
        if (sourceDialog.getTypes().isEmpty()) {
            sourceDialog.setTable(null);
            rebuildFieldRows();
        }
    }

    private void rebuildFieldRowsIf(boolean rebuild) {
        if (rebuild) {
            rebuildFieldRows();
        }
    }

    /** Un champ que la source peut fournir : sa nature (concept ou colonne), sa clé et son libellé. */
    private record FieldCandidate(String kind, String key, String label) {
    }

    private List<FieldCandidate> candidatesOf(TableDef def) {
        List<FieldCandidate> out = new ArrayList<>();
        if (def.technical() != null) {
            def.technical().valueNames().stream().sorted()
                    .forEach(name -> out.add(new FieldCandidate(ExportTemplateEditModel.FIELD_COLUMN, name, name)));
        } else {
            for (FieldOption o : fieldsOf(def.subject())) {
                out.add(new FieldCandidate(ExportTemplateEditModel.FIELD_CONCEPT, ExportTemplateEditModel.keyOf(o.concept()),
                        o.system() ? langBean.msg(o.label()) : o.label()));
            }
        }
        return out;
    }

    /** Champs de la table choisie, tous décochés ; la colonne cible est celle qui lit déjà ce champ, sinon une nouvelle. */
    private void rebuildFieldRows() {
        List<ExportSourceDialog.FieldRow> rows = new ArrayList<>();
        Optional<TableDef> def = sourceDialog.getTable() == null ? Optional.empty() : tableDef(sourceDialog.getTable());
        if (def.isPresent()) {
            for (FieldCandidate f : candidatesOf(def.get())) {
                rows.add(new ExportSourceDialog.FieldRow(f.kind(), f.key(), f.label(), existingTargetOf(f.kind(), f.key())));
            }
        }
        sourceDialog.setFieldRows(rows);
        sourceDialog.setFieldQuery("");
    }

    private String existingTargetOf(String kind, String key) {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null) {
            return "";
        }
        return sheet.getColumns().stream()
                .filter(c -> c.getRules().stream().anyMatch(r -> ExportTemplateEditModel.RULE_DIRECT.equals(r.getType())
                        && (r.getPath() == null || r.getPath().isEmpty())
                        && kind.equals(r.getField().getKind()) && key.equals(r.getField().getKey())))
                .map(EditColumn::getHeader).findFirst().orElse("");
    }

    /** Champs visibles après la recherche de la fenêtre. */
    public List<ExportSourceDialog.FieldRow> getVisibleFieldRows() {
        String q = sourceDialog.getFieldQuery() == null ? "" : sourceDialog.getFieldQuery().trim().toLowerCase();
        return sourceDialog.getFieldRows().stream()
                .filter(r -> q.isEmpty() || r.getLabel().toLowerCase().contains(q) || r.getKey().toLowerCase().contains(q))
                .toList();
    }

    public boolean isHasFieldRows() {
        return !sourceDialog.getFieldRows().isEmpty();
    }

    public long getCheckedFieldCount() {
        return getVisibleFieldRows().stream().filter(ExportSourceDialog.FieldRow::isChecked).count();
    }

    /** Case globale à trois états : tout, rien, ou une partie des champs visibles. */
    public String getFieldsSelectionState() {
        long checked = getCheckedFieldCount();
        int total = getVisibleFieldRows().size();
        return checked == 0 ? ExportSourceDialog.STATE_NONE : checked == total ? ExportSourceDialog.STATE_ALL : ExportSourceDialog.STATE_SOME;
    }

    public void toggleAllFields() {
        boolean all = ExportSourceDialog.STATE_ALL.equals(getFieldsSelectionState());
        getVisibleFieldRows().forEach(r -> r.setChecked(!all));
    }

    /** Colonnes cibles possibles d'un champ : une nouvelle colonne, ou une colonne de la feuille. */
    public List<Item> getTargetColumnItems() {
        List<Item> items = new ArrayList<>();
        items.add(new Item(langBean.msg("exportTemplates.editor.source.newColumn"), ""));
        EditSheet sheet = getSelectedSheet();
        if (sheet != null) {
            sheet.getColumns().forEach(c -> items.add(new Item(c.getHeader(), c.getHeader())));
        }
        return items;
    }

    public boolean isSourceDialogConfirmable() {
        return sourceDialog.isPicked();
    }

    /** « Ajouter » / « Enregistrer » : crée ou met à jour la source, puis crée les colonnes des seuls champs cochés. */
    public void confirmSourceDialog() {
        if (published || !sourceDialog.isPicked()) {
            return;
        }
        EditSheet sheet = requireSelectedSheet();
        int index = sourceDialog.getEditIndex();
        EditSource source;
        if (index >= 0 && index < sheet.getSources().size()) {
            source = sheet.getSources().get(index);
        } else {
            source = new EditSource();
            sheet.getSources().add(source);
            index = sheet.getSources().size() - 1;
        }
        applyTable(source);
        importFields(sheet, index);
        sourceDialog.reset();
        selectedColumn = null;
    }

    private void applyTable(EditSource source) {
        String key = sourceDialog.getTable();
        source.getTypeKeys().clear();
        source.setEntity(null);
        source.setTechnicalKey(null);
        if (ExportSourceDialog.TABLE_PROJECT.equals(key)) {
            source.setKind(ExportTemplateEditModel.KIND_PROJECT);
        } else if (key.startsWith(ExportSourceDialog.TABLE_TECHNICAL_PREFIX)) {
            source.setKind(ExportTemplateEditModel.KIND_TECHNICAL);
            source.setTechnicalKey(key.substring(ExportSourceDialog.TABLE_TECHNICAL_PREFIX.length()));
        } else {
            source.setKind(ExportTemplateEditModel.KIND_ENTITY);
            source.setEntity(key);
            // Tous les types cochés = pas de filtre (les entités sans type en font partie).
            if (!sourceDialog.getTypes().containsAll(typeKeysOf(key))) {
                source.getTypeKeys().addAll(sourceDialog.getTypes());
            }
        }
    }

    private void importFields(EditSheet sheet, int sourceIndex) {
        for (ExportSourceDialog.FieldRow row : sourceDialog.getFieldRows()) {
            if (!row.isChecked()) {
                continue;
            }
            Optional<EditColumn> existing = row.getTarget() == null || row.getTarget().isEmpty() ? Optional.empty()
                    : sheet.getColumns().stream().filter(c -> row.getTarget().equals(c.getHeader())).findFirst();
            EditColumn column = existing.orElseGet(() -> newColumn(sheet, row.getLabel()));
            if (ruleAt(column, sourceIndex).isPresent()) {
                continue;
            }
            EditRule rule = new EditRule();
            rule.setType(ExportTemplateEditModel.RULE_DIRECT);
            rule.setSources(new ArrayList<>(List.of(String.valueOf(sourceIndex))));
            rule.getField().setKind(row.getKind());
            rule.getField().setKey(row.getKey());
            column.getRules().add(rule);
        }
    }

    private EditColumn newColumn(EditSheet sheet, String wanted) {
        String header = wanted;
        int n = 2;
        while (headerTaken(sheet, header)) {
            header = wanted + " " + n++;
        }
        EditColumn column = new EditColumn();
        column.setHeader(header);
        sheet.getColumns().add(column);
        return column;
    }

    private static boolean headerTaken(EditSheet sheet, String header) {
        return sheet.getColumns().stream().anyMatch(c -> header.equals(c.getHeader()));
    }


    // ------------------------------------------------------------------ recherche commune aux deux modes

    private String needle() {
        return columnSearch == null ? "" : columnSearch.trim().toLowerCase();
    }

    /** Texte cherché pour une colonne : son nom, ses champs, les tables de ses sources et ses constantes. */
    private String haystack(EditSheet sheet, EditColumn column) {
        StringBuilder text = new StringBuilder(column.getHeader() == null ? "" : column.getHeader());
        for (EditRule rule : column.getRules()) {
            text.append(' ').append(describeRule(rule));
            if (rule.getField() != null && rule.getField().getKey() != null) {
                text.append(' ').append(rule.getField().getKey());
            }
            if (rule.getConstantValue() != null) {
                text.append(' ').append(rule.getConstantValue());
            }
            for (EditPart part : rule.getParts()) {
                text.append(' ').append(part.getLabel() == null ? "" : part.getLabel())
                        .append(' ').append(part.getLiteral() == null ? "" : part.getLiteral())
                        .append(' ').append(part.isLiteralPart() ? "" : choiceLabel(rule.getSources(), part.getFieldChoice()));
            }
            ruleSourceIndexes(sheet, rule).forEach(i -> text.append(' ').append(describe(sheet.getSources().get(i))));
        }
        return text.toString().toLowerCase();
    }

    private static List<Integer> ruleSourceIndexes(EditSheet sheet, EditRule rule) {
        if (rule.getSources().isEmpty()) {
            return java.util.stream.IntStream.range(0, sheet.getSources().size()).boxed().toList();
        }
        return rule.getSources().stream().map(Integer::valueOf).filter(i -> i < sheet.getSources().size()).toList();
    }

    /** Une ligne du menu de suggestions de la recherche. */
    public record Suggestion(String label, String sub, EditColumn column, String fieldKind, String fieldKey) implements Serializable {
    }

    private static final int SUGGEST_COLUMNS = 5;
    private static final int SUGGEST_FIELDS = 6;

    /** Colonnes dont le nom correspond (les premières, tant que rien n'est tapé). */
    public List<Suggestion> getSuggestedColumns() {
        EditSheet sheet = getSelectedSheet();
        String needle = needle();
        if (sheet == null) {
            return List.of();
        }
        return sheet.getColumns().stream()
                .filter(c -> needle.isEmpty() || (c.getHeader() != null && c.getHeader().toLowerCase().contains(needle)))
                .limit(SUGGEST_COLUMNS)
                .map(c -> new Suggestion(c.getHeader(), mappingSummary(c), c, null, null))
                .toList();
    }

    /** Colonnes dont le nom ne correspond pas mais qui sont mappées sur le champ (ou la table, ou la constante) cherché. */
    public List<Suggestion> getSuggestedMappedColumns() {
        EditSheet sheet = getSelectedSheet();
        String needle = needle();
        if (sheet == null || needle.isEmpty()) {
            return List.of();
        }
        return sheet.getColumns().stream()
                .filter(c -> c.getHeader() == null || !c.getHeader().toLowerCase().contains(needle))
                .filter(c -> haystack(sheet, c).contains(needle))
                .limit(SUGGEST_COLUMNS)
                .map(c -> new Suggestion(c.getHeader(), "← " + mappingSummary(c), c, null, null))
                .toList();
    }

    private String mappingSummary(EditColumn column) {
        String text = mappingText(column);
        return text.isEmpty() ? langBean.msg("exportTemplates.editor.status.NONE") : text;
    }

    /** Champs des sources de la feuille à partir desquels créer une colonne mappée en direct. */
    public List<Suggestion> getSuggestedFields() {
        EditSheet sheet = getSelectedSheet();
        if (published || sheet == null) {
            return List.of();
        }
        String needle = needle();
        Map<String, Suggestion> found = new java.util.LinkedHashMap<>();
        for (EditSource source : sheet.getSources()) {
            String key = tableKeyOf(source);
            Optional<TableDef> def = key == null ? Optional.empty() : tableDef(key);
            if (def.isEmpty()) {
                continue;
            }
            for (FieldCandidate f : candidatesOf(def.get())) {
                if (needle.isEmpty() || f.label().toLowerCase().contains(needle) || f.key().toLowerCase().contains(needle)) {
                    found.putIfAbsent(f.kind() + "\u0000" + f.key(), new Suggestion(f.label(), def.get().label(), null, f.kind(), f.key()));
                }
            }
        }
        return found.values().stream().limit(SUGGEST_FIELDS).toList();
    }

    public boolean isNoSuggestion() {
        return getSuggestedColumns().isEmpty() && getSuggestedMappedColumns().isEmpty() && getSuggestedFields().isEmpty();
    }

    /** Choix d'une colonne dans le menu : elle est sélectionnée et son détail s'ouvre. */
    public void pickSuggestedColumn(Suggestion suggestion) {
        selectColumn(suggestion.column());
    }

    /** « + Nouveau » : crée une colonne nommée comme le champ, mappée en direct sur les sources qui le fournissent. */
    public void createFromSuggestedField(Suggestion suggestion) {
        EditSheet sheet = getSelectedSheet();
        if (published || sheet == null) {
            return;
        }
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < sheet.getSources().size(); i++) {
            String key = tableKeyOf(sheet.getSources().get(i));
            Optional<TableDef> def = key == null ? Optional.empty() : tableDef(key);
            if (def.isPresent() && candidatesOf(def.get()).stream()
                    .anyMatch(f -> f.kind().equals(suggestion.fieldKind()) && f.key().equals(suggestion.fieldKey()))) {
                sources.add(String.valueOf(i));
            }
        }
        EditColumn column = newColumn(sheet, suggestion.label());
        EditRule rule = new EditRule();
        rule.setType(ExportTemplateEditModel.RULE_DIRECT);
        rule.setSources(sources);
        rule.getField().setKind(suggestion.fieldKind());
        rule.getField().setKey(suggestion.fieldKey());
        column.getRules().add(rule);
        columnSearch = "";
        columnFilter = FILTER_ALL;
        gapSource = -1;
        selectColumn(column);
    }

    /** Positions, dans la feuille, des colonnes que la recherche et les filtres laissent visibles (pour l'aperçu). */
    public List<Integer> getPreviewColumnIndexes() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null ? List.of() : getVisibleColumns().stream().map(c -> sheet.getColumns().indexOf(c)).toList();
    }

    public String previewHeader(int index) {
        ExportEngine.SheetPreview sp = getPreviewOfSelectedSheet();
        return sp == null || index < 0 || index >= sp.headers().size() ? "" : sp.headers().get(index);
    }

    /** Champ mappé de la colonne, affiché sous son nom dans l'en-tête de l'aperçu. */
    public String previewFieldText(int index) {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null || index < 0 || index >= sheet.getColumns().size()) {
            return "";
        }
        return mappingSummary(sheet.getColumns().get(index));
    }

    public String previewCell(List<String> row, int index) {
        return row == null || index < 0 || index >= row.size() || row.get(index) == null ? "" : row.get(index);
    }


    // ------------------------------------------------------------------ panneau de détail (commun aux deux modes)

    /** Le panneau est toujours là en mode Mapping ; en mode Aperçu, il s'ouvre avec une colonne et se ferme avec ✕. */
    public boolean isDetailVisible() {
        return !previewOpen || selectedColumn != null;
    }

    public void closeDetail() {
        selectColumn(null);
    }

    /** Clic sur un en-tête ou une cellule de l'aperçu : la colonne (position dans la feuille, paramètre {@code ix}) est sélectionnée. */
    public void selectColumnAt() {
        EditSheet sheet = getSelectedSheet();
        String ix = jakarta.faces.context.FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("ix");
        if (sheet == null || ix == null) {
            return;
        }
        try {
            int at = Integer.parseInt(ix);
            if (at >= 0 && at < sheet.getColumns().size()) {
                selectColumn(sheet.getColumns().get(at));
            }
        } catch (NumberFormatException e) {
            log.debug("Ignoring column index '{}'", ix);
        }
    }

    /** Largeur de la zone du nom de colonne, qui suit le nom pour que le badge d'état reste collé derrière. */
    public String nameWidth(EditColumn column) {
        int length = column == null || column.getHeader() == null ? 0 : column.getHeader().length();
        return Math.max(6, length + 2) + "ch";
    }

    /** Pastille (icône et famille de couleur) de la table d'une source, pour ses puces dans le panneau. */
    public ExportEntityStyle entityStyleOf(EditSource source) {
        String key = tableKeyOf(source);
        return key == null ? ExportEntityStyle.neutral() : tableDef(key).map(TableDef::style).orElse(ExportEntityStyle.neutral());
    }

    /** Champ proposé par la saisie d'un mapping : libellé, choix ({@code chemin::clé::propriété}) et « relation » si le chemin traverse une jointure. */
    public record FieldSuggestion(String label, String choice, boolean relation) implements Serializable {

        private static final String SEPARATOR = "@@@";

        /** Valeur envoyée par le navigateur : le choix, puis le libellé à réafficher. */
        public String encoded() {
            return choice + SEPARATOR + label;
        }

        public static FieldSuggestion decode(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            int at = value.indexOf(SEPARATOR);
            String choice = at < 0 ? value : value.substring(0, at);
            String label = at < 0 ? value : value.substring(at + SEPARATOR.length());
            return new FieldSuggestion(label, choice, !ExportTemplateEditModel.splitChoice(choice)[0].isEmpty());
        }
    }

    private static final int MAX_FIELD_SUGGESTIONS = 60;

    /**
     * Valeur de la saisie de champ d'une règle ({@code fieldItems[rule]}) : lue avec son libellé, écrite en changeant le choix de la règle.
     * Une saisie vidée retire le champ.
     */
    public Map<Object, FieldSuggestion> getFieldItems() {
        return new java.util.AbstractMap<>() {
            @Override
            public FieldSuggestion get(Object key) {
                if (!(key instanceof EditRule rule) || rule.getFieldChoice().isBlank()) {
                    return null;
                }
                return new FieldSuggestion(choiceLabel(rule.getSources(), rule.getFieldChoice()), rule.getFieldChoice(),
                        !ExportTemplateEditModel.splitChoice(rule.getFieldChoice())[0].isEmpty());
            }

            @Override
            public FieldSuggestion put(Object key, FieldSuggestion value) {
                if (published || !(key instanceof EditRule rule)) {
                    return null;
                }
                rule.setFieldChoice(value == null ? "" : value.choice());
                return value;
            }

            @Override
            public java.util.Set<Entry<Object, FieldSuggestion>> entrySet() {
                return java.util.Set.of();
            }
        };
    }

    /** Suggestions de champs pour une règle (attribut {@code rule} du composant), par libellé ou par clé. */
    public List<FieldSuggestion> completeField(String query) {
        jakarta.faces.component.UIComponent component = jakarta.faces.component.UIComponent
                .getCurrentComponent(jakarta.faces.context.FacesContext.getCurrentInstance());
        Object rule = component == null ? null : component.getAttributes().get("rule");
        if (!(rule instanceof EditRule editRule)) {
            return List.of();
        }
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<FieldSuggestion> out = new ArrayList<>();
        for (SelectItem item : fieldChoices(editRule)) {
            if (item instanceof SelectItemGroup group) {
                for (SelectItem child : group.getSelectItems()) {
                    addSuggestion(out, group.getLabel(), child, needle);
                }
            }
        }
        return out.size() > MAX_FIELD_SUGGESTIONS ? out.subList(0, MAX_FIELD_SUGGESTIONS) : out;
    }

    private static void addSuggestion(List<FieldSuggestion> out, String group, SelectItem item, String needle) {
        String choice = String.valueOf(item.getValue());
        if (choice.isBlank()) {
            return;
        }
        boolean relation = !ExportTemplateEditModel.splitChoice(choice)[0].isEmpty();
        String label = relation ? group + " › " + item.getLabel() : item.getLabel();
        if (needle.isEmpty() || label.toLowerCase().contains(needle) || choice.toLowerCase().contains(needle)) {
            out.add(new FieldSuggestion(label, choice, relation));
        }
    }

    // ------------------------------------------------------------------ liste des colonnes

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_MAPPED = "MAPPED";
    public static final String FILTER_CHECK = "CHECK";
    private static final String ERROR_FORBIDDEN = "common.error.forbidden";
    private static final String ERROR_INVALID = "exportTemplates.editor.error.invalid";

    private String columnFilter = FILTER_ALL;
    private String columnSearch = "";
    private boolean addColumnOpen;
    private String newColumnName = "";

    public void selectColumnFilter(String filter) {
        columnFilter = filter;
    }

    /** Règle qui produit la valeur de la colonne pour la source donnée (une règle sans source vaut pour toutes). */
    public Optional<EditRule> ruleAt(EditColumn column, int sourceIndex) {
        String index = String.valueOf(sourceIndex);
        return column.getRules().stream()
                .filter(r -> r.getSources().isEmpty() || r.getSources().contains(index))
                .findFirst();
    }

    /** Colonne mappée : au moins une règle. */
    public boolean isMapped(EditColumn column) {
        return !column.getRules().isEmpty();
    }

    /** À vérifier : une source de la feuille n'a aucun mapping pour cette colonne. */
    public boolean needsCheck(EditColumn column) {
        EditSheet sheet = getSelectedSheet();
        for (int i = 0; sheet != null && i < sheet.getSources().size(); i++) {
            if (ruleAt(column, i).isEmpty()) {
                return true;
            }
        }
        return sheet == null || sheet.getSources().isEmpty();
    }

    /** Colonnes de la feuille après recherche et filtre. */
    public List<EditColumn> getVisibleColumns() {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null) {
            return List.of();
        }
        String needle = needle();
        return sheet.getColumns().stream()
                .filter(c -> needle.isEmpty() || haystack(sheet, c).contains(needle))
                .filter(c -> gapSource < 0 || ruleAt(c, gapSource).isEmpty())
                .filter(c -> switch (columnFilter == null ? FILTER_ALL : columnFilter) {
                    case FILTER_MAPPED -> isMapped(c);
                    case FILTER_CHECK -> needsCheck(c);
                    default -> true;
                })
                .toList();
    }

    public long getMappedColumnCount() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null ? 0 : sheet.getColumns().stream().filter(this::isMapped).count();
    }

    /** Statut d'une colonne : OK (mappée pour chaque source), CHECK (mappée en partie) ou NONE. */
    public String columnStatus(EditColumn column) {
        if (!isMapped(column)) {
            return "NONE";
        }
        return needsCheck(column) ? FILTER_CHECK : "OK";
    }

    /** Texte de mapping d'une ligne : le champ, ou le nombre de mappings s'il y en a plusieurs ; vide si non mappée. */
    public String mappingText(EditColumn column) {
        if (column.getRules().size() > 1) {
            return langBean.msg("exportTemplates.editor.mappings.count", column.getRules().size());
        }
        return column.getRules().isEmpty() ? "" : describeRule(column.getRules().get(0));
    }

    /** Première valeur non vide de la colonne, dans l'aperçu ouvert ou dans l'échantillon ; vide si le projet de référence ne donne rien. */
    public String previewValue(EditColumn column) {
        ExportEngine.SheetPreview sp = previewOpen ? getPreviewOfSelectedSheet() : sampleOfSelectedSheet();
        EditSheet sheet = getSelectedSheet();
        if (sp == null || sheet == null) {
            return "";
        }
        int at = sheet.getColumns().indexOf(column);
        for (List<String> row : sp.rows()) {
            if (at >= 0 && at < row.size() && row.get(at) != null && !row.get(at).isBlank()) {
                return row.get(at);
            }
        }
        return "";
    }

    /** Lignes lues par source pour l'échantillon de la liste. */
    private static final int SAMPLE_ROWS = 3;
    private static final String SAMPLE_DONE = "xe.sample.done";

    private transient ExportEngine.Preview sample;
    private transient String sampleKey;

    private ExportEngine.SheetPreview sampleOfSelectedSheet() {
        EditSheet selected = getSelectedSheet();
        ExportEngine.Preview current = sample();
        if (current == null || selected == null) {
            return null;
        }
        return current.sheets().stream().filter(s -> s.name().equals(selected.getName())).findFirst().orElse(null);
    }

    /**
     * Échantillon de valeurs pour la colonne « Aperçu » de la liste quand l'aperçu complet est fermé. Il est
     * recalculé quand le modèle change (comparaison du JSON), au plus une fois par requête, et reste nul si le
     * modèle en cours n'est pas valide ou si le projet de référence manque.
     */
    private ExportEngine.Preview sample() {
        if (model == null || referenceProjectId == null || isNoSources()) {
            return null;
        }
        jakarta.faces.context.FacesContext faces = jakarta.faces.context.FacesContext.getCurrentInstance();
        if (faces != null && faces.getAttributes().containsKey(SAMPLE_DONE)) {
            return sample;
        }
        try {
            normalizeFieldKinds();
            ExportTemplateDefinition definition = ExportTemplateJson.parse(ExportTemplateJson.toJson(model.toDefinition()));
            String key = referenceProjectId + ExportTemplateJson.toJson(definition);
            if (!key.equals(sampleKey)) {
                sample = exportService.preview(userInfoUser(), definition, referenceProjectId, SAMPLE_ROWS);
                sampleKey = key;
            }
        } catch (RuntimeException e) {
            // Modèle en cours d'édition invalide, projet disparu, droit manquant : pas d'échantillon.
            log.debug("No sample for the column list: {}", e.getMessage());
            sample = null;
            sampleKey = null;
        }
        if (faces != null) {
            faces.getAttributes().put(SAMPLE_DONE, Boolean.TRUE);
        }
        return sample;
    }

    /** Position, dans les colonnes de la feuille, de la colonne sélectionnée (-1 si aucune), pour surligner l'aperçu. */
    public int getPreviewSelectedIndex() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null || selectedColumn == null ? -1 : sheet.getColumns().indexOf(selectedColumn);
    }

    public void openAddColumn() {
        if (published) {
            return;
        }
        addColumnOpen = true;
        newColumnName = "";
    }

    public void closeAddColumn() {
        addColumnOpen = false;
    }

    /** Ajoute une colonne sans mapping (nommée « colonne_n » si le nom est vide) et la sélectionne. */
    public void confirmAddColumn() {
        if (published) {
            return;
        }
        EditSheet sheet = requireSelectedSheet();
        EditColumn column = new EditColumn();
        String name = newColumnName == null ? "" : newColumnName.trim();
        column.setHeader(name.isEmpty() ? "colonne_" + (sheet.getColumns().size() + 1) : name);
        sheet.getColumns().add(column);
        selectColumn(column);
        addColumnOpen = false;
    }

    /** Le nom de la colonne sélectionnée a changé : l'ordre de tri de la feuille suit. */
    public void onColumnRenamed() {
        if (published) {
            return;
        }
        EditSheet sheet = getSelectedSheet();
        if (sheet != null && selectedColumn != null && selectedColumnHeader != null) {
            sheet.getSortBy().replaceAll(h -> h.equals(selectedColumnHeader) ? selectedColumn.getHeader() : h);
            selectedColumnHeader = selectedColumn.getHeader();
        }
    }

    // ------------------------------------------------------------------ mappings de la colonne sélectionnée

    public static final String TYPE_DIRECT = ExportTemplateEditModel.RULE_DIRECT;
    public static final String TYPE_CONSTANT = ExportTemplateEditModel.RULE_CONSTANT;
    public static final String TYPE_CONCAT = ExportTemplateEditModel.RULE_CONCAT;

    /** Ajoute un mapping à la colonne sélectionnée ; il vise d'abord les sources qui n'en ont pas encore. */
    public void addRuleToSelected() {
        if (published) {
            return;
        }
        EditRule rule = new EditRule();
        List<String> open = unassignedIndexes(selectedColumn);
        rule.setSources(open.size() == requireSelectedSheet().getSources().size() ? new ArrayList<>() : new ArrayList<>(open));
        selectedColumn.getRules().add(rule);
    }

    /** Ensemble « liste des champs + détail du mapping » : déplié par défaut, repliable. */
    private boolean paneOpen = true;

    public void togglePane() {
        paneOpen = !paneOpen;
    }

    /** Section « Mappings » du détail : dépliée par défaut, repliable. */
    private boolean mappingsOpen = true;

    public void toggleMappings() {
        mappingsOpen = !mappingsOpen;
    }

    public void setRuleType(EditRule rule, String type) {
        if (published) {
            return;
        }
        rule.setType(type);
    }

    public boolean ruleCovers(EditRule rule, int sourceIndex) {
        return rule.getSources().isEmpty() || rule.getSources().contains(String.valueOf(sourceIndex));
    }

    /** Cible ou retire une source pour un mapping (une règle sans source visait toutes les sources). */
    public void toggleRuleSource(EditRule rule, int sourceIndex) {
        if (published) {
            return;
        }
        List<String> now = new ArrayList<>();
        if (rule.getSources().isEmpty()) {
            for (int i = 0; i < requireSelectedSheet().getSources().size(); i++) {
                now.add(String.valueOf(i));
            }
        } else {
            now.addAll(rule.getSources());
        }
        String index = String.valueOf(sourceIndex);
        if (!now.remove(index)) {
            now.add(index);
        }
        now.sort(Comparator.comparingInt(Integer::parseInt));
        rule.setSources(now);
    }

    private List<String> unassignedIndexes(EditColumn column) {
        EditSheet sheet = getSelectedSheet();
        List<String> out = new ArrayList<>();
        for (int i = 0; sheet != null && i < sheet.getSources().size(); i++) {
            if (ruleAt(column, i).isEmpty()) {
                out.add(String.valueOf(i));
            }
        }
        return out;
    }

    /** Libellés des sources de la feuille sans mapping pour la colonne (vide si toutes en ont un). */
    public String unassignedSources(EditColumn column) {
        if (column == null || column.getRules().isEmpty()) {
            return "";
        }
        EditSheet sheet = getSelectedSheet();
        return unassignedIndexes(column).stream()
                .map(i -> sourceLabel(sheet.getSources().get(Integer.parseInt(i))))
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    /** Valeur d'exemple d'un mapping : celle de la première ligne de l'aperçu pour la colonne. */
    public String getRulePreview() {
        return selectedColumn == null ? "" : previewValue(selectedColumn);
    }

    private String describeRule(EditRule rule) {
        return switch (rule.getType()) {
            case ExportTemplateEditModel.RULE_CONSTANT -> "\"" + (rule.getConstantValue() == null ? "" : rule.getConstantValue()) + "\"";
            case ExportTemplateEditModel.RULE_CONCAT -> langBean.msg("exportTemplates.editor.cell.concat", rule.getParts().size());
            default -> choiceLabel(rule.getSources(), rule.getFieldChoice());
        };
    }

    /** Libellé d'une source : « Table · types » pour une entité, sinon le libellé de sa nature. */
    public String sourceLabel(EditSource source) {
        return describe(source);
    }

    /** Types d'une source, résumés pour le bandeau de sources ; vide s'il n'y a pas de filtre. */
    public String sourceTypes(EditSource source) {
        if (source.getTypeKeys().isEmpty()) {
            return "";
        }
        List<Item> items = typeItems(source);
        return source.getTypeKeys().stream()
                .map(k -> items.stream().filter(i -> i.value().equals(k)).map(Item::label).findFirst().orElse(k))
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    // ------------------------------------------------------------------ listes déroulantes

    public List<Item> getOutputItems() {
        return Arrays.stream(OutputType.values())
                .map(o -> new Item(langBean.msg("exportTemplates.editor.output." + o.name()), o.name()))
                .toList();
    }

    /** Entité d'une source ; vide si non choisie ou inconnue (une liste déroulante peut renvoyer une chaîne vide). */
    private static Optional<EntityKind> entityOf(EditSource source) {
        if (source.getEntity() == null || source.getEntity().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EntityKind.valueOf(source.getEntity()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String describe(EditSource source) {
        return switch (source.getKind()) {
            case ExportTemplateEditModel.KIND_ENTITY -> entityOf(source)
                    .map(k -> langBean.msg("exportTemplates.editor.entity." + k.name())).orElse("?");
            case ExportTemplateEditModel.KIND_TECHNICAL -> source.getTechnicalKey() == null ? "?"
                    : langBean.msg("exportTemplates.editor.technical." + source.getTechnicalKey());
            case ExportTemplateEditModel.KIND_TABLE -> source.getTableName();
            default -> langBean.msg("exportTemplates.editor.source.PROJECT");
        };
    }

    public List<Item> getColumnHeaderItems() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null ? List.of() : sheet.getColumns().stream().map(c -> new Item(c.getHeader(), c.getHeader())).toList();
    }

    /** Types (concepts) configurés pour le projet de référence, pour filtrer une source d'entités. */
    public List<Item> typeItems(EditSource source) {
        return entityOf(source).map(kind -> typeItemsFor(kind, source.getTypeKeys())).orElse(List.of());
    }

    /** Types d'une entité ; les clés de {@code extraKeys} que le projet de référence ne connaît pas sont ajoutées comme « non résolues ». */
    private List<Item> typeItemsFor(EntityKind kind, java.util.Collection<String> extraKeys) {
        ConfigurableTable table = ExportSubject.of(kind).table();
        if (typeCache == null) {
            clearCaches();
        }
        List<Item> items = new ArrayList<>(table == null || referenceProjectId == null ? List.of() : typeCache.computeIfAbsent(table, this::loadTypes));
        for (String key : extraKeys) {
            if (items.stream().noneMatch(i -> i.value().equals(key))) {
                items.add(new Item(langBean.msg("exportTemplates.editor.unresolved", key), key));
            }
        }
        return items;
    }

    private List<Item> loadTypes(ConfigurableTable table) {
        return tableFieldConfigService.listConfiguredTypeConcepts(referenceProjectId, table).stream()
                .filter(c -> c.getVocabulary() != null && c.getExternalId() != null)
                .map(c -> new Item(labelBean.findLabelOfConcept(c), ExportTemplateEditModel.keyOf(refOf(c))))
                .toList();
    }

    private static ConceptRef refOf(Concept c) {
        return new ConceptRef(c.getVocabulary().getExternalVocabularyId(), c.getExternalId(), c.getUri());
    }

    private List<FieldOption> fieldsOf(ExportSubject subject) {
        if (fieldCache == null) {
            clearCaches();
        }
        return fieldCache.computeIfAbsent(subject, s -> exportFieldResolver.listFields(s, referenceProjectId));
    }

    // ------------------------------------------------------------------ choix de champ (listes déroulantes groupées)

    /** Profondeur des navigations proposées depuis la source (ex. mobilier › unité d'enregistrement › projet). */
    private static final int NAVIGATION_DEPTH = 2;

    /** Champs que peut lire une règle, groupés par entité atteinte ; la valeur est {@code chemin::clé::propriété}. */
    public List<SelectItem> fieldChoices(EditRule rule) {
        return fieldChoices(rule.getSources(), rule.getFieldChoice());
    }

    /** Idem pour une partie de concaténation (même sources que sa règle). */
    public List<SelectItem> fieldChoices(EditRule rule, EditPart part) {
        return fieldChoices(rule.getSources(), part.getFieldChoice());
    }

    private List<SelectItem> fieldChoices(List<String> sourceChoices, String current) {
        List<SelectItem> out = new ArrayList<>();
        out.add(choice("", langBean.msg("exportTemplates.editor.choose")));
        Optional<ExportTechnicalSource> technical = technicalSourceOf(sourceChoices);
        if (technical.isPresent()) {
            SelectItem[] columns = technical.get().valueNames().stream().sorted()
                    .map(n -> choice(ExportTemplateEditModel.CHOICE_SEPARATOR + n + ExportTemplateEditModel.CHOICE_SEPARATOR, n))
                    .toArray(SelectItem[]::new);
            if (columns.length > 0) {
                out.add(new SelectItemGroup(langBean.msg("exportTemplates.editor.choices.columns"), "", false, columns));
            }
            technical.get().ends().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> addSubjectChoices(out, e.getValue(), List.of(e.getKey()), NAVIGATION_DEPTH - 1));
        } else {
            firstSource(sourceChoices).flatMap(ExportTemplateEditorBean::subjectOf)
                    .ifPresent(subject -> addSubjectChoices(out, subject, List.of(), NAVIGATION_DEPTH));
        }
        if (current != null && !current.isBlank() && !containsChoice(out, current)) {
            out.add(choice(current, langBean.msg("exportTemplates.editor.unresolved", current)));
        }
        return out;
    }

    private void addSubjectChoices(List<SelectItem> out, ExportSubject subject, List<String> path, int depth) {
        List<SelectItem> items = new ArrayList<>();
        String prefix = String.join(",", path);
        for (FieldOption o : fieldsOf(subject)) {
            String label = o.system() ? langBean.msg(o.label()) : o.label();
            String key = ExportTemplateEditModel.keyOf(o.concept());
            String sep = ExportTemplateEditModel.CHOICE_SEPARATOR;
            items.add(choice(prefix + sep + key + sep, label));
            if (o.measurement()) {
                for (String property : ExportTemplateDefinition.ConceptField.MEASUREMENT_PROPERTIES) {
                    items.add(choice(prefix + sep + key + sep + property,
                            label + " › " + langBean.msg("exportTemplates.editor.property." + property)));
                }
            }
        }
        if (!items.isEmpty()) {
            String label = path.isEmpty() ? langBean.msg("exportTemplates.editor.choices.own")
                    : String.join(" › ", path);
            out.add(new SelectItemGroup(label, "", false, items.toArray(SelectItem[]::new)));
        }
        if (depth > 0) {
            ExportNavigations.names(subject).stream().sorted().forEach(nav -> {
                List<String> next = new ArrayList<>(path);
                next.add(nav);
                addSubjectChoices(out, ExportNavigations.targetOf(subject, List.of(nav)), next, depth - 1);
            });
        }
    }

    private static SelectItem choice(String value, String label) {
        return new SelectItem(value, label);
    }

    private static boolean containsChoice(List<SelectItem> items, String value) {
        for (SelectItem item : items) {
            if (item instanceof SelectItemGroup group) {
                if (containsChoice(java.util.Arrays.asList(group.getSelectItems()), value)) {
                    return true;
                }
            } else if (value.equals(item.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Libellé lisible d'un choix de champ (chemin › champ › propriété), pour le résumé d'une ligne. */
    private String choiceLabel(List<String> sourceChoices, String choice) {
        if (choice == null || choice.isBlank()) {
            return "?";
        }
        for (SelectItem item : fieldChoices(sourceChoices, choice)) {
            Optional<String> label = labelOf(item, choice);
            if (label.isPresent()) {
                return label.get();
            }
        }
        return choice;
    }

    private Optional<String> labelOf(SelectItem item, String choice) {
        if (item instanceof SelectItemGroup group) {
            return groupedChoiceLabel(group, choice);
        }
        return choice.equals(item.getValue()) ? Optional.of(item.getLabel()) : Optional.empty();
    }

    private Optional<String> groupedChoiceLabel(SelectItemGroup group, String choice) {
        for (SelectItem inner : group.getSelectItems()) {
            if (choice.equals(inner.getValue())) {
                boolean own = group.getLabel().equals(langBean.msg("exportTemplates.editor.choices.own"));
                return Optional.of(own ? inner.getLabel() : group.getLabel() + " › " + inner.getLabel());
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ résolution du sujet d'une règle

    private List<Integer> sourceIndexes(List<String> choices, EditSheet sheet) {
        List<Integer> out = new ArrayList<>();
        if (sheet == null) {
            return out;
        }
        if (choices.isEmpty()) {
            for (int i = 0; i < sheet.getSources().size(); i++) out.add(i);
        } else {
            choices.stream().map(Integer::valueOf).filter(i -> i >= 0 && i < sheet.getSources().size()).sorted().forEach(out::add);
        }
        return out;
    }

    private Optional<EditSource> firstSource(List<String> choices) {
        EditSheet sheet = getSelectedSheet();
        List<Integer> indexes = sourceIndexes(choices, sheet);
        return sheet == null || indexes.isEmpty() ? Optional.empty() : Optional.of(sheet.getSources().get(indexes.get(0)));
    }

    private Optional<ExportTechnicalSource> technicalSourceOf(List<String> choices) {
        return firstSource(choices)
                .filter(s -> ExportTemplateEditModel.KIND_TECHNICAL.equals(s.getKind()))
                .flatMap(s -> ExportTechnicalSource.ofKey(s.getTechnicalKey()));
    }

    private static Optional<ExportSubject> subjectOf(EditSource source) {
        return switch (source.getKind()) {
            case ExportTemplateEditModel.KIND_ENTITY -> entityOf(source).map(ExportSubject::of);
            case ExportTemplateEditModel.KIND_PROJECT -> Optional.of(ExportSubject.PROJECT);
            default -> Optional.empty();
        };
    }

    // ------------------------------------------------------------------ enregistrement

    /** Fixe, pour chaque règle, si son champ est un concept ou une colonne de table technique. */
    void normalizeFieldKinds() {
        for (EditSheet sheet : model.getSheets()) {
            for (EditColumn column : sheet.getColumns()) {
                for (EditRule rule : column.getRules()) {
                    normalize(rule.getField(), rule.getSources(), rule.getPath(), sheet);
                    rule.getParts().stream().filter(p -> !p.isLiteralPart())
                            .forEach(p -> normalize(p.getField(), rule.getSources(), p.getPath(), sheet));
                }
            }
        }
    }

    private void normalize(EditField field, List<String> sourceChoices, String path, EditSheet sheet) {
        if (ExportTemplateEditModel.FIELD_NAME.equals(field.getKind())) {
            return; // champ nommé du cœur commun : conservé tel quel
        }
        List<Integer> indexes = sourceIndexes(sourceChoices, sheet);
        boolean technicalColumn = sheet != null && !indexes.isEmpty()
                && ExportTemplateEditModel.KIND_TECHNICAL.equals(sheet.getSources().get(indexes.get(0)).getKind())
                && ExportTemplateEditModel.pathOf(path).isEmpty();
        field.setKind(technicalColumn ? ExportTemplateEditModel.FIELD_COLUMN : ExportTemplateEditModel.FIELD_CONCEPT);
    }

    public void save() {
        if (published) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.published");
            return;
        }
        persist(true);
    }

    /** Enregistre le brouillon, puis le publie : le modèle devient en lecture seule et ne reste que duplicable. */
    public void publish() {
        if (published || !persist(false)) {
            return;
        }
        try {
            exportTemplateService.publish(userInfoUser(), templateId);
            published = true;
            sourceDialog.reset();
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.editor.publishedDone");
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        }
    }

    /** Copie modifiable d'un modèle publié, nommée « (copie) », ouverte aussitôt. */
    public String duplicateTemplate() {
        try {
            ExportTemplateDTO copy = exportTemplateService.duplicate(userInfoUser(), templateId,
                    langBean.msg("exportTemplates.copyName", templateName));
            load(copy.id());
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.duplicated", templateName);
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        } catch (NoSuchElementException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.notFound");
        }
        return null;
    }

    /** @return vrai si le modèle a été enregistré */
    private boolean persist(boolean notify) {
        if (model == null) {
            return false;
        }
        normalizeFieldKinds();
        ExportTemplateDefinition definition;
        try {
            definition = model.toDefinition();
        } catch (IllegalArgumentException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_INVALID, e.getMessage());
            return false;
        }
        try {
            // Structure d'abord (codec), puis cohérence avec les registres du code.
            ExportTemplateDefinition checked = fr.siamois.domain.models.exporttemplate.ExportTemplateJson.parse(
                    fr.siamois.domain.models.exporttemplate.ExportTemplateJson.toJson(definition));
            List<String> problems = ExportTemplateChecker.check(checked);
            if (!problems.isEmpty()) {
                problems.forEach(p -> MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.problem", p));
                return false;
            }
            ExportTemplateDTO saved = exportTemplateService.updateDefinition(userInfoUser(), templateId, checked);
            templateName = saved.name();
            model = ExportTemplateEditModel.from(exportTemplateService.getDefinition(userInfoUser(), templateId));
            selectedColumn = null;
            sourceDialog.reset();
            if (notify) {
                MessageUtils.displayInfoMessage(langBean, "exportTemplates.editor.saved");
            }
            return true;
        } catch (InvalidExportTemplateException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_INVALID, e.getMessage());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, ERROR_FORBIDDEN);
        }
        return false;
    }

    /** Abandonne les modifications non enregistrées. */
    public void reload() {
        load(templateId);
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        templateId = null;
        templateName = null;
        model = null;
        selectedColumn = null;
        selectedSheetIndex = 0;
        sourceDialog.reset();
        closePreview();
        referenceProjectId = null;
        projectItems = new ArrayList<>();
        clearCaches();
    }
}
