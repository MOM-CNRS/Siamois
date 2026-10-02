package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntityKind;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.OutputType;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver.FieldOption;
import fr.siamois.domain.services.exporttemplate.ExportNavigations;
import fr.siamois.domain.services.exporttemplate.ExportTemplateChecker;
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

    /** JSON partageable du modèle tel qu'enregistré, pour l'aperçu du tiroir « Propriétés ». */
    public String getSavedJson() {
        return templateId == null ? "" : exportTemplateService.exportJson(userInfoUser(), templateId);
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
        sheet.getColumns().add(newColumn(1));
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

    private static EditColumn newColumn(int number) {
        EditColumn column = new EditColumn();
        column.setHeader("colonne_" + number);
        column.getRules().add(newRule());
        return column;
    }

    private static EditRule newRule() {
        EditRule rule = new EditRule();
        rule.setType(ExportTemplateEditModel.RULE_CONSTANT);
        return rule;
    }

    public void addColumn() {
        EditSheet sheet = getSelectedSheet();
        EditColumn column = newColumn(sheet.getColumns().size() + 1);
        sheet.getColumns().add(column);
        selectedColumn = column;
    }

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
    }

    public boolean isColumnSelected(EditColumn column) {
        return column == selectedColumn;
    }

    // ------------------------------------------------------------------ règles

    public void addRule() {
        selectedColumn.getRules().add(newRule());
    }

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

    // ------------------------------------------------------------------ matrice et tiroir

    public static final String DRAWER_RULE = "RULE";
    public static final String DRAWER_COLUMN = "COLUMN";
    public static final String DRAWER_SOURCE = "SOURCE";
    public static final String DRAWER_SHEET = "SHEET";
    public static final String DRAWER_MODEL = "MODEL";
    public static final String TAB_DIRECT = "DIRECT";
    public static final String TAB_PATH = "PATH";
    public static final String TAB_CONSTANT = ExportTemplateEditModel.RULE_CONSTANT;
    public static final String TAB_CONCAT = ExportTemplateEditModel.RULE_CONCAT;

    /** Mode du tiroir latéral ; {@code null} = fermé. */
    private String drawerMode;
    private EditColumn drawerColumn;
    private EditSource drawerSource;
    private int drawerSourceIndex;
    /** Règle en cours d'édition (copie de travail) ; {@code drawerOriginal} est la règle d'origine, nulle pour une nouvelle. */
    private EditRule drawerRule;
    private EditRule drawerOriginal;
    /** Onglet « Chemin » choisi alors que le chemin est encore vide (il ne se déduit pas de la règle). */
    private boolean drawerPathTab;

    public boolean isDrawerOpen() {
        return drawerMode != null;
    }

    public boolean isDrawerRuleMode() {
        return DRAWER_RULE.equals(drawerMode);
    }

    public boolean isDrawerColumnMode() {
        return DRAWER_COLUMN.equals(drawerMode);
    }

    public boolean isDrawerSourceMode() {
        return DRAWER_SOURCE.equals(drawerMode);
    }

    public boolean isDrawerSheetMode() {
        return DRAWER_SHEET.equals(drawerMode);
    }

    public boolean isDrawerModelMode() {
        return DRAWER_MODEL.equals(drawerMode);
    }

    public void closeDrawer() {
        drawerMode = null;
        drawerColumn = null;
        drawerSource = null;
        drawerRule = null;
        drawerOriginal = null;
        drawerPathTab = false;
    }

    /** Règle qui produit la valeur de la colonne pour la source donnée (une règle sans source vaut pour toutes). */
    public Optional<EditRule> ruleAt(EditColumn column, int sourceIndex) {
        String index = String.valueOf(sourceIndex);
        return column.getRules().stream()
                .filter(r -> r.getSources().isEmpty() || r.getSources().contains(index))
                .findFirst();
    }

    /** Genre de la cellule : DIRECT, PATH, CONSTANT, CONCAT, ou chaîne vide si non mappée. */
    public String cellKind(EditColumn column, int sourceIndex) {
        return ruleAt(column, sourceIndex).map(ExportTemplateEditorBean::tabOf).orElse("");
    }

    private static String tabOf(EditRule rule) {
        if (ExportTemplateEditModel.RULE_DIRECT.equals(rule.getType())) {
            return ExportTemplateEditModel.pathOf(rule.getPath()).isEmpty() ? TAB_DIRECT : TAB_PATH;
        }
        return rule.getType();
    }

    /** Texte de la cellule : champ (précédé de son chemin), constante entre guillemets, ou résumé de concaténation. */
    public String cellValue(EditColumn column, int sourceIndex) {
        return ruleAt(column, sourceIndex).map(this::describeRule).orElse("");
    }

    private String describeRule(EditRule rule) {
        return switch (rule.getType()) {
            case ExportTemplateEditModel.RULE_CONSTANT -> "\"" + (rule.getConstantValue() == null ? "" : rule.getConstantValue()) + "\"";
            case ExportTemplateEditModel.RULE_CONCAT -> langBean.msg("exportTemplates.editor.cell.concat", rule.getParts().size());
            default -> {
                String field = fieldLabel(rule);
                String path = String.join(".", ExportTemplateEditModel.pathOf(rule.getPath()));
                yield path.isEmpty() ? field : path + "." + field;
            }
        };
    }

    private String fieldLabel(EditRule rule) {
        String key = rule.getField().getKey();
        if (key == null || key.isBlank()) {
            return "?";
        }
        return fieldItems(rule).stream().filter(i -> i.value().equals(key)).map(Item::label).findFirst().orElse(key);
    }

    /** Nombre de sources de la feuille que cible la règle du tiroir (pour avertir qu'elle est partagée). */
    public int getDrawerRuleSourceCount() {
        EditSheet sheet = getSelectedSheet();
        if (drawerRule == null || sheet == null) {
            return 0;
        }
        return drawerRule.getSources().isEmpty() ? sheet.getSources().size() : drawerRule.getSources().size();
    }

    public void openCell(EditColumn column, int sourceIndex) {
        closeDrawer();
        drawerMode = DRAWER_RULE;
        drawerColumn = column;
        drawerSourceIndex = sourceIndex;
        drawerOriginal = ruleAt(column, sourceIndex).orElse(null);
        if (drawerOriginal != null) {
            drawerRule = ExportTemplateEditModel.copyOf(drawerOriginal);
        } else {
            drawerRule = newRule();
            drawerRule.setType(ExportTemplateEditModel.RULE_DIRECT);
            drawerRule.getSources().add(String.valueOf(sourceIndex));
        }
    }

    public void openColumn(EditColumn column) {
        closeDrawer();
        drawerMode = DRAWER_COLUMN;
        drawerColumn = column;
    }

    public void openSource(EditSource source) {
        closeDrawer();
        drawerMode = DRAWER_SOURCE;
        drawerSource = source;
        drawerSourceIndex = getSelectedSheet().getSources().indexOf(source);
    }

    /** Ajoute une source à la feuille et ouvre directement son tiroir pour la renseigner. */
    public void addSourceAndEdit() {
        addSource();
        List<EditSource> sources = getSelectedSheet().getSources();
        openSource(sources.get(sources.size() - 1));
    }

    /** Choisit le champ de la règle en cours d'édition (clé « thesaurus|id|uri », ou nom de colonne technique). */
    public void selectDrawerField(String key) {
        if (drawerRule != null) {
            drawerRule.getField().setKey(key);
        }
    }

    public void openSheet() {
        closeDrawer();
        drawerMode = DRAWER_SHEET;
    }

    public void openModel() {
        closeDrawer();
        drawerMode = DRAWER_MODEL;
    }

    /** Onglet actif du tiroir de règle. */
    public String getDrawerTab() {
        if (drawerRule == null) {
            return TAB_DIRECT;
        }
        String tab = tabOf(drawerRule);
        return drawerPathTab && TAB_DIRECT.equals(tab) ? TAB_PATH : tab;
    }

    public void selectDrawerTab(String tab) {
        if (drawerRule == null) {
            return;
        }
        switch (tab) {
            case TAB_CONSTANT -> drawerRule.setType(ExportTemplateEditModel.RULE_CONSTANT);
            case TAB_CONCAT -> drawerRule.setType(ExportTemplateEditModel.RULE_CONCAT);
            case TAB_PATH -> drawerRule.setType(ExportTemplateEditModel.RULE_DIRECT);
            default -> {
                drawerRule.setType(ExportTemplateEditModel.RULE_DIRECT);
                drawerRule.setPath("");
            }
        }
        drawerPathTab = TAB_PATH.equals(tab);
    }

    /** Intitulé de la cellule éditée : « colonne » (titre) et « source » (sous-titre). */
    public String getDrawerTitle() {
        return switch (drawerMode == null ? "" : drawerMode) {
            case DRAWER_RULE, DRAWER_COLUMN -> drawerColumn.getHeader();
            case DRAWER_SOURCE -> langBean.msg("exportTemplates.editor.source.title", drawerSourceIndex + 1);
            case DRAWER_SHEET -> getSelectedSheet().getName();
            case DRAWER_MODEL -> model.getName();
            default -> "";
        };
    }

    public String getDrawerSubtitle() {
        EditSheet sheet = getSelectedSheet();
        if (DRAWER_RULE.equals(drawerMode) && sheet != null && drawerSourceIndex < sheet.getSources().size()) {
            return sourceLabel(sheet.getSources().get(drawerSourceIndex));
        }
        return "";
    }

    /** Valide la règle du tiroir : remplace la règle d'origine, ou ajoute la nouvelle si elle désigne bien quelque chose. */
    public void applyDrawer() {
        if (DRAWER_RULE.equals(drawerMode) && drawerRule != null) {
            if (drawerOriginal != null) {
                int at = drawerColumn.getRules().indexOf(drawerOriginal);
                drawerColumn.getRules().set(at, drawerRule);
            } else if (isMeaningful(drawerRule)) {
                drawerColumn.getRules().add(drawerRule);
            }
        }
        closeDrawer();
    }

    private static boolean isMeaningful(EditRule rule) {
        return switch (rule.getType()) {
            case ExportTemplateEditModel.RULE_CONCAT -> !rule.getParts().isEmpty();
            case ExportTemplateEditModel.RULE_CONSTANT -> true;
            default -> rule.getField().getKey() != null && !rule.getField().getKey().isBlank();
        };
    }

    /** Retire le mapping de la cellule ; une règle partagée avec d'autres sources les garde. */
    public void clearCell() {
        if (DRAWER_RULE.equals(drawerMode) && drawerOriginal != null) {
            EditSheet sheet = getSelectedSheet();
            if (drawerOriginal.getSources().isEmpty() || drawerOriginal.getSources().size() > 1) {
                List<String> remaining = new ArrayList<>();
                if (drawerOriginal.getSources().isEmpty()) {
                    for (int i = 0; i < sheet.getSources().size(); i++) {
                        remaining.add(String.valueOf(i));
                    }
                } else {
                    remaining.addAll(drawerOriginal.getSources());
                }
                remaining.remove(String.valueOf(drawerSourceIndex));
                drawerOriginal.setSources(remaining);
            } else {
                drawerColumn.getRules().remove(drawerOriginal);
            }
        }
        closeDrawer();
    }

    public void removeDrawerColumn() {
        removeColumn(drawerColumn);
        closeDrawer();
    }

    public void removeDrawerSource() {
        removeSource(drawerSource);
        closeDrawer();
    }

    public void removeSelectedSheet() {
        removeSheet(getSelectedSheet());
        closeDrawer();
    }

    public void moveDrawerColumn(int delta) {
        moveColumn(drawerColumn, delta);
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

    /** Couleur du point d'une source (palette du design : contexte, tertiaire, primaire…). */
    public String sourceDot(int index) {
        return switch (index % 3) {
            case 0 -> "context";
            case 1 -> "third";
            default -> "primary";
        };
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

    public List<Item> getRuleTypeItems() {
        return List.of(
                new Item(langBean.msg("exportTemplates.editor.rule.DIRECT"), ExportTemplateEditModel.RULE_DIRECT),
                new Item(langBean.msg("exportTemplates.editor.rule.CONSTANT"), ExportTemplateEditModel.RULE_CONSTANT),
                new Item(langBean.msg("exportTemplates.editor.rule.CONCAT"), ExportTemplateEditModel.RULE_CONCAT));
    }

    public List<Item> getSourceKindItems() {
        return List.of(
                new Item(langBean.msg("exportTemplates.editor.source.ENTITY"), ExportTemplateEditModel.KIND_ENTITY),
                new Item(langBean.msg("exportTemplates.editor.source.PROJECT"), ExportTemplateEditModel.KIND_PROJECT),
                new Item(langBean.msg("exportTemplates.editor.source.TECHNICAL"), ExportTemplateEditModel.KIND_TECHNICAL));
    }

    /** Sources de la feuille courante, pour cibler une règle. */
    public List<Item> getSourceChoiceItems() {
        EditSheet sheet = getSelectedSheet();
        if (sheet == null) {
            return List.of();
        }
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < sheet.getSources().size(); i++) {
            items.add(new Item(langBean.msg("exportTemplates.editor.source.choice", i + 1, describe(sheet.getSources().get(i))), String.valueOf(i)));
        }
        return items;
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

    /** Champs proposables pour une règle directe, selon ses sources et son chemin. */
    public List<Item> fieldItems(EditRule rule) {
        return fieldItems(rule.getSources(), rule.getPath(), rule.getField());
    }

    /** Champs proposables pour une partie de concaténation de la règle donnée. */
    public List<Item> fieldItems(EditRule rule, EditPart part) {
        return fieldItems(rule.getSources(), part.getPath(), part.getField());
    }

    private List<Item> fieldItems(List<String> sourceChoices, String path, EditField current) {
        List<Item> items = new ArrayList<>();
        Optional<ExportTechnicalSource> technical = technicalSourceOf(sourceChoices);
        List<String> steps = ExportTemplateEditModel.pathOf(path);
        if (technical.isPresent() && steps.isEmpty()) {
            technical.get().valueNames().stream().sorted().forEach(n -> items.add(new Item(n, n)));
        } else {
            targetSubject(sourceChoices, steps).ifPresent(subject -> fieldsOf(subject).forEach(o -> items.add(new Item(
                    o.system() ? langBean.msg(o.label()) : o.label(), ExportTemplateEditModel.keyOf(o.concept())))));
        }
        if (current != null && current.getKey() != null && !current.getKey().isBlank()
                && items.stream().noneMatch(i -> i.value().equals(current.getKey()))) {
            items.add(new Item(langBean.msg("exportTemplates.editor.unresolved", current.getKey()), current.getKey()));
        }
        return items;
    }

    private List<FieldOption> fieldsOf(ExportSubject subject) {
        if (fieldCache == null) {
            clearCaches();
        }
        return fieldCache.computeIfAbsent(subject, s -> exportFieldResolver.listFields(s, referenceProjectId));
    }

    /** Navigations disponibles depuis la source d'une règle, pour aider à saisir un chemin. */
    public String pathHint(EditRule rule) {
        EditSheet sheet = getSelectedSheet();
        List<Integer> indexes = sourceIndexes(rule.getSources(), sheet);
        if (indexes.isEmpty()) {
            return "";
        }
        EditSource source = sheet.getSources().get(indexes.get(0));
        if (ExportTemplateEditModel.KIND_TECHNICAL.equals(source.getKind())) {
            return ExportTechnicalSource.ofKey(source.getTechnicalKey())
                    .map(t -> String.join(", ", t.ends().keySet().stream().sorted().toList())).orElse("");
        }
        return subjectOf(source).map(s -> String.join(", ", ExportNavigations.names(s).stream().sorted().toList())).orElse("");
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
        referenceProjectId = null;
        projectItems = new ArrayList<>();
        clearCaches();
    }
}
