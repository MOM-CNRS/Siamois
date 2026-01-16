package fr.siamois.ui.table;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FieldSource;
import fr.siamois.ui.form.TableRowFieldSource;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.BaseTreeTableLazyModel;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.component.api.UIColumn;
import org.primefaces.event.ColumnToggleEvent;
import org.primefaces.model.TreeNode;
import org.primefaces.model.Visibility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static fr.siamois.ui.bean.dialog.newunit.NewUnitContext.TreeInsert.ROOT;

/**
 * View model générique pour une table d'entités avec formulaires dynamiques.
 *
 * - tient une TableDefinition (colonnes)
 * - s'appuie sur un BaseLazyDataModel<T> "pur data"
 * - crée un EntityFormContext<T> par ligne :
 *      - réponses
 *      - règles d'activation
 *      - champs système (min/max, etc.)
 *
 * Les sous-classes doivent définir :
 *  - resolveRowFormFor(T entity)
 *  - configureRowSystemFields(T entity, CustomForm form)
 */
@Getter
public abstract class EntityTableViewModel<T extends TraceableEntity, ID> {

    @Setter
    protected String globalFilter;

    /** Lazy model "pur data" (chargement, tri, filtres, sélection, etc.) */
    protected final BaseLazyDataModel<T> lazyDataModel;
    protected final BaseTreeTableLazyModel<T, ID> treeLazyModel;

    /** Services nécessaires pour la logique formulaire de ligne */
    protected final FormService formService;
    protected final SpatialUnitTreeService spatialUnitTreeService;
    protected final SpatialUnitService spatialUnitService;
    protected final NavBean navBean;
    protected final GenericNewUnitDialogBean<T> genericNewUnitDialogBean;

    /** Fournit l'identifiant unique d'une entité T (ex: RecordingUnit::getId) */
    private final Function<T, ID> idExtractor;

    /**
     * Nom de la propriété de T utilisée comme "form scope" (ex: "type"),
     * passé au EntityFormContext si besoin.
     */
    private final String formScopeValueBinding;

    /** Définition globale des colonnes de la table */
    private final TableDefinition tableDefinition = new TableDefinition();

    /** Contexte de formulaire par ligne (clé = ID de l'entité) */
    protected final Map<ID, EntityFormContext<T>> rowContexts = new HashMap<>();

    // tree mode selection
    @Setter
    @Getter
    private List<TreeNode<T>> checkboxSelectedTreeNodes;



    @Getter
    @Setter
    private ToolbarCreateConfig toolbarCreateConfig;


    @Getter
    @Setter
    protected boolean treeMode = true; // false = table, true = tree

    protected EntityTableViewModel(
            BaseLazyDataModel<T> lazyDataModel,
            GenericNewUnitDialogBean<T> genericNewUnitDialogBean,
            FormService formService,
            SpatialUnitTreeService spatialUnitTreeService,
            SpatialUnitService spatialUnitService,
            NavBean navBean,
            Function<T, ID> idExtractor,
            String formScopeValueBinding
    ) {
        this(
                lazyDataModel,
                null, // treeLazyModel is not used in this constructor
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                idExtractor,
                formScopeValueBinding
        );
    }

    protected EntityTableViewModel(
            BaseLazyDataModel<T> lazyDataModel,
            BaseTreeTableLazyModel<T, ID> treeLazyModel,
            GenericNewUnitDialogBean<T> genericNewUnitDialogBean,
            FormService formService,
            SpatialUnitTreeService spatialUnitTreeService,
            SpatialUnitService spatialUnitService,
            NavBean navBean,
            Function<T, ID> idExtractor,
            String formScopeValueBinding
    ) {
        this.lazyDataModel = lazyDataModel;
        this.treeLazyModel = treeLazyModel;
        this.formService = formService;
        this.genericNewUnitDialogBean = genericNewUnitDialogBean;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
        this.navBean = navBean;
        this.idExtractor = idExtractor;
        this.formScopeValueBinding = formScopeValueBinding;

        // Set global filter for both models if they exist
        if (this.lazyDataModel != null) {
            this.lazyDataModel.setGlobalFilter(globalFilter);
        }
        if (this.treeLazyModel != null) {
            this.treeLazyModel.setGlobalFilter(globalFilter);
        }
    }


