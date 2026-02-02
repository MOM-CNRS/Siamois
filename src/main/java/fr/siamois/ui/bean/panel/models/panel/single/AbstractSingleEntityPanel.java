package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.files.DocumentStorage;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.single.tab.*;
import io.micrometer.common.lang.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.RevisionType;
import org.primefaces.PrimeFaces;
import org.primefaces.event.TabChangeEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.menu.DefaultMenuItem;
import org.primefaces.model.menu.DefaultMenuModel;
import org.primefaces.model.menu.MenuModel;
import org.springframework.context.ApplicationContext;
import org.springframework.util.MimeType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Slf4j
public abstract class AbstractSingleEntityPanel<T extends TraceableEntity> extends AbstractSingleEntity<T>  implements Serializable {

    public static final String RECORDING_UNIT_FORM_RECORDING_UNIT_TABS = "recordingUnitForm:recordingUnitTabs";
    // Deps
    protected final transient DocumentCreationBean documentCreationBean;
    protected final transient HistoryAuditService historyAuditService;
    protected final transient FieldService fieldService;
    protected final transient ConceptService conceptService;
    protected final transient FlowBean flowBean;

    //--------------- Locals

    public static final String SPATIAL = "SPATIAL";
    public static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    public static final String PF_BUI_CONTENT_HIDE = "PF('buiContent').hide()";
    public static final String THIS = "@this";

    protected Integer activeTabIndex; // Keeping state of active tab
    protected transient T backupClone;
    protected String errorMessage;
    protected transient List<RevisionWithInfo<T>> history;
    protected transient RevisionWithInfo<T> revisionToDisplay = null;
    protected Long idunit;  // ID of the spatial unit
    protected transient List<Document> documents;
    protected transient Map<String, ConceptFieldConfig> fieldConfigs = new HashMap<>();

    // lazy model for children of entity
    protected long totalChildrenCount = 0;
    protected transient List<Concept> selectedCategoriesChildren;


    // lazy model for parents of entity
    protected long totalParentsCount = 0;
    protected transient List<Concept> selectedCategoriesParents;

    protected transient List<PanelTab> tabs;

    protected transient RevisionWithInfo<T> bufferedLastRevision;

    public abstract void refreshUnit();

    public void refresh() {
        refreshUnit();
    }

    @Override
    public String display() {
        return "/panel/singleUnitPanel.xhtml";
    }



    public abstract void init();

    public abstract List<Person> authorsAvailable();

    public static final Vocabulary SYSTEM_THESO;

    static {
        SYSTEM_THESO = new Vocabulary();
        SYSTEM_THESO.setBaseUri("https://thesaurus.mom.fr");
        SYSTEM_THESO.setExternalVocabularyId("th230");
    }

    protected static final String COLUMN_CLASS_NAME = "ui-g-12 ui-md-6 ui-lg-4";

        /*
    Find unit by its ID
     */
    abstract T findUnitById(Long id);

    /*
    Get label of unit to display in breadcrumn
     */
    abstract String findLabel(T unit);

    protected abstract DefaultMenuItem createRootTypeItem();

    /*
Return the command that opens panel for the unit
 */
    abstract String getOpenPanelCommand(T unit);

    public List<MenuModel> getAllParentBreadcrumbModels() {

        MenuModel breadcrumbModel = new DefaultMenuModel();
        breadcrumbModel.getElements().add(createHomeItem());
        breadcrumbModel.getElements().add(createRootTypeItem());
        T currentUnit = findUnitById(idunit);

        if (currentUnit != null) {
            breadcrumbModel.getElements().add(createUnitItem(currentUnit));
        }

        return List.of(breadcrumbModel);
    }

