package fr.siamois.ui.bean.dialog.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.document.DocumentParent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.ui.bean.ActionFromBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.DocumentUtils;
import fr.siamois.utils.MessageUtils;
import jakarta.servlet.ServletContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.file.UploadedFile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import javax.faces.bean.SessionScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;

@Slf4j
@Component
@Getter
@Setter
@SessionScoped
public class DocumentCreationBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient DocumentService documentService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final LangBean langBean;
    private final transient ServletContext servletContext;
    private final transient ConceptService conceptService;
    private final transient ArkService arkService;
    private String docTitle;
    private Concept docNature;
    private Concept docScale;
    private Concept docType;
    private String docDescription;

    private transient ActionFromBean actionOnSave = null;
    private Concept parentNature = null;
    private Concept parentScale = null;
    private Concept parentType = null;
    private transient UploadedFile docFile;
    private String panelIdToUpdate ;

    public DocumentCreationBean(SessionSettingsBean sessionSettingsBean, DocumentService documentService, FieldConfigurationService fieldConfigurationService, LangBean langBean, ServletContext servletContext, ConceptService conceptService, ArkService arkService) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.documentService = documentService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.langBean = langBean;
        this.servletContext = servletContext;
        this.conceptService = conceptService;
        this.arkService = arkService;
    }

    public void init() throws NoConfigForFieldException {
        PrimeFaces.current().ajax().update("newDocumentDiag");
        prepareParentConcept();
        reset();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        docTitle = null;
        docNature = null;
        docScale = null;
        docType = null;
        docFile = null;
        docDescription = null;
    }

    private void prepareParentConcept() throws NoConfigForFieldException {
        UserInfo info = sessionSettingsBean.getUserInfo();
        parentNature = fieldConfigurationService.findConfigurationForFieldCode(info, Document.NATURE_FIELD_CODE);
        parentScale = fieldConfigurationService.findConfigurationForFieldCode(info, Document.SCALE_FIELD_CODE);
        parentType = fieldConfigurationService.findConfigurationForFieldCode(info, Document.FORMAT_FIELD_CODE);
    }

    public String getUrlForConcept(Concept concept) {
        return fieldConfigurationService.getUrlOfConcept(concept);
    }


    public List<Concept> autocomplete(Concept parent, String input) {
        log.trace("Autocomplete order received");

        return fieldConfigurationService.fetchAutocomplete(
                sessionSettingsBean.getUserInfo(),
                parent,
                input);
    }

    public List<Concept> autocompleteNature(String input) {
        return autocomplete(parentNature, input);
    }

    public List<Concept> autocompleteScale(String input) {
        return autocomplete(parentScale, input);
    }

    public List<Concept> autocompleteType(String input) {
        return autocomplete(parentType, input);
    }

    public Document createDocument() {
        if (docFile == null) {
            MessageUtils.displayErrorMessage(langBean, "documents.nofileset");
            return null;
        }

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        Document document = DocumentUtils.prepareDocumentFrom(conceptService, docFile, this);

        if (Boolean.TRUE.equals(sessionSettingsBean.getInstitutionSettings().getArkIsEnabled())) {
            Ark ark = arkService.generateAndSave(sessionSettingsBean.getInstitutionSettings());
            document.setArk(ark);
        }

        try (InputStream inputStream = docFile.getInputStream()) {
            document = documentService.saveFile(userInfo, document, inputStream, servletContext.getContextPath());
            reset();
            return document;
        } catch (InvalidFileTypeException e) {
            log.error("Invalid file type {}", e.getMessage());
            MessageUtils.displayErrorMessage(langBean, "documents.unsupportedtype");
        } catch (InvalidFileSizeException e) {
            log.error("Invalid file size {}", e.getMessage());
            MessageUtils.displayErrorMessage(langBean, "documents.toolarge");
        } catch (IOException e) {
            log.error("IO Exception {}", e.getMessage());
            MessageUtils.displayInternalError(langBean);
        }

        return null;

    }

    public String regexSupportedTypes() {
        List<MimeType> supported = documentService.supportedMimeTypes();
        return DocumentUtils.allowedTypesRegex(supported);
    }

    public String allowedExtensions() {
        List<MimeType> supported = documentService.supportedMimeTypes();
        return DocumentUtils.allowedExtensionsStringList(supported);
    }

    public long maxFileSize() {
        return documentService.maxFileSize();
    }

    public int maxDescriptionSize() {
        return DocumentParent.MAX_DESCRIPTION_LENGTH;
    }

    public int maxTitleLength() {
        return DocumentParent.MAX_TITLE_LENGTH;
    }

    public void callActionOnSave() {
        actionOnSave.apply();
    }

}