    /**
     * Colonnes visibles (pour <p:columns>).
     */
    public List<TableColumn> getColumns() {
        return tableDefinition.getVisibleColumns();
    }

    /**
     * Retourne (ou crée) le contexte de formulaire pour une ligne donnée.
     * Utilisable en EL : #{tableModel.rowContext(item)}
     */
    public EntityFormContext<T> getRowContext(T entity) {
        if (entity == null) {
            return null;
        }
        ID id = idExtractor.apply(entity);
        if (id == null) {
            return null;
        }

        return rowContexts.computeIfAbsent(id, key -> {
            // 1) Formulaire spécifique à cette entité (défini par la sous-classe)
            CustomForm rowForm = resolveRowFormFor(entity);

            // 2) Configuration min/max des champs système pour CETTE ligne
            configureRowSystemFields(entity, rowForm);

            // 3) FieldSource pour cette ligne : colonnes de la table + form spécifique
            FieldSource fs = new TableRowFieldSource(tableDefinition, rowForm);

            // 4) Contexte de formulaire pour cette ligne
            EntityFormContext<T> ctx = new EntityFormContext<>(
                    entity,
                    fs,
                    formService,
                    spatialUnitTreeService,
                    spatialUnitService,
                    null,                    // pas de callback form scope en mode table pour l’instant
                    formScopeValueBinding
            );
            ctx.init(false);
            return ctx;
        });
    }

    /**
     * À appeler si tu veux "réinitialiser" les contextes de formulaire,
     * par exemple après un gros refresh de la liste.
     */
    public void resetRowContexts() {
        rowContexts.clear();
    }

    // ---------------------- Hooks à surcharger par les sous-classes ----------------------

    /**
     * Détermine le CustomForm spécifique à une ligne d'entité T.
     * (ex: pour RecordingUnit, dépend du type + institution)
     */
    protected abstract CustomForm resolveRowFormFor(T entity);

    /**
     * Applique la logique min/max sur les champs système pour une ligne donnée.
     * (ex: identifier, openingDate, closingDate pour RecordingUnit)
     */
    protected abstract void configureRowSystemFields(T entity, CustomForm rowForm);

    // ---------------------- Helpers génériques ----------------------

    /**
     * Helper : récupère tous les CustomField d'un CustomForm (panels → rows → cols).
     * Indépendant du type T, donc factorisé ici.
     */
    protected List<CustomField> getAllFieldsFromForm(CustomForm form) {
        if (form == null || form.getLayout() == null) {
            return List.of();
        }

        return form.getLayout().stream()
                .filter(panel -> panel.getRows() != null)
                .flatMap(panel -> panel.getRows().stream())
                .filter(row -> row.getColumns() != null)
                .flatMap(row -> row.getColumns().stream())
                .map(CustomCol::getField)
                .filter(Objects::nonNull)
                .toList();
    }


    public void onToggle(ColumnToggleEvent e) {
        Integer index = (Integer) e.getData();
        UIColumn column = e.getColumn();
        Visibility visibility = e.getVisibility();
        String header = column.getAriaHeaderText() != null ? column.getAriaHeaderText() : column.getHeaderText();
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "Column " + index + " toggled: " + header + " " + visibility, null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }


    public abstract String resolveText(TableColumn column, T item);

    public abstract Integer resolveCount(TableColumn column, T item);

    public abstract boolean isRendered(TableColumn column, String key, T item, Integer panelIndex);

    public void handleAction(TableColumn column, T item, Integer panelIndex) {
        if (column instanceof CommandLinkColumn linkColumn) {
            handleCommandLink(linkColumn, item, panelIndex);
        } else if (column instanceof RelationColumn relColumn) {
            handleRelationAction(relColumn, item, panelIndex, relColumn.getViewAction());
        }
    }

    public void handleRelationAction(RelationColumn column, T item, Integer panelIndex, TableColumnAction action) {
        // default no-op
    }

    protected void handleCommandLink(CommandLinkColumn column, T item, Integer panelIndex) {
        // default no-op
    }

