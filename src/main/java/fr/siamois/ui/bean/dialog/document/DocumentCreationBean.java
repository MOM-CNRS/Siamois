package fr.siamois.ui.bean.dialog.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.ActionFromBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.DocumentUtils;
import fr.siamois.utils.MessageUtils;
import jakarta.servlet.ServletContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.file.UploadedFile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class DocumentCreationBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient DocumentService documentService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final LangBean langBean;
    private final transient ServletContext servletContext;
    private final transient ConceptService conceptService;
    private final transient ArkService arkService;
    private final transient FieldService fieldService;
    private String docTitle;
    private ConceptAutocompleteDTO docNature;
    private ConceptAutocompleteDTO docScale;
    private ConceptAutocompleteDTO docType;
    private String docDescription;

    private transient ActionFromBean actionOnSave = null;
    private transient UploadedFile docFile;
    private String panelIdToUpdate;

    public void init() {
        PrimeFaces.current().ajax().update("newDocumentDiag");
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


    public String getUrlForConcept(Concept concept) {
        return fieldConfigurationService.getUrlOfConcept(concept);
    }


    public List<ConceptAutocompleteDTO>  autocomplete(String fieldCode, String input) throws NoConfigForFieldException {

        return fieldConfigurationService.fetchAutocomplete(
                sessionSettingsBean.getUserInfo(), fieldCode, input
        );

    }

    public List<ConceptAutocompleteDTO> autocompleteNature(String input) throws NoConfigForFieldException {
        return autocomplete(Document.NATURE_FIELD_CODE, input);
    }

    public List<ConceptAutocompleteDTO> autocompleteScale(String input) throws NoConfigForFieldException {
        return autocomplete(Document.SCALE_FIELD_CODE, input);
    }

    public List<ConceptAutocompleteDTO> autocompleteType(String input) throws NoConfigForFieldException {
        return autocomplete(Document.FORMAT_FIELD_CODE, input);
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
        return Document.MAX_DESCRIPTION_LENGTH;
    }

    public int maxTitleLength() {
        return Document.MAX_TITLE_LENGTH;
    }

    public void callActionOnSave() {
        actionOnSave.apply();
    }

}