    protected DefaultMenuItem createHomeItem() {
        return DefaultMenuItem.builder()
                .value("")
                .id("home")
                .icon("bi bi-house")
                .command("#{flowBean.addWelcomePanel()}")
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    protected DefaultMenuItem createUnitItem(T unit) {
        return DefaultMenuItem.builder()
                .value(findLabel(unit))
                .id(String.valueOf(unit.getId()))
                .command(getOpenPanelCommand(unit))
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    protected AbstractSingleEntityPanel(String titleCodeOrTitle,
                                        String icon, String panelClass,
                                        ApplicationContext context) {
        super(titleCodeOrTitle, icon, panelClass, context);
        this.documentCreationBean = context.getBean(DocumentCreationBean.class);
        this.historyAuditService = context.getBean(HistoryAuditService.class);
        this.fieldService = context.getBean(FieldService.class);
        this.conceptService = context.getBean(ConceptService.class);
        this.flowBean = context.getBean(FlowBean.class);

        // Overview tab
        tabs = new ArrayList<>();
        DetailsFormTab detailsTab = new DetailsFormTab("panel.tab.details",
                "bi bi-pen",
                "detailTab");
        tabs.add(detailsTab);
        DocumentTab documentTab = new DocumentTab("panel.tab.documents",
                "bi bi-paperclip",
                "documentsTab");
        tabs.add(documentTab);
        if(activeTabIndex == null) { activeTabIndex = 0; }
    }


    public abstract void initForms(boolean forceInit);

    public abstract void cancelChanges();

    public abstract void visualise(RevisionWithInfo<T> history);

    /**
     * Save the current entity in the database.
     * @param validated Set to true if the entity is validated.
     * @return true if the entity has been saved, false if any error occurred
     */
    public abstract boolean save(Boolean validated);

    public void saveAction(boolean validated) {
        save(validated);
    }

    public boolean contentIsImage(String mimeType) {
        MimeType currentMimeType = MimeType.valueOf(mimeType);
        return currentMimeType.getType().equals("image");
    }

    protected abstract boolean documentExistsInUnitByHash(T unit, String hash);

    protected abstract void addDocumentToUnit(Document doc, T unit);

    public void saveDocument() {
        try {
            BufferedInputStream currentFile = new BufferedInputStream(documentCreationBean.getDocFile().getInputStream());
            String hash = documentService.getMD5Sum(currentFile);
            currentFile.mark(Integer.MAX_VALUE);
            if (documentExistsInUnitByHash(unit, hash)) {
                log.error("Document already exists in spatial unit");
                currentFile.reset();
                return;
            }
        } catch (IOException e) {
            log.error("Error while processing spatial unit document", e);
            return;
        }

        Document created = documentCreationBean.createDocument();
        if (created == null)
            return;

        log.trace("Document created: {}", created);
        addDocumentToUnit(created, unit);
        log.trace("Document added to unit: {}", unit);

        documents.add(created);
        PrimeFaces.current().executeScript("PF('newDocumentDiag').hide()");

        int pos = flowBean.getPanels().indexOf(this);
        if(pos >=0) { PrimeFaces.current().ajax().update("panel-".concat(Integer.toString(pos))); }


    }

    public Integer getIndexOfTab(PanelTab tab) {
        return tabs.indexOf(tab);
    }


    public void initDialog() throws NoConfigForFieldException {
        log.trace("initDialog");
        documentCreationBean.init();

        documentCreationBean.setActionOnSave(this::saveDocument);

        PrimeFaces.current().executeScript("PF('newDocumentDiag').show()");
    }

    public Boolean isHierarchyTabEmpty() {
        return (totalChildrenCount + totalParentsCount) == 0;
    }


    public void onTabChange(TabChangeEvent<?> event) {
        activeTabIndex = event.getIndex();
    }

    @Nullable
    public Boolean emptyTabFor(PanelTab tabItem) {
        if (tabItem instanceof MultiHierarchyTab) return isHierarchyTabEmpty();
        if (tabItem instanceof DocumentTab) return documents.isEmpty();
        if(tabItem instanceof EntityListTab) return ((EntityListTab<?>) tabItem).getTotalCount() == 0;
        return null; // N/A for others
    }

    @SuppressWarnings("unchecked")
    private RevisionWithInfo<T> findLastRevisionForEntity() {
        RevisionWithInfo<T> result = (RevisionWithInfo<T>) historyAuditService.findLastRevisionForEntity(unit.getClass(), idunit);
        if (result == null) {
            InfoRevisionEntity info = new InfoRevisionEntity();
            UserInfo userInfo = sessionSettingsBean.getUserInfo();
            info.setRevId(0L);
            info.setEpochTimestamp(OffsetDateTime.now().toEpochSecond());
            info.setUpdatedBy(userInfo.getUser());
            info.setUpdatedFrom(userInfo.getInstitution());
            result = new RevisionWithInfo<>(unit, info, RevisionType.MOD);
        }
        return result;
    }

    public String lastUpdateDate() {
        bufferedLastRevision = findLastRevisionForEntity();
        return this.formatUtcDateTime(bufferedLastRevision.getDate());
    }

    public String lastUpdater() {
        if (bufferedLastRevision == null) {
            bufferedLastRevision = findLastRevisionForEntity();
        }
        String result = bufferedLastRevision.revisionEntity().getUpdatedBy().displayName();
        bufferedLastRevision = null;
        return result;
    }

    /**
     * Returns all the persons contributing to the unit
     * @return the list of contributors as a string
     */
    public String allUpdaters() {
        return historyAuditService.findAllContributorsFor(unit.getClass(), idunit)
                .stream()
                .map(Person::displayName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns multi hierarchy tab parent table
     * @return parent table
     */
    public Object parentTableModelOf(Object tabItem) {
        if (tabItem instanceof MultiHierarchyTab t) {
            return t.getParentTableModel();
        }
        return null;
    }

    /**
     * Returns multi hierarchy tab childen table
     * @return child table
     */
    public Object childTableModelOf(Object tabItem) {
        if (tabItem instanceof MultiHierarchyTab t) {
            return t.getChildTableModel();
        }
        return null;
    }

    // Get the tabview view name for this panel
    public abstract String getTabView() ;

    public DefaultStreamedContent streamOf(Document document) {

        Optional<InputStream> opt = documentService.findInputStreamOfDocument(document);
        if (opt.isPresent()) {
            InputStream inputStream = opt.get();
            return DefaultStreamedContent.builder()
                    .stream(() -> inputStream)
                    .contentType(document.getMimeType()) // Set the correct content type
                    .name(document.getFileName()) // Set the filename
                    .build();
        }
        return null;
    }


}
