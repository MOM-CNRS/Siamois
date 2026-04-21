package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;
import jakarta.faces.FacesException;
import jakarta.faces.component.ContextCallback;
import jakarta.faces.component.StateHelper;
import jakarta.faces.component.TransientStateHelper;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.behavior.ClientBehavior;
import jakarta.faces.component.visit.VisitCallback;
import jakarta.faces.component.visit.VisitContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.*;
import jakarta.faces.render.Renderer;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.columngroup.ColumnGroup;
import org.primefaces.component.headerrow.HeaderRow;
import org.primefaces.component.row.Row;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.component.treetable.TreeTableState;
import org.primefaces.model.*;
import org.primefaces.util.Callbacks;
import org.primefaces.util.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("rawtypes")
public class LazyTreeTable extends TreeTable {

    private static final Logger log = LoggerFactory.getLogger(LazyTreeTable.class);
    private transient TreeNode lazyRoot;
    private transient boolean blockFiltering = false;

    @Override
    public String resolveWidgetVar() {
        return super.resolveWidgetVar();
    }

    @Override
    public String resolveWidgetVar(FacesContext context) {
        return super.resolveWidgetVar(context);
    }

    enum PropertyKeys {
        lazy,
        rowCount,
        lazyDataModel,
        isLeafMethod,
        loadMethod;
    }

    public void setIsLeafMethod(Callbacks.SerializableFunction<AbstractEntityDTO, Boolean> isLeafMethod) {
        getStateHelper().put(PropertyKeys.isLeafMethod, isLeafMethod);
    }

    public void setLoadMethod(Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>> loadMethod) {
        getStateHelper().put(PropertyKeys.loadMethod, loadMethod);
    }

    @SuppressWarnings("unchecked")
    public Callbacks.SerializableFunction<AbstractEntityDTO, Boolean> getIsLeafMethod() {
        return (Callbacks.SerializableFunction<AbstractEntityDTO, Boolean>) getStateHelper().eval(PropertyKeys.isLeafMethod, null);
    }

    @SuppressWarnings("unchecked")
    public Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>> getLoadMethod() {
        return (Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>>) getStateHelper().eval(PropertyKeys.loadMethod, null);
    }

    public boolean isLazy() {
        return (boolean) getStateHelper().eval(PropertyKeys.lazy, false);
    }

    public void setLazy(boolean lazy) {
        getStateHelper().put(PropertyKeys.lazy, lazy);
    }

    @SuppressWarnings("unchecked")
    public LazyDataModel<? extends AbstractEntityDTO> getLazyDataModel() {
        return (LazyDataModel<? extends AbstractEntityDTO>) getStateHelper().eval(PropertyKeys.lazyDataModel, null);
    }

    public void setLazyDataModel(LazyDataModel<? extends AbstractEntityDTO> model) {
        getStateHelper().put(PropertyKeys.lazyDataModel, model);
    }

    public LazyTreeTable() {
        super();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return super.getAttributes();
    }

    @Override
    public Map<String, Object> getPassThroughAttributes() {
        return super.getPassThroughAttributes();
    }

    @Override
    public Map<String, Object> getPassThroughAttributes(boolean create) {
        return super.getPassThroughAttributes(create);
    }

    @Override
    public ValueExpression getValueExpression(String name) {
        return super.getValueExpression(name);
    }

    @Override
    public void setValueExpression(String name, ValueExpression binding) {
        super.setValueExpression(name, binding);
    }

    @Override
    public String getClientId(FacesContext context) {
        return super.getClientId(context);
    }

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    @Override
    public UIComponent getParent() {
        return super.getParent();
    }

    @Override
    public void setParent(UIComponent parent) {
        super.setParent(parent);
    }

    @Override
    public boolean isRendered() {
        return super.isRendered();
    }

    @Override
    public void setRendered(boolean rendered) {
        super.setRendered(rendered);
    }

    @Override
    public String getRendererType() {
        return super.getRendererType();
    }

    @Override
    public void setRendererType(String rendererType) {
        super.setRendererType(rendererType);
    }

    @Override
    public boolean getRendersChildren() {
        return super.getRendersChildren();
    }

    @Override
    public Map<String, String> getResourceBundleMap() {
        return super.getResourceBundleMap();
    }

    @Override
    public List<UIComponent> getChildren() {
        return super.getChildren();
    }

    @Override
    public int getChildCount() {
        return super.getChildCount();
    }

    @Override
    public UIComponent findComponent(String expression) {
        return super.findComponent(expression);
    }

    @Override
    public String getRowKey() {
        return super.getRowKey();
    }

    @Override
    public void setRowKey(String rowKey) {
        super.setRowKey(rowKey);
    }

    @Override
    public void setRowKey(TreeNode root, String rowKey) {
        super.setRowKey(root, rowKey);
    }

    @Override
    public void setRowKey(Lazy<TreeNode> root, String rowKey) {
        super.setRowKey(root, rowKey);
    }

    @Override
    protected void setRowKey(Lazy<TreeNode> lazyRoot, TreeNode root, String rowKey) {
        super.setRowKey(lazyRoot, root, rowKey);
    }

    @Override
    public TreeNode getRowNode() {
        return super.getRowNode();
    }

    @Override
    public String getVar() {
        return super.getVar();
    }

    @Override
    public void setVar(String _var) {
        super.setVar(_var);
    }

    @Override
    public String getNodeVar() {
        return super.getNodeVar();
    }

    @Override
    public void setNodeVar(String _nodeVar) {
        super.setNodeVar(_nodeVar);
    }

