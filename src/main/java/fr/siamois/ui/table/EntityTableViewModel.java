package fr.siamois.ui.table;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitCreationContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FieldSource;
import fr.siamois.ui.form.TableRowFieldSource;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.BaseTreeTableLazyModel;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.PrimeFaces;
import org.primefaces.component.api.UIColumn;
import org.primefaces.event.ColumnToggleEvent;
import org.primefaces.model.Visibility;

import java.util.*;
import java.util.function.Function;

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

    /** Lazy model "pur data" (chargement, tri, filtres, sélection, etc.) */
    protected final BaseLazyDataModel<T> lazyDataModel;
    protected final BaseTreeTableLazyModel<T, ID> treeLazyModel;

    /** Services nécessaires pour la logique formulaire de ligne */
    protected final FormService formService;
    protected final SpatialUnitTreeService spatialUnitTreeService;
    protected final SpatialUnitService spatialUnitService;
    protected final NavBean navBean;
    protected final GenericNewUnitDialogBean genericNewUnitDialogBean;

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
    private final Map<ID, EntityFormContext<T>> rowContexts = new HashMap<>();

    @Getter
    @Setter
    private boolean treeMode = false; // false = table, true = tree

    protected EntityTableViewModel(BaseLazyDataModel<T> lazyDataModel,
                                   BaseTreeTableLazyModel<T, ID> treeLazyModel,
                                   GenericNewUnitDialogBean<T> genericNewUnitDialogBean,
                                   FormService formService,
                                   SpatialUnitTreeService spatialUnitTreeService,
                                   SpatialUnitService spatialUnitService, NavBean navBean,
                                   Function<T, ID> idExtractor,
                                   String formScopeValueBinding) {
        this.lazyDataModel = lazyDataModel;
        this.treeLazyModel = treeLazyModel;
        this.formService = formService;
        this.genericNewUnitDialogBean = genericNewUnitDialogBean;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
        this.navBean = navBean;
        this.idExtractor = idExtractor;
        this.formScopeValueBinding = formScopeValueBinding;
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

        Set<CustomField> fields = new LinkedHashSet<>();

        for (CustomFormPanel panel : form.getLayout()) {
            if (panel.getRows() == null) continue;
            for (CustomRow row : panel.getRows()) {
                if (row.getColumns() == null) continue;
                for (CustomCol col : row.getColumns()) {
                    CustomField field = col.getField();
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }
        }

        return new ArrayList<>(fields);
    }

    public void onToggle(ColumnToggleEvent e) {
        Integer index = (Integer) e.getData();
        UIColumn column = e.getColumn();
        Visibility visibility = e.getVisibility();
        String header = column.getAriaHeaderText() != null ? column.getAriaHeaderText() : column.getHeaderText();
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "Column " + index + " toggled: " + header + " " + visibility, null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }


    public String resolveText(TableColumn column, T item) {
        return "";
    }

    public Integer resolveCount(TableColumn column, T item) {
        return 0;
    }

    public boolean isRendered(TableColumn column, String key, T item, Integer panelIndex) {
        return true;
    }

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

    public boolean isTreeViewSupported() {
        return false;
    }

    // ==========================
//  Central method (private)
// ==========================
    private <X extends TraceableEntity> void doTrySelectKind(
            UnitKind kind,
            BaseLazyDataModel<X> lazyContext,
            Set<X> setContext,
            TraceableEntity parent,
            X multiHierarchyParent,
            X multiHierarchyChild,
            NewUnitCreationContext<?> creationContext
    ) {
        try {
            // 1) init dialog kind + bind the "source table model" + insertion context
            genericNewUnitDialogBean.selectKind(
                    kind,
                    this,              // 👈 source table model / owner of insertion logic
                    creationContext
            );

            // 2) provide "what to update" context
            genericNewUnitDialogBean.setLazyDataModel(lazyContext);
            genericNewUnitDialogBean.setSetToUpdate(setContext);

            // 3) creation context (your existing logic)
            genericNewUnitDialogBean.setParent(parent);
            genericNewUnitDialogBean.setMultiHierarchyParent(multiHierarchyParent);
            genericNewUnitDialogBean.setMultiHierarchyChild(multiHierarchyChild);

            // 4) open dialog
            PrimeFaces.current().ajax().update("newUnitForm");
            PrimeFaces.current().executeScript("PF('newUnitDiag').show()");

        } catch (CannotInitializeNewUnitDialogException e) {
            // keep same behavior as before (silent fail)
        }
    }
// =======================================
//  Existing overloads (keep them working)
// =======================================

    /**
     * Old: selectKind(kind)
     */
    protected void trySelectKind(UnitKind kind) {
        doTrySelectKind(kind, null, null, null, null, null, null);
    }

    /**
     * Old: selectKind(kind, BaseLazyDataModel context)
     */
    protected <X extends TraceableEntity> void trySelectKind(UnitKind kind, BaseLazyDataModel<X> lazyContext) {
        doTrySelectKind(kind, lazyContext, null, null, null, null, null);
    }

    /**
     * Old: selectKind(kind, Set context, TraceableEntity parent)
     */
    protected <X extends TraceableEntity> void trySelectKind(UnitKind kind, Set<X> setContext, TraceableEntity parent) {
        doTrySelectKind(kind, null, setContext, parent, null, null, null);
    }

    /**
     * Old: selectKind(kind, Set context, parent, child) (multi hierarchy)
     */
    protected <X extends TraceableEntity> void trySelectKind(UnitKind kind, Set<X> setContext, X parent, X child) {
        doTrySelectKind(kind, null, setContext, null, parent, child, null);
    }

// =======================================
//  New overloads WITH NewUnitCreationContext
// =======================================

    /**
     * New: same as trySelectKind(kind) but with insertion context
     */
    protected void trySelectKind(UnitKind kind, NewUnitCreationContext<?> ctx) {
        doTrySelectKind(kind, null, null, null, null, null, ctx);
    }

    /**
     * New: same as trySelectKind(kind, lazyContext) but with insertion context
     */
    protected <X extends TraceableEntity> void trySelectKind(
            UnitKind kind,
            BaseLazyDataModel<X> lazyContext,
            NewUnitCreationContext<?> ctx
    ) {
        doTrySelectKind(kind, lazyContext, null, null, null, null, ctx);
    }

    /**
     * New: same as trySelectKind(kind, setContext, parentEntity) but with insertion context
     */
    protected <X extends TraceableEntity> void trySelectKind(
            UnitKind kind,
            Set<X> setContext,
            TraceableEntity parent,
            NewUnitCreationContext<?> ctx
    ) {
        doTrySelectKind(kind, null, setContext, parent, null, null, ctx);
    }

    /**
     * New: same as trySelectKind(kind, setContext, mhParent, mhChild) but with insertion context
     */
    protected <X extends TraceableEntity> void trySelectKind(
            UnitKind kind,
            Set<X> setContext,
            X multiHierarchyParent,
            X multiHierarchyChild,
            NewUnitCreationContext<?> ctx
    ) {
        doTrySelectKind(kind, null, setContext, null, multiHierarchyParent, multiHierarchyChild, ctx);
    }


    public void onAnyEntityCreated(TraceableEntity created, NewUnitCreationContext<?> ctx) {
        if (created == null) return;

        try {
            @SuppressWarnings("unchecked")
            T casted = (T) created;
            ID id = (ID) ctx.getClickedId();

            if (lazyDataModel != null) {
                lazyDataModel.addRowToModel(casted);
            }
            if (treeLazyModel != null && ctx != null) {
                switch (ctx.getInsertMode()) {
                    case TREE_CHILD_FIRST ->
                            treeLazyModel.insertChildFirst(id, casted);
                    case TREE_PARENT_AT_ROOT ->
                            treeLazyModel.insertParentAtRoot(id, casted);
                    default -> {}
                }
            }
        } catch (ClassCastException e) {
            // pas le bon type -> ignore
        }
    }

}