    public List<RowAction> getRowActions() {
        return List.of(); // par défaut aucune action
    }

    public Object getTreeRoot() {
        return null; // subclasses override if they support TREE mode
    }

    public String getRowActionTooltipCode(RowAction action, T unit) {
        return null; // no tooltip by default
    }

    public boolean isTreeViewSupported() {
        return false;
    }

    public void openCreateDialog(NewUnitContext ctx,
                                 fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean<?> dialogBean) {
        try {
            dialogBean.selectKind(ctx, this);
            org.primefaces.PrimeFaces.current().ajax().update("newUnitForm");
            org.primefaces.PrimeFaces.current().executeScript("PF('newUnitDiag').show()");
        } catch (fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException e) {
            // silent fail, same behavior as before
        }
    }


    public void onAnyEntityCreated(fr.siamois.domain.models.TraceableEntity created, NewUnitContext ctx) {
        if (created == null) return;

        NewUnitContext.UiInsertPolicy policy = ctx.getInsertPolicy();
        if (policy == null) { // if insert policy is null
            return;
        }

        // ⚠️ cast “best effort” sans entityClass
        final T casted;
        try {
            @SuppressWarnings("unchecked")
            T tmp = (T) created;
            casted = tmp;
        } catch (ClassCastException e) {
            return; // pas gérable par cette table -> no-op
        }

        // 1) List view: insert at top
        if (lazyDataModel != null && policy.getListInsert() != NewUnitContext.ListInsert.NONE) {
            lazyDataModel.addRowToModel(casted);
        }

        // 2) Tree view: manual insertion (si treeLazyModel présent)
        if (treeLazyModel != null  && policy.getTreeInsert() != NewUnitContext.TreeInsert.NONE) {
            applyTreeInsertion(casted, ctx);
        }
    }

    protected void applyTreeInsertion(T created, NewUnitContext ctx) {
        // si pas de clickedId => bouton global => root
        ID clickedId = (ctx.getTrigger() != null) ? (ID) ctx.getTrigger().getClickedId() : null;

        var treeInsert = ctx.getInsertPolicy().getTreeInsert();

        if (clickedId == null || treeInsert == ROOT) {
            treeLazyModel.insertChildFirst(null, created);
        } else {
            switch (treeInsert) {
                case CHILD_FIRST ->
                        treeLazyModel.insertChildFirst(clickedId, created);
                case PARENT_AT_ROOT ->
                        treeLazyModel.insertParentAtRoot(clickedId, created);
                default -> {
                    // no op
                }
            }
        }
    }

    // Handler when clicking on the create button on top of the table
    public void openCreateFromToolbar(fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean<?> dialogBean) {
        if (toolbarCreateConfig == null) {
            return; // pas de bouton configuré
        }

        NewUnitContext.UiInsertPolicy policy = toolbarCreateConfig.getInsertPolicySupplier() != null
                ? toolbarCreateConfig.getInsertPolicySupplier().get()
                : NewUnitContext.UiInsertPolicy.builder()
                .listInsert(NewUnitContext.ListInsert.TOP)
                .treeInsert(ROOT)
                .build();

        NewUnitContext ctx = NewUnitContext.builder()
                .kindToCreate(toolbarCreateConfig.getKindToCreate())
                .trigger(toolbarCreateConfig.getTriggerSupplier().get()) // toolbar()
                .scope(toolbarCreateConfig.getScopeSupplier().get())
                .insertPolicy(policy)
                .build();

        try {
            dialogBean.selectKind(ctx, this);
        } catch (fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException e) {
            // comportement silencieux comme avant
        }
    }

    public boolean hasUnsavedModifications() {
        return rowContexts.values().stream()
                .anyMatch(EntityFormContext::isHasUnsavedModifications);
    }

    /**
     * Save all the entities of the table
     */
    public abstract void save();

    /**
     * Cancel any modifs
     */
    public void cancelModifications() {
        rowContexts.values().forEach(ctx -> {
            ctx.init(true);
            ctx.setHasUnsavedModifications(false);
        });
    }

    /*
    * Check if user has permission to edit the row data
     */
    public abstract boolean canUserEditRow(T unit);




}