    @Override
    public String getFamily() {
        return super.getFamily();
    }

    @Override
    public String getWidgetVar() {
        return super.getWidgetVar();
    }

    @Override
    public void setWidgetVar(String widgetVar) {
        super.setWidgetVar(widgetVar);
    }

    @Override
    public String getStyle() {
        return super.getStyle();
    }

    @Override
    public void setStyle(String style) {
        super.setStyle(style);
    }

    @Override
    public String getStyleClass() {
        return super.getStyleClass();
    }

    @Override
    public void setStyleClass(String styleClass) {
        super.setStyleClass(styleClass);
    }

    @Override
    public boolean isScrollable() {
        return super.isScrollable();
    }

    @Override
    public void setScrollable(boolean scrollable) {
        super.setScrollable(scrollable);
    }

    @Override
    public String getScrollHeight() {
        return super.getScrollHeight();
    }

    @Override
    public void setScrollHeight(String scrollHeight) {
        super.setScrollHeight(scrollHeight);
    }

    @Override
    public String getScrollWidth() {
        return super.getScrollWidth();
    }

    @Override
    public void setScrollWidth(String scrollWidth) {
        super.setScrollWidth(scrollWidth);
    }

    @Override
    public String getTableStyle() {
        return super.getTableStyle();
    }

    @Override
    public void setTableStyle(String tableStyle) {
        super.setTableStyle(tableStyle);
    }

    @Override
    public String getTableStyleClass() {
        return super.getTableStyleClass();
    }

    @Override
    public void setTableStyleClass(String tableStyleClass) {
        super.setTableStyleClass(tableStyleClass);
    }

    @Override
    public String getEmptyMessage() {
        return super.getEmptyMessage();
    }

    @Override
    public void setEmptyMessage(String emptyMessage) {
        super.setEmptyMessage(emptyMessage);
    }

    @Override
    public boolean isResizableColumns() {
        return super.isResizableColumns();
    }

    @Override
    public void setResizableColumns(boolean resizableColumns) {
        super.setResizableColumns(resizableColumns);
    }

    @Override
    public String getRowStyleClass() {
        return super.getRowStyleClass();
    }

    @Override
    public void setRowStyleClass(String rowStyleClass) {
        super.setRowStyleClass(rowStyleClass);
    }

    @Override
    public String getRowTitle() {
        return super.getRowTitle();
    }

    @Override
    public void setRowTitle(String rowTitle) {
        super.setRowTitle(rowTitle);
    }

    @Override
    public boolean isLiveResize() {
        return super.isLiveResize();
    }

    @Override
    public void setLiveResize(boolean liveResize) {
        super.setLiveResize(liveResize);
    }

    @Override
    public Object getSortBy() {
        return super.getSortBy();
    }

    @Override
    public void setSortBy(Object sortBy) {
        super.setSortBy(sortBy);
    }

    @Override
    public void decodeColumnTogglerState(FacesContext context) {
        super.decodeColumnTogglerState(context);
    }

    @Override
    public void decodeColumnResizeState(FacesContext context) {
        super.decodeColumnResizeState(context);
    }

    @Override
    public boolean isNativeElements() {
        return super.isNativeElements();
    }

    @Override
    public void setNativeElements(boolean nativeElements) {
        super.setNativeElements(nativeElements);
    }

    @Override
    public Object getDataLocale() {
        return super.getDataLocale();
    }

    @Override
    public void setDataLocale(Object dataLocale) {
        super.setDataLocale(dataLocale);
    }

    @Override
    public String getExpandMode() {
        return super.getExpandMode();
    }

    @Override
    public void setExpandMode(String expandMode) {
        super.setExpandMode(expandMode);
    }

    @Override
    public boolean isStickyHeader() {
        return super.isStickyHeader();
    }

    @Override
    public void setStickyHeader(boolean stickyHeader) {
        super.setStickyHeader(stickyHeader);
    }

    @Override
    public boolean isEditable() {
        return super.isEditable();
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
    }

    @Override
    public String getEditMode() {
        return super.getEditMode();
    }

    @Override
    public void setEditMode(String editMode) {
        super.setEditMode(editMode);
    }

    @Override
    public boolean isEditingRow() {
        return super.isEditingRow();
    }

    @Override
    public void setEditingRow(boolean editingRow) {
        super.setEditingRow(editingRow);
    }

    @Override
    public String getCellSeparator() {
        return super.getCellSeparator();
    }

    @Override
    public void setCellSeparator(String cellSeparator) {
        super.setCellSeparator(cellSeparator);
    }

    @Override
    public boolean isDisabledTextSelection() {
        return super.isDisabledTextSelection();
    }

    @Override
    public void setDisabledTextSelection(boolean disabledTextSelection) {
        super.setDisabledTextSelection(disabledTextSelection);
    }

    @Override
    public boolean isPaginator() {
        return super.isPaginator();
    }

    @Override
    public void setPaginator(boolean paginator) {
        super.setPaginator(paginator);
    }

    @Override
    public String getPaginatorTemplate() {
        return super.getPaginatorTemplate();
    }

    @Override
    public void setPaginatorTemplate(String paginatorTemplate) {
        super.setPaginatorTemplate(paginatorTemplate);
    }

    @Override
    public String getRowsPerPageTemplate() {
        return super.getRowsPerPageTemplate();
    }

    @Override
    public void setRowsPerPageTemplate(String rowsPerPageTemplate) {
        super.setRowsPerPageTemplate(rowsPerPageTemplate);
    }

    @Override
    public String getCurrentPageReportTemplate() {
        return super.getCurrentPageReportTemplate();
    }

