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
    private List<Item> projectItems = new ArrayList<>();

    private transient Map<ExportSubject, List<FieldOption>> fieldCache = new EnumMap<>(ExportSubject.class);
    private transient Map<ConfigurableTable, List<Item>> typeCache = new EnumMap<>(ConfigurableTable.class);

    // ------------------------------------------------------------------ ouverture

    /** Ouvre l'éditeur sur un modèle de l'institution ; retourne la navigation vers la page. */
    public String open(Long id) {
        if (!canManage()) {
            MessageUtils.displayErrorMessage(langBean, "common.error.forbidden");
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
        model = ExportTemplateEditModel.from(exportTemplateService.getDefinition(userInfoUser(), id));
        selectedSheetIndex = 0;
        selectedColumn = null;
        closeDrawer();
        modelMenuOpen = false;
        sheetMenuOpen = false;
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
            MessageUtils.displayErrorMessage(langBean, "common.error.forbidden");
        }
        clearCaches();
    }

    private void clearCaches() {
        fieldCache = new EnumMap<>(ExportSubject.class);
        typeCache = new EnumMap<>(ConfigurableTable.class);
    }

    // ------------------------------------------------------------------ feuilles

    public EditSheet getSelectedSheet() {
        if (model == null || model.getSheets().isEmpty()) {
            return null;
        }
        selectedSheetIndex = Math.max(0, Math.min(selectedSheetIndex, model.getSheets().size() - 1));
        return model.getSheets().get(selectedSheetIndex);
    }

    public void selectSheet(EditSheet sheet) {
        selectedSheetIndex = model.getSheets().indexOf(sheet);
        selectedColumn = null;
        closeDrawer();
    }

    public boolean isSelected(EditSheet sheet) {
        return sheet == getSelectedSheet();
    }

    public void addSheet() {
        EditSheet sheet = new EditSheet();
        sheet.setName(langBean.msg("exportTemplates.blank.sheetName"));
        sheet.getSources().add(new EditSource());
        EditColumn first = new EditColumn();
        first.setHeader("colonne_1");
        sheet.getColumns().add(first);
        model.getSheets().add(sheet);
        selectedSheetIndex = model.getSheets().size() - 1;
        selectedColumn = null;
    }

    public void removeSheet(EditSheet sheet) {
        model.getSheets().remove(sheet);
        selectedColumn = null;
    }

    public void moveSheet(EditSheet sheet, int delta) {
        move(model.getSheets(), sheet, delta);
    }

    // ------------------------------------------------------------------ sources

    public void addSource() {
        getSelectedSheet().getSources().add(new EditSource());
    }

    /** Retire une source et recale les indices de source des règles ; une règle qui ne visait que elle disparaît. */
    public void removeSource(EditSource source) {
        EditSheet sheet = getSelectedSheet();
        int removed = sheet.getSources().indexOf(source);
        if (removed < 0) {
            return;
        }
        sheet.getSources().remove(removed);
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
        EditSheet sheet = getSelectedSheet();
        sheet.getColumns().remove(column);
        sheet.getSortBy().remove(column.getHeader());
        if (selectedColumn == column) {
            selectedColumn = null;
        }
    }

    public void moveColumn(EditColumn column, int delta) {
        move(getSelectedSheet().getColumns(), column, delta);
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
        selectedColumn.getRules().remove(rule);
    }

    public void addPart(EditRule rule) {
        rule.getParts().add(new EditPart());
    }

    public void addLiteralPart(EditRule rule) {
        EditPart part = new EditPart();
        part.setLiteralPart(true);
        part.setLiteral("");
        rule.getParts().add(part);
    }

    public void removePart(EditRule rule, EditPart part) {
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
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.invalid", e.getMessage());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, "common.error.forbidden");
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

    public static final String DRAWER_SOURCE = "SOURCE";

    /** Fenêtre d'édition d'une source ; {@code null} = fermée. */
    private String drawerMode;
    private EditSource drawerSource;
    private int drawerSourceIndex;

    /** Menus déroulants « Réglages du modèle » et « Feuille » ; le nom, la version et le patron sont gardés pour « Annuler ». */
    private boolean modelMenuOpen;
    private boolean sheetMenuOpen;
    private String modelNameBefore;
    private String modelVersionBefore;
    private String modelPatternBefore;

    public boolean isDrawerSourceMode() {
        return DRAWER_SOURCE.equals(drawerMode);
    }

    public void closeDrawer() {
        drawerMode = null;
        drawerSource = null;
    }

    public void toggleModelMenu() {
        if (modelMenuOpen) {
            modelMenuOpen = false;
            return;
        }
        sheetMenuOpen = false;
        modelMenuOpen = true;
        modelNameBefore = model.getName();
        modelVersionBefore = model.getVersion();
        modelPatternBefore = model.getFileNamePattern();
    }

    /** « Annuler » : remet le nom, la version et le patron tels qu'à l'ouverture du menu. */
    public void cancelModelMenu() {
        model.setName(modelNameBefore);
        model.setVersion(modelVersionBefore);
        model.setFileNamePattern(modelPatternBefore);
        modelMenuOpen = false;
    }

    public void closeModelMenu() {
        modelMenuOpen = false;
    }

    public void toggleSheetMenu() {
        modelMenuOpen = false;
        sheetMenuOpen = !sheetMenuOpen;
    }

    public void closeSheetMenu() {
        sheetMenuOpen = false;
    }

    /** Première colonne de tri de la feuille ({@code ""} = aucun tri). */
    public String getSortColumn() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null || sheet.getSortBy().isEmpty() ? "" : sheet.getSortBy().get(0);
    }

    public void setSortColumn(String header) {
        EditSheet sheet = getSelectedSheet();
        sheet.getSortBy().clear();
        if (header != null && !header.isBlank()) {
            sheet.getSortBy().add(header);
        }
    }

    public void duplicateSheet() {
        EditSheet copy = ExportTemplateEditModel.copyOf(getSelectedSheet());
        copy.setName(copy.getName() + langBean.msg("exportTemplates.editor.sheet.copySuffix"));
        model.getSheets().add(model.getSheets().indexOf(getSelectedSheet()) + 1, copy);
        selectedSheetIndex = model.getSheets().indexOf(copy);
        selectedColumn = null;
        sheetMenuOpen = false;
    }

    public void openSource(EditSource source) {
        closeDrawer();
        drawerMode = DRAWER_SOURCE;
        drawerSource = source;
        drawerSourceIndex = getSelectedSheet().getSources().indexOf(source);
    }

    /** Ajoute une source à la feuille et ouvre directement sa fenêtre pour la renseigner. */
    public void addSourceAndEdit() {
        addSource();
        List<EditSource> sources = getSelectedSheet().getSources();
        openSource(sources.get(sources.size() - 1));
    }

    public String getDrawerTitle() {
        return DRAWER_SOURCE.equals(drawerMode)
                ? langBean.msg("exportTemplates.editor.source.title", drawerSourceIndex + 1) : "";
    }

    public void removeDrawerSource() {
        removeSource(drawerSource);
        closeDrawer();
    }

    public void removeSelectedSheet() {
        removeSheet(getSelectedSheet());
        sheetMenuOpen = false;
    }

    // ------------------------------------------------------------------ liste des colonnes

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_MAPPED = "MAPPED";
    public static final String FILTER_CHECK = "CHECK";

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
        String needle = columnSearch == null ? "" : columnSearch.trim().toLowerCase();
        return sheet.getColumns().stream()
                .filter(c -> needle.isEmpty() || (c.getHeader() != null && c.getHeader().toLowerCase().contains(needle)))
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
        return needsCheck(column) ? "CHECK" : "OK";
    }

    /** Texte de mapping d'une ligne : le champ, ou le nombre de mappings s'il y en a plusieurs ; vide si non mappée. */
    public String mappingText(EditColumn column) {
        if (column.getRules().size() > 1) {
            return langBean.msg("exportTemplates.editor.mappings.count", column.getRules().size());
        }
        return column.getRules().isEmpty() ? "" : describeRule(column.getRules().get(0));
    }

    /** Première valeur de l'aperçu pour la colonne ; vide si l'aperçu n'est pas ouvert. */
    public String previewValue(EditColumn column) {
        ExportEngine.SheetPreview sp = getPreviewOfSelectedSheet();
        EditSheet sheet = getSelectedSheet();
        if (sp == null || sheet == null || sp.rows().isEmpty()) {
            return "";
        }
        int at = sheet.getColumns().indexOf(column);
        List<String> row = sp.rows().get(0);
        return at >= 0 && at < row.size() && row.get(at) != null ? row.get(at) : "";
    }

    /** Position, dans les colonnes de la feuille, de la colonne sélectionnée (-1 si aucune), pour surligner l'aperçu. */
    public int getPreviewSelectedIndex() {
        EditSheet sheet = getSelectedSheet();
        return sheet == null || selectedColumn == null ? -1 : sheet.getColumns().indexOf(selectedColumn);
    }

    public void openAddColumn() {
        addColumnOpen = true;
        newColumnName = "";
    }

    public void closeAddColumn() {
        addColumnOpen = false;
    }

    /** Ajoute une colonne sans mapping (nommée « colonne_n » si le nom est vide) et la sélectionne. */
    public void confirmAddColumn() {
        EditSheet sheet = getSelectedSheet();
        EditColumn column = new EditColumn();
        String name = newColumnName == null ? "" : newColumnName.trim();
        column.setHeader(name.isEmpty() ? "colonne_" + (sheet.getColumns().size() + 1) : name);
        sheet.getColumns().add(column);
        selectColumn(column);
        addColumnOpen = false;
    }

    /** Le nom de la colonne sélectionnée a changé : l'ordre de tri de la feuille suit. */
    public void onColumnRenamed() {
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
        EditRule rule = new EditRule();
        List<String> open = unassignedIndexes(selectedColumn);
        rule.setSources(open.size() == getSelectedSheet().getSources().size() ? new ArrayList<>() : new ArrayList<>(open));
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
        rule.setType(type);
    }

    public boolean ruleCovers(EditRule rule, int sourceIndex) {
        return rule.getSources().isEmpty() || rule.getSources().contains(String.valueOf(sourceIndex));
    }

    /** Cible ou retire une source pour un mapping (une règle sans source visait toutes les sources). */
    public void toggleRuleSource(EditRule rule, int sourceIndex) {
        List<String> now = new ArrayList<>();
        if (rule.getSources().isEmpty()) {
            for (int i = 0; i < getSelectedSheet().getSources().size(); i++) {
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
    public String rulePreview(EditRule rule) {
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

    public List<Item> getEntityItems() {
        return Arrays.stream(EntityKind.values())
                .map(k -> new Item(langBean.msg("exportTemplates.editor.entity." + k.name()), k.name()))
                .toList();
    }

    public List<Item> getTechnicalItems() {
        return Arrays.stream(ExportTechnicalSource.values())
                .map(k -> new Item(langBean.msg("exportTemplates.editor.technical." + k.name()), k.name()))
                .toList();
    }

    public List<Item> getOutputItems() {
        return Arrays.stream(OutputType.values())
                .map(o -> new Item(langBean.msg("exportTemplates.editor.output." + o.name()), o.name()))
                .toList();
    }

    public List<Item> getSourceKindItems() {
        return List.of(
                new Item(langBean.msg("exportTemplates.editor.source.ENTITY"), ExportTemplateEditModel.KIND_ENTITY),
                new Item(langBean.msg("exportTemplates.editor.source.PROJECT"), ExportTemplateEditModel.KIND_PROJECT),
                new Item(langBean.msg("exportTemplates.editor.source.TECHNICAL"), ExportTemplateEditModel.KIND_TECHNICAL));
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
        Optional<EntityKind> kind = entityOf(source);
        if (kind.isEmpty()) {
            return List.of();
        }
        ConfigurableTable table = ExportSubject.of(kind.get()).table();
        if (typeCache == null) {
            clearCaches();
        }
        List<Item> items = new ArrayList<>(table == null || referenceProjectId == null ? List.of() : typeCache.computeIfAbsent(table, this::loadTypes));
        for (String key : source.getTypeKeys()) {
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
        List<SelectItem> items = fieldChoices(sourceChoices, choice);
        for (SelectItem item : items) {
            if (item instanceof SelectItemGroup group) {
                for (SelectItem inner : group.getSelectItems()) {
                    if (choice.equals(inner.getValue())) {
                        return group.getLabel().equals(langBean.msg("exportTemplates.editor.choices.own"))
                                ? inner.getLabel() : group.getLabel() + " › " + inner.getLabel();
                    }
                }
            } else if (choice.equals(item.getValue())) {
                return item.getLabel();
            }
        }
        return choice;
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
        return indexes.isEmpty() ? Optional.empty() : Optional.of(sheet.getSources().get(indexes.get(0)));
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

    /** Entité que lit une règle après son chemin ; vide si le chemin est invalide ou la source incomplète. */
    private Optional<ExportSubject> targetSubject(List<String> sourceChoices, List<String> steps) {
        Optional<EditSource> source = firstSource(sourceChoices);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (ExportTemplateEditModel.KIND_TECHNICAL.equals(source.get().getKind())) {
                return ExportTechnicalSource.ofKey(source.get().getTechnicalKey())
                        .map(t -> ExportNavigations.targetOf(t, steps));
            }
            return subjectOf(source.get()).map(s -> ExportNavigations.targetOf(s, steps));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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
        boolean technicalColumn = !indexes.isEmpty()
                && ExportTemplateEditModel.KIND_TECHNICAL.equals(sheet.getSources().get(indexes.get(0)).getKind())
                && ExportTemplateEditModel.pathOf(path).isEmpty();
        field.setKind(technicalColumn ? ExportTemplateEditModel.FIELD_COLUMN : ExportTemplateEditModel.FIELD_CONCEPT);
    }

    public void save() {
        if (model == null) {
            return;
        }
        normalizeFieldKinds();
        ExportTemplateDefinition definition;
        try {
            definition = model.toDefinition();
        } catch (IllegalArgumentException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.invalid", e.getMessage());
            return;
        }
        try {
            // Structure d'abord (codec), puis cohérence avec les registres du code.
            ExportTemplateDefinition checked = fr.siamois.domain.models.exporttemplate.ExportTemplateJson.parse(
                    fr.siamois.domain.models.exporttemplate.ExportTemplateJson.toJson(definition));
            List<String> problems = ExportTemplateChecker.check(checked);
            if (!problems.isEmpty()) {
                problems.forEach(p -> MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.problem", p));
                return;
            }
            ExportTemplateDTO saved = exportTemplateService.updateDefinition(userInfoUser(), templateId, checked);
            templateName = saved.name();
            model = ExportTemplateEditModel.from(exportTemplateService.getDefinition(userInfoUser(), templateId));
            selectedColumn = null;
            closeDrawer();
            MessageUtils.displayInfoMessage(langBean, "exportTemplates.editor.saved");
        } catch (InvalidExportTemplateException e) {
            MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.invalid", e.getMessage());
        } catch (ForbiddenOperationException e) {
            MessageUtils.displayErrorMessage(langBean, "common.error.forbidden");
        }
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
        closeDrawer();
        closePreview();
        referenceProjectId = null;
        projectItems = new ArrayList<>();
        clearCaches();
    }
}
