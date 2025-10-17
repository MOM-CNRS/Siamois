package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.panel.single.tab.*;
import io.micrometer.common.lang.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.event.TabChangeEvent;
import org.springframework.util.MimeType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Slf4j
public abstract class AbstractSingleEntityPanel<T> extends AbstractSingleEntity<T>  implements Serializable {

    public static final String RECORDING_UNIT_FORM_RECORDING_UNIT_TABS = "recordingUnitForm:recordingUnitTabs";
    // Deps
    protected final transient DocumentCreationBean documentCreationBean;
    protected final transient HistoryAuditService historyAuditService;

    //--------------- Locals

    protected Integer activeTabIndex; // Keeping state of active tab
    protected transient T backupClone;
    protected String errorMessage;
    protected transient List<RevisionWithInfo<T>> history;
    protected transient RevisionWithInfo<T> revisionToDisplay = null;
    protected Long idunit;  // ID of the spatial unit
    protected transient List<Document> documents;

    // lazy model for children of entity
    protected long totalChildrenCount = 0;
    protected transient List<Concept> selectedCategoriesChildren;


    // lazy model for parents of entity
    protected long totalParentsCount = 0;
    protected transient List<Concept> selectedCategoriesParents;

    protected transient List<PanelTab> tabs;

    protected transient RevisionWithInfo<T> bufferedLastRevision;

    @Override
    public String display() {
        return "/panel/singleUnitPanel.xhtml";
    }

    public abstract void init();

    public abstract List<Person> authorsAvailable();

    public static final Vocabulary SYSTEM_THESO;

    static {
        SYSTEM_THESO = new Vocabulary();
        SYSTEM_THESO.setBaseUri("https://thesaurus.mom.fr/");
        SYSTEM_THESO.setExternalVocabularyId("th230");
    }

    protected static final String COLUMN_CLASS_NAME = "ui-g-12 ui-md-6 ui-lg-4";

    protected AbstractSingleEntityPanel(String titleCodeOrTitle,
                                        String icon, String panelClass,
                                        DocumentCreationBean documentCreationBean,
                                        AbstractSingleEntity.Deps deps,
                                        HistoryAuditService historyAuditService) {
        super(titleCodeOrTitle, icon, panelClass, deps);
        this.documentCreationBean = documentCreationBean;
        this.historyAuditService = historyAuditService;

        // Overview tab
        tabs = new ArrayList<>();
        OverviewFormTab overviewTab = new OverviewFormTab("panel.tab.overview",
                "bi bi-eye",
                "overviewTab");
        tabs.add(overviewTab);
        DetailsFormTab detailsTab = new DetailsFormTab("panel.tab.details",
                "bi bi-pen",
                "detailTab");
        tabs.add(detailsTab);
        DocumentTab documentTab = new DocumentTab("panel.tab.documents",
                "bi bi-paperclip",
                "documentsTab");
        tabs.add(documentTab);
        if(activeTabIndex == null) { activeTabIndex = 1; }
    }


    public abstract void initForms();

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
        PrimeFaces.current().ajax().update("spatialUnitForm");
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
    public String lastUpdateDate() {
        bufferedLastRevision = (RevisionWithInfo<T>) historyAuditService.findLastRevisionForEntity(unit.getClass(), idunit);
        return bufferedLastRevision.getDate().toString();
    }

    public String lastUpdater() {
        String result = bufferedLastRevision.revisionEntity().getUpdatedBy().displayName();
        bufferedLastRevision = null;
        return result;
    }
}