    @Override
    public void setCurrentPageReportTemplate(String currentPageReportTemplate) {
        super.setCurrentPageReportTemplate(currentPageReportTemplate);
    }

    @Override
    public int getPageLinks() {
        return super.getPageLinks();
    }

    @Override
    public void setPageLinks(int pageLinks) {
        super.setPageLinks(pageLinks);
    }

    @Override
    public String getPaginatorPosition() {
        return super.getPaginatorPosition();
    }

    @Override
    public void setPaginatorPosition(String paginatorPosition) {
        super.setPaginatorPosition(paginatorPosition);
    }

    @Override
    public boolean isPaginatorAlwaysVisible() {
        return super.isPaginatorAlwaysVisible();
    }

    @Override
    public void setPaginatorAlwaysVisible(boolean paginatorAlwaysVisible) {
        super.setPaginatorAlwaysVisible(paginatorAlwaysVisible);
    }

    @Override
    public int getRows() {
        return super.getRows();
    }

    @Override
    public Map<String, Class<? extends BehaviorEvent>> getBehaviorEventMapping() {
        return super.getBehaviorEventMapping();
    }

    @Override
    public Collection<String> getEventNames() {
        return super.getEventNames();
    }

    @Override
    public Map<String, List<ClientBehavior>> getClientBehaviors() {
        return super.getClientBehaviors();
    }

    @Override
    public String getDefaultEventName() {
        return super.getDefaultEventName();
    }

    @Override
    public void queueEvent(FacesEvent event) {
        super.queueEvent(event);
    }

    @Override
    public void subscribeToEvent(Class<? extends SystemEvent> eventClass, ComponentSystemEventListener componentListener) {
        super.subscribeToEvent(eventClass, componentListener);
    }

    @Override
    public void unsubscribeFromEvent(Class<? extends SystemEvent> eventClass, ComponentSystemEventListener componentListener) {
        super.unsubscribeFromEvent(eventClass, componentListener);
    }

    @Override
    public List<SystemEventListener> getListenersForEventClass(Class<? extends SystemEvent> eventClass) {
        return super.getListenersForEventClass(eventClass);
    }

    @Override
    public UIComponent getNamingContainer() {
        return super.getNamingContainer();
    }

    @Override
    public void broadcast(FacesEvent event) throws AbortProcessingException {
        super.broadcast(event);
    }

    @Override
    public void decode(FacesContext context) {
        super.decode(context);
    }

    @Override
    public void processEvent(ComponentSystemEvent event) throws AbortProcessingException {
        super.processEvent(event);
    }

    @Override
    public void processDecodes(FacesContext context) {
        super.processDecodes(context);
    }

    @Override
    public void processValidators(FacesContext context) {
        super.processValidators(context);
    }

    @Override
    protected void validateSelection(FacesContext context) {
        super.validateSelection(context);
    }

    @Override
    public void processUpdates(FacesContext context) {
        super.processUpdates(context);
    }

    @Override
    public Object processSaveState(FacesContext context) {
        return super.processSaveState(context);
    }

    @Override
    public void processRestoreState(FacesContext context, Object state) {
        super.processRestoreState(context, state);
    }

    @Override
    protected FacesContext getFacesContext() {
        return super.getFacesContext();
    }

    @Override
    protected Renderer getRenderer(FacesContext context) {
        return super.getRenderer(context);
    }

    @Override
    public void markInitialState() {
        super.markInitialState();
    }

    @Override
    public boolean initialStateMarked() {
        return super.initialStateMarked();
    }

    @Override
    public void clearInitialState() {
        super.clearInitialState();
    }

    @Override
    protected StateHelper getStateHelper() {
        return super.getStateHelper();
    }

    @Override
    protected StateHelper getStateHelper(boolean create) {
        return super.getStateHelper(create);
    }

    @Override
    public TransientStateHelper getTransientStateHelper() {
        return super.getTransientStateHelper();
    }

    @Override
    public TransientStateHelper getTransientStateHelper(boolean create) {
        return super.getTransientStateHelper(create);
    }

    @Override
    public void restoreTransientState(FacesContext context, Object state) {
        super.restoreTransientState(context, state);
    }

    @Override
    public Object saveTransientState(FacesContext context) {
        return super.saveTransientState(context);
    }

    @Override
    public boolean isInView() {
        return super.isInView();
    }

    @Override
    public void setInView(boolean isInView) {
        super.setInView(isInView);
    }

    @Override
    public String getClientId() {
        return super.getClientId();
    }

    @Override
    public void updateSelection(FacesContext context) {
        super.updateSelection(context);
    }

    @Override
    protected void processNodes(FacesContext context, PhaseId phaseId, TreeNode root) {
        super.processNodes(context, phaseId, root);
    }

    @Override
    public boolean isResizeRequest(FacesContext context) {
        return super.isResizeRequest(context);
    }

    @Override
    public String getScrollState() {
        return super.getScrollState();
    }

    @Override
    public Locale resolveDataLocale() {
        return super.resolveDataLocale();
    }

    @Override
    public void forEachColumn(Predicate<UIColumn> callback) {
        super.forEachColumn(callback);
    }

    @Override
    public void forEachColumn(boolean unwrapDynamicColumns, boolean skipUnrendered, boolean skipColumnGroups, Predicate<UIColumn> callback) {
        super.forEachColumn(unwrapDynamicColumns, skipUnrendered, skipColumnGroups, callback);
    }

    @Override
    public boolean forEachColumn(FacesContext context, UIComponent root, boolean unwrapDynamicColumns, boolean skipUnrendered, boolean skipColumnGroups, Predicate<UIColumn> callback) {
        return super.forEachColumn(context, root, unwrapDynamicColumns, skipUnrendered, skipColumnGroups, callback);
    }

    @Override
    public boolean forEachColumnGroupRow(FacesContext context, ColumnGroup cg, boolean skipUnrendered, Predicate<Row> callback) {
        return super.forEachColumnGroupRow(context, cg, skipUnrendered, callback);
    }

    @Override
    public void invokeOnColumn(String columnKey, Consumer<UIColumn> callback) {
        super.invokeOnColumn(columnKey, callback);
    }

    @Override
    public void invokeOnColumn(String columnKey, int rowIndex, Consumer<UIColumn> callback) {
        super.invokeOnColumn(columnKey, rowIndex, callback);
    }

    @Override
    public UIColumn findColumn(String columnKey) {
        return super.findColumn(columnKey);
    }

    @Override
    public int getFrozenColumnsCount() {
        return super.getFrozenColumnsCount();
    }

    @Override
    public UIColumn findColumnInGroup(String columnKey, ColumnGroup group) {
        return super.findColumnInGroup(columnKey, group);
    }

    @Override
    public ColumnGroup getColumnGroup(String type) {
        return super.getColumnGroup(type);
    }

    @Override
    public List<UIColumn> getColumns() {
        return super.getColumns();
    }

    @Override
    public void setColumns(List<UIColumn> columns) {
        super.setColumns(columns);
    }

    @Override
    public List<UIColumn> collectColumns() {
        return super.collectColumns();
    }

    @Override
    public int getColumnsCount() {
        return super.getColumnsCount();
    }

    @Override
    public int getColumnsCount(boolean visibleOnly) {
        return super.getColumnsCount(visibleOnly);
    }

    @Override
    public int getColumnsCountWithSpan() {
        return super.getColumnsCountWithSpan();
    }

    @Override
    public int getColumnsCountWithSpan(boolean visibleOnly) {
        return super.getColumnsCountWithSpan(visibleOnly);
    }

    @Override
    public Object saveState(FacesContext context) {
        return super.saveState(context);
    }

    @Override
    public void restoreState(FacesContext context, Object state) {
        super.restoreState(context, state);
    }

    @Override
    public boolean isTransient() {
        return super.isTransient();
    }

    @Override
    public void setTransient(boolean transientFlag) {
        super.setTransient(transientFlag);
    }

    @Override
    public void addClientBehavior(String eventName, ClientBehavior behavior) {
        super.addClientBehavior(eventName, behavior);
    }

    @Override
    public int getRowCount() {
        if (isLazy()) {
            return (Integer) getStateHelper().eval(PropertyKeys.rowCount, 0);
        }
        return super.getRowCount();
    }

    @Override
    public int getPage() {
        return super.getPage();
    }

    @Override
    public int getRowsToRender() {
        return super.getRowsToRender();
    }

    @Override
    public int getPageCount() {
        return super.getPageCount();
    }

    @Override
    public UIComponent getHeader() {
        return super.getHeader();
    }

    @Override
    public UIComponent getFooter() {
        return super.getFooter();
    }

    @Override
    public void calculateFirst() {
        super.calculateFirst();
    }

    @Override
    public void updatePaginationData(FacesContext context) {
        super.updatePaginationData(context);
    }

    @Override
    public void updateFilteredValue(FacesContext context, TreeNode node) {
        super.updateFilteredValue(context, node);
    }

    @Override
    public List<String> getFilteredRowKeys() {
        return super.getFilteredRowKeys();
    }

    @Override
    public void setFilteredRowKeys(List<String> filteredRowKeys) {
        super.setFilteredRowKeys(filteredRowKeys);
    }

    @Override
    protected boolean requiresColumns() {
        return super.requiresColumns();
    }

    @Override
    protected boolean visitColumnsAndColumnFacets(VisitContext context, VisitCallback callback, boolean visitRows, Lazy<TreeNode> root) {
        return super.visitColumnsAndColumnFacets(context, callback, visitRows, root);
    }

    @Override
    protected boolean visitColumnFacets(VisitContext context, VisitCallback callback, UIComponent component) {
        return super.visitColumnFacets(context, callback, component);
    }

    @Override
    protected boolean visitColumnGroup(VisitContext context, VisitCallback callback, ColumnGroup group) {
        return super.visitColumnGroup(context, callback, group);
    }

    @Override
    public boolean isRTLRendering() {
        return super.isRTLRendering();
    }

    @Override
    public void setRTLRendering(boolean rtl) {
        super.setRTLRendering(rtl);
    }

    @Override
    protected boolean shouldVisitNode(TreeNode node) {
        return super.shouldVisitNode(node);
    }

    @Override
    public void restoreMultiViewState() {
        super.restoreMultiViewState();
    }

    @Override
    public TreeTableState getMultiViewState(boolean create) {
        return super.getMultiViewState(create);
    }

    @Override
    public void resetMultiViewState() {
        super.resetMultiViewState();
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public Map<String, SortMeta> getSortByAsMap() {
        return super.getSortByAsMap();
    }

    @Override
    public void setSortByAsMap(Map<String, SortMeta> sortBy) {
        super.setSortByAsMap(sortBy);
    }

    @Override
    public Map<String, FilterMeta> getFilterByAsMap() {
        return super.getFilterByAsMap();
    }

    @Override
    public void setFilterByAsMap(Map<String, FilterMeta> filterBy) {
        super.setFilterByAsMap(filterBy);
    }

    @Override
    public Map<String, FilterMeta> getActiveFilterMeta() {
        return super.getActiveFilterMeta();
    }

    @Override
    public boolean isFilterByAsMapDefined() {
        return super.isFilterByAsMapDefined();
    }

    @Override
    public boolean isMultiSort() {
        return super.isMultiSort();
    }

    @Override
    public Map<String, ColumnMeta> getColumnMeta() {
        return super.getColumnMeta();
    }

    @Override
    public void setColumnMeta(Map<String, ColumnMeta> columnMeta) {
        super.setColumnMeta(columnMeta);
    }

    @Override
    public String getOrderedColumnKeys() {
        return super.getOrderedColumnKeys();
    }

    @Override
    public String getWidth() {
        return super.getWidth();
    }

    @Override
    public void setWidth(String width) {
        super.setWidth(width);
    }

    @Override
    public void decodeColumnDisplayOrderState(FacesContext context) {
        super.decodeColumnDisplayOrderState(context);
    }

    @Override
    public String getColumnsWidthForClientSide() {
        return super.getColumnsWidthForClientSide();
    }

    @Override
    public Object getFieldValue(FacesContext context, UIColumn column) {
        return super.getFieldValue(context, column);
    }

    @Override
    public String getConvertedFieldValue(FacesContext context, UIColumn column) {
        return super.getConvertedFieldValue(context, column);
    }

    public void setRowCount(int rowCount) {
        getStateHelper().put(PropertyKeys.rowCount, rowCount);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public TreeNode getValue() {
        if (isLazy()) {
            if (lazyRoot == null) {
                loadLazyData();
            }
            return lazyRoot;
        }
        return super.getValue();
    }

    @Override
    public void setValue(TreeNode _value) {
        super.setValue(_value);
    }

    @Override
    public String getSelectionMode() {
        return super.getSelectionMode();
    }

    @Override
    public void setSelectionMode(String _selectionMode) {
        super.setSelectionMode(_selectionMode);
    }

    @Override
    public Object getSelection() {
        return super.getSelection();
    }

    @Override
    public void setSelection(Object _selection) {
        super.setSelection(_selection);
    }

    @Override
    public boolean isRequired() {
        return super.isRequired();
    }

    @Override
    public void setRequired(boolean _required) {
        super.setRequired(_required);
    }

    @Override
    public String getRequiredMessage() {
        return super.getRequiredMessage();
    }

    @Override
    public void setRequiredMessage(String _requiredMessage) {
        super.setRequiredMessage(_requiredMessage);
    }

    @Override
    public boolean isSkipChildren() {
        return super.isSkipChildren();
    }

    @Override
    public void setSkipChildren(boolean _skipChildren) {
        super.setSkipChildren(_skipChildren);
    }

    @Override
    public boolean isShowUnselectableCheckbox() {
        return super.isShowUnselectableCheckbox();
    }

    @Override
    public void setShowUnselectableCheckbox(boolean _showUnselectableCheckbox) {
        super.setShowUnselectableCheckbox(_showUnselectableCheckbox);
    }

    @Override
    public Object getLocalSelectedNodes() {
        return super.getLocalSelectedNodes();
    }

    @Override
    public boolean isPropagateSelectionDown() {
        return super.isPropagateSelectionDown();
    }

    @Override
    public void setPropagateSelectionDown(boolean _propagateSelectionDown) {
        super.setPropagateSelectionDown(_propagateSelectionDown);
    }

    @Override
    public boolean isPropagateSelectionUp() {
        return super.isPropagateSelectionUp();
    }

    @Override
    public void setPropagateSelectionUp(boolean _propagateSelectionUp) {
        super.setPropagateSelectionUp(_propagateSelectionUp);
    }

    @Override
    protected TreeNode<?> findTreeNode(TreeNode<?> searchRoot, String rowKey) {
        return super.findTreeNode(searchRoot, rowKey);
    }

    @Override
    public void buildRowKeys(TreeNode<?> node) {
        super.buildRowKeys(node);
    }

    @Override
    public void populateRowKeys(TreeNode<?> node, List<String> keys) {
        super.populateRowKeys(node, keys);
    }

    @Override
    public void updateRowKeys(TreeNode<?> node) {
        super.updateRowKeys(node);
    }

    @Override
    public void initPreselection() {
        super.initPreselection();
    }

    @Override
    public void refreshSelectedNodeKeys() {
        super.refreshSelectedNodeKeys();
    }

    @Override
    public String getSelectedRowKeysAsString() {
        return super.getSelectedRowKeysAsString();
    }

    @Override
    public String getContainerClientId(FacesContext context) {
        return super.getContainerClientId(context);
    }

    private void clearCache() {
        if (isLazy()) {
            this.lazyRoot = null;
            this.setValue(null);
        }
    }

    @Override
    public void setFirst(int first) {
        if (first != this.getFirst()) {
            super.setFirst(first);
            clearCache();
        }
    }

    @Override
    public void setRows(int rows) {
        if (rows != this.getRows()) {
            super.setRows(rows);
            clearCache();
        }
    }

    @Override
    public int getFirst() {
        return super.getFirst();
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        super.encodeBegin(context);
        this.blockFiltering = true;
    }

    @Override
    public void encodeChildren(FacesContext context) throws IOException {
        super.encodeChildren(context);
    }

    @Override
    protected void preDecode(FacesContext context) {
        super.preDecode(context);
    }

    @Override
    protected void preValidate(FacesContext context) {
        super.preValidate(context);
    }

    @Override
    protected void preUpdate(FacesContext context) {
        super.preUpdate(context);
    }

    @Override
    protected void preEncode(FacesContext context) {
        super.preEncode(context);
    }

    @Override
    public boolean isSelectionEnabled() {
        return super.isSelectionEnabled();
    }

    @Override
    public boolean isMultipleSelectionMode() {
        return super.isMultipleSelectionMode();
    }

    @Override
    public Class<?> getSelectionType() {
        return super.getSelectionType();
    }

    @Override
    public boolean isCheckboxSelectionMode() {
        return super.isCheckboxSelectionMode();
    }

    @Override
    protected String childRowKey(String parentRowKey, int childIndex) {
        return super.childRowKey(parentRowKey, childIndex);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadLazyData() {
        if (lazyRoot != null) return;

        FacesContext context = getFacesContext();
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = getClientId(context);

        String behaviorEvent = params.get("javax.faces.behavior.event");
        boolean isFilterJSCall = params.containsKey(clientId + "_filtering");

        // 1. Gestion des événements de Reset (retour page 1 sur filtre) ou Pagination
        if ("filter".equals(behaviorEvent) || isFilterJSCall) {
            super.setFirst(0);
        } else if (params.containsKey(clientId + "_pagination")) {
            String firstParam = params.get(clientId + "_first");
            String rowsParam = params.get(clientId + "_rows");

            if (firstParam != null) {
                super.setFirst(Integer.parseInt(firstParam));
            }
            if (rowsParam != null) {
                super.setRows(Integer.parseInt(rowsParam));
            }
        }

        LazyDataModel<? extends AbstractEntityDTO> lazyModel = getLazyDataModel();

        if (lazyModel != null) {
            int rows = getRows() > 0 ? getRows() : 10;
            int first = getFirst();

            Map<String, SortMeta> sortMetaMap = getSortByAsMap();
            Map<String, FilterMeta> rawFilterMap = getFilterByAsMap();
            Map<String, FilterMeta> activeFilters = new HashMap<>();
            Map<String, SortMeta> activeSorts = new HashMap<>();

            if (rawFilterMap != null) {
                for (Map.Entry<String, FilterMeta> entry : rawFilterMap.entrySet()) {
                    if ("globalFilter".equals(entry.getKey())) {
                        continue;
                    }

                    Object val = entry.getValue().getFilterValue();
                    if (val instanceof String strVal) {
                        if (!strVal.trim().isEmpty()) {
                            activeFilters.put(entry.getValue().getFilterBy().getValue(context.getELContext()), entry.getValue());
                        }
                    } else if (val != null) {
                        activeFilters.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            String globalVal = null;

            if (rawFilterMap != null && rawFilterMap.containsKey("globalFilter")) {
                globalVal = (String) rawFilterMap.get("globalFilter").getFilterValue();
            }

            String globalFilterParam = clientId + ":globalFilter";
            if ((globalVal == null || globalVal.trim().isEmpty()) && params.containsKey(globalFilterParam)) {
                globalVal = params.get(globalFilterParam);
            }

            if (globalVal != null && globalVal.trim().length() >= 3) {
                FilterMeta globalMeta = FilterMeta.builder()
                        .field("globalFilter")
                        .filterValue(globalVal.trim())
                        .matchMode(MatchMode.GLOBAL)
                        .build();
                activeFilters.put("globalFilter", globalMeta);
            }

            if (activeFilters.isEmpty() && rawFilterMap != null) {
                rawFilterMap.remove(rawFilterMap.keySet().stream().findFirst().orElse(null));
            }

            log.trace("Load appelée avec filtres actifs: {}", activeFilters);
            for (Map.Entry<String, FilterMeta> entry : activeFilters.entrySet()) {
                log.trace("\tFiltre : {} : {}", entry.getKey(), entry.getValue().getFilterValue());
            }


            for (Map.Entry<String, SortMeta> entry : sortMetaMap.entrySet()) {
                if (!entry.getValue().getOrder().isUnsorted()) {
                    activeSorts.put(entry.getValue().getSortBy().getValue(context.getELContext()),  entry.getValue());
                }
            }

            log.trace("Load appelée avec tris actifs: {}", activeSorts);
            for (Map.Entry<String, SortMeta> entry : activeSorts.entrySet()) {
                log.trace("\tTri : {} : {}", entry.getKey(), entry.getValue());
            }

            List<? extends AbstractEntityDTO> data = lazyModel.load(first, rows, activeSorts, activeFilters);

            if (activeFilters.isEmpty()) {
                setRowCount(lazyModel.getRowCount());
            } else {
                setRowCount(lazyModel.count(activeFilters));
            }

            if (data != null) {
                lazyRoot = new RootTreeNode(getRowCount(), first);
                for (AbstractEntityDTO elt : data) {
                    ChildTreeNode child = new ChildTreeNode(elt, getLoadMethod(), getIsLeafMethod());
                    child.setParent(lazyRoot);
                    lazyRoot.getChildren().add(child);
                }
                log.trace("Data generated");
                setValue(lazyRoot);

                for (int i = 0; i < lazyModel.getRowCount(); i++) {
                    log.trace("\t - {}", lazyRoot.getChildren().get(i));
                }

                log.trace("New root set");
            }

            log.trace("Il doit y avoir {} enfants", getRowCount());
        }
    }

    @Override
    public void filterAndSort() {
        log.trace("Filter and sort");
    }

    @Override
    public void resetColumns() {
        super.resetColumns();
    }

    @Override
    public boolean hasFooterColumn() {
        return super.hasFooterColumn();
    }

    @Override
    public Locale resolveDataLocale(FacesContext context) {
        return super.resolveDataLocale(context);
    }

    @Override
    public boolean shouldEncodeFeature(FacesContext context) {
        return super.shouldEncodeFeature(context);
    }

    @Override
    protected boolean isCacheableColumns(List<UIColumn> columns) {
        return super.isCacheableColumns(columns);
    }

    @Override
    protected void processNode(FacesContext context, PhaseId phaseId, TreeNode root, TreeNode treeNode, String rowKey) {
        super.processNode(context, phaseId, root, treeNode, rowKey);
    }

    @Override
    protected void processFacets(FacesContext context, PhaseId phaseId, TreeNode root) {
        super.processFacets(context, phaseId, root);
    }

    @Override
    protected void processColumnFacets(FacesContext context, PhaseId phaseId, TreeNode root) {
        super.processColumnFacets(context, phaseId, root);
    }

    @Override
    protected void processColumnChildren(FacesContext context, PhaseId phaseId, TreeNode root, String nodeKey) {
        super.processColumnChildren(context, phaseId, root, nodeKey);
    }

    @Override
    protected void processComponent(FacesContext context, UIComponent component, PhaseId phaseId) {
        super.processComponent(context, component, phaseId);
    }

    @Override
    public boolean invokeOnComponent(FacesContext context, String clientId, ContextCallback callback) throws FacesException {
        return super.invokeOnComponent(context, clientId, callback);
    }

    @Override
    public Map<String, UIComponent> getFacets() {
        return super.getFacets();
    }

    @Override
    public int getFacetCount() {
        return super.getFacetCount();
    }

    @Override
    public UIComponent getFacet(String name) {
        return super.getFacet(name);
    }

    @Override
    public Iterator<UIComponent> getFacetsAndChildren() {
        return super.getFacetsAndChildren();
    }

    @Override
    public boolean visitTree(VisitContext context, VisitCallback callback) {
        return super.visitTree(context, callback);
    }

    @Override
    protected boolean isVisitable(VisitContext context) {
        return super.isVisitable(context);
    }

    @Override
    protected boolean doVisitChildren(VisitContext context) {
        return super.doVisitChildren(context);
    }

    @Override
    protected boolean visitFacets(VisitContext context, Lazy<TreeNode> root, VisitCallback callback, boolean visitNodes) {
        return super.visitFacets(context, root, callback, visitNodes);
    }

    @Override
    protected boolean visitColumnContent(VisitContext context, VisitCallback callback, UIComponent component) {
        return super.visitColumnContent(context, callback, component);
    }

    @Override
    protected boolean visitNodes(VisitContext context, Lazy<TreeNode> root, VisitCallback callback, boolean visitRows) {
        return super.visitNodes(context, root, callback, visitRows);
    }

    @Override
    protected boolean visitNode(VisitContext context, Lazy<TreeNode> root, VisitCallback callback, TreeNode treeNode, String rowKey) {
        return super.visitNode(context, root, callback, treeNode, rowKey);
    }

    @Override
    public boolean isFilteringCurrentlyActive() {
        if (isLazy() && blockFiltering) return false;
        return super.isFilteringCurrentlyActive();
    }

    @Override
    public boolean isFilteringEnabled() {
        if (isLazy() && blockFiltering) {
            return false;
        }
        return super.isFilteringEnabled();
    }

    @Override
    public void encodeEnd(FacesContext context) throws IOException {
        this.blockFiltering = true;

        try {
            super.encodeEnd(context);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        } finally {
            this.blockFiltering = false;
        }
    }

    @Override
    public void encodeAll(FacesContext context) throws IOException {
        super.encodeAll(context);
    }

    @Override
    public void pushComponentToEL(FacesContext context, UIComponent component) {
        super.pushComponentToEL(context, component);
    }

    @Override
    public void popComponentFromEL(FacesContext context) {
        super.popComponentFromEL(context);
    }

    @Override
    protected void addFacesListener(FacesListener listener) {
        super.addFacesListener(listener);
    }

    @Override
    protected FacesListener[] getFacesListeners(Class clazz) {
        return super.getFacesListeners(clazz);
    }

    @Override
    protected void removeFacesListener(FacesListener listener) {
        super.removeFacesListener(listener);
    }

    @Override
    public TreeNode getFilteredValue() {
        if (isLazy()) {
            return getValue();
        }
        return super.getFilteredValue();
    }

    @Override
    public void setFilteredValue(TreeNode<?> filteredValue) {
        if (!isLazy()) {
            super.setFilteredValue(filteredValue);
        }
    }

    @Override
    public String getFilterEvent() {
        return super.getFilterEvent();
    }

    @Override
    public void setFilterEvent(String filterEvent) {
        super.setFilterEvent(filterEvent);
    }

    @Override
    public int getFilterDelay() {
        return super.getFilterDelay();
    }

    @Override
    public void setFilterDelay(int filterDelay) {
        super.setFilterDelay(filterDelay);
    }

    @Override
    public String getCellEditMode() {
        return super.getCellEditMode();
    }

    @Override
    public void setCellEditMode(String cellEditMode) {
        super.setCellEditMode(cellEditMode);
    }

    @Override
    public String getEditInitEvent() {
        return super.getEditInitEvent();
    }

    @Override
    public void setEditInitEvent(String editInitEvent) {
        super.setEditInitEvent(editInitEvent);
    }

    @Override
    public boolean isMultiViewState() {
        return super.isMultiViewState();
    }

    @Override
    public void setMultiViewState(boolean multiViewState) {
        super.setMultiViewState(multiViewState);
    }

    @Override
    public Map<String, FilterMeta> initFilterBy(FacesContext context) {
        return super.initFilterBy(context);
    }

    @Override
    public void updateFilterByWithMVS(FacesContext context, Map<String, FilterMeta> tsFilterBy) {
        super.updateFilterByWithMVS(context, tsFilterBy);
    }

    @Override
    public void updateFilterByWithUserFilterBy(FacesContext context, Map<String, FilterMeta> intlFilterBy, Object usrFilterBy) {
        super.updateFilterByWithUserFilterBy(context, intlFilterBy, usrFilterBy);
    }

    @Override
    public void updateFilterByWithGlobalFilter(FacesContext context, Map<String, FilterMeta> filterBy) {
        super.updateFilterByWithGlobalFilter(context, filterBy);
    }

    @Override
    public boolean isColumnFilterable(FacesContext context, UIColumn column) {
        return super.isColumnFilterable(context, column);
    }

    @Override
    public void updateFilterByValuesWithFilterRequest(FacesContext context, Map<String, FilterMeta> filterBy) {
        super.updateFilterByValuesWithFilterRequest(context, filterBy);
    }

    @Override
    public Object getFilterValue(UIColumn column) {
        return super.getFilterValue(column);
    }

    @Override
    public Object getFilterBy() {
        return super.getFilterBy();
    }

    @Override
    public void setFilterBy(Object filterBy) {
        super.setFilterBy(filterBy);
    }

    @Override
    public String getGlobalFilter() {
        return super.getGlobalFilter();
    }

    @Override
    public void setGlobalFilter(String globalFilter) {
        super.setGlobalFilter(globalFilter);
    }

    @Override
    public MethodExpression getGlobalFilterFunction() {
        return super.getGlobalFilterFunction();
    }

    @Override
    public void setGlobalFilterFunction(MethodExpression globalFilterFunction) {
        super.setGlobalFilterFunction(globalFilterFunction);
    }

    @Override
    public boolean isGlobalFilterOnly() {
        return super.isGlobalFilterOnly();
    }

    @Override
    public void setGlobalFilterOnly(boolean globalFilterOnly) {
        super.setGlobalFilterOnly(globalFilterOnly);
    }

    @Override
    public boolean isAllowUnsorting() {
        return super.isAllowUnsorting();
    }

    @Override
    public void setAllowUnsorting(boolean allowUnsorting) {
        super.setAllowUnsorting(allowUnsorting);
    }

    @Override
    public String getSortMode() {
        return super.getSortMode();
    }

    @Override
    public void setSortMode(String sortMode) {
        super.setSortMode(sortMode);
    }

    @Override
    public boolean isCloneOnFilter() {
        return super.isCloneOnFilter();
    }

    @Override
    public void setCloneOnFilter(boolean cloneOnFilter) {
        super.setCloneOnFilter(cloneOnFilter);
    }

    @Override
    public boolean isSaveOnCellBlur() {
        return super.isSaveOnCellBlur();
    }

    @Override
    public void setSaveOnCellBlur(boolean saveOnCellBlur) {
        super.setSaveOnCellBlur(saveOnCellBlur);
    }

    @Override
    public boolean isShowGridlines() {
        return super.isShowGridlines();
    }

    @Override
    public void setShowGridlines(boolean showGridlines) {
        super.setShowGridlines(showGridlines);
    }

    @Override
    public String getSize() {
        return super.getSize();
    }

    @Override
    public void setSize(String size) {
        super.setSize(size);
    }

    @Override
    public String getExportRowTag() {
        return super.getExportRowTag();
    }

    @Override
    public void setExportRowTag(String exportRowTag) {
        super.setExportRowTag(exportRowTag);
    }

    @Override
    public String getExportTag() {
        return super.getExportTag();
    }

    @Override
    public void setExportTag(String exportTag) {
        super.setExportTag(exportTag);
    }

    @Override
    public boolean isFilterNormalize() {
        return super.isFilterNormalize();
    }

    @Override
    public Map<String, SortMeta> initSortBy(FacesContext context) {
        return super.initSortBy(context);
    }

    @Override
    public void updateSortByWithMVS(Map<String, SortMeta> tsSortBy) {
        super.updateSortByWithMVS(tsSortBy);
    }

    @Override
    public void updateSortByWithUserSortBy(FacesContext context, Map<String, SortMeta> intlSortBy, Object usrSortBy, AtomicBoolean sorted) {
        super.updateSortByWithUserSortBy(context, intlSortBy, usrSortBy, sorted);
    }

    @Override
    public SortMeta getHighestPriorityActiveSortMeta() {
        return super.getHighestPriorityActiveSortMeta();
    }

    @Override
    public Map<String, SortMeta> getActiveSortMeta() {
        return super.getActiveSortMeta();
    }

    @Override
    public boolean isSortingCurrentlyActive() {
        return super.isSortingCurrentlyActive();
    }

    @Override
    public boolean isColumnSortable(FacesContext context, UIColumn column) {
        return super.isColumnSortable(context, column);
    }

    @Override
    public String getSortMetaAsString() {
        return super.getSortMetaAsString();
    }

    @Override
    public boolean isSortingEnabled() {
        return super.isSortingEnabled();
    }

    @Override
    public HeaderRow getHeaderRow() {
        return super.getHeaderRow();
    }

    @Override
    public void setFilterNormalize(boolean filterNormalize) {
        super.setFilterNormalize(filterNormalize);
    }

}