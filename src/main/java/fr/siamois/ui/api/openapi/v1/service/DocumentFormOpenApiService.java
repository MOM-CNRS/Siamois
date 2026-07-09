package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentFormCurrentValuesApi;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentFormData;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentFormFieldApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Formulaire OpenAPI document : définition des champs (vocabulaires via GET /api/v1/vocabularies).
 */
@Service
@RequiredArgsConstructor
public class DocumentFormOpenApiService {

    public static final String CONCEPT_SELECT = "CONCEPT_SELECT";
    private final InstitutionService institutionService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final ConceptMapper conceptMapper;

    @Transactional(readOnly = true)
    public DocumentFormData buildForm(PersonDTO person,
                                      long organizationId,
                                      Set<Long> accessibleInstitutionIds,
                                      String lang,
                                      Long documentId) {
        if (institutionService.findById(organizationId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }

        DocumentFormCurrentValuesApi current = null;
        if (documentId != null) {
            Document doc = documentContentOpenApiService.requireAccessibleDocument(documentId, accessibleInstitutionIds);
            current = new DocumentFormCurrentValuesApi(
                    doc.getTitle(),
                    doc.getDescription(),
                    toAutocomplete(doc.getNature(), lang),
                    toAutocomplete(doc.getScale(), lang),
                    toAutocomplete(doc.getFormat(), lang),
                    doc.getFileName(),
                    doc.getMimeType());
        }

        return new DocumentFormData(staticFields(), current);
    }

    private static List<DocumentFormFieldApi> staticFields() {
        return List.of(
                new DocumentFormFieldApi("title", "TEXT", null, Document.MAX_TITLE_LENGTH),
                new DocumentFormFieldApi("description", "TEXTAREA", null, Document.MAX_DESCRIPTION_LENGTH),
                new DocumentFormFieldApi("nature", CONCEPT_SELECT, Document.NATURE_FIELD_CODE, null),
                new DocumentFormFieldApi("scale", CONCEPT_SELECT, Document.SCALE_FIELD_CODE, null),
                new DocumentFormFieldApi("format", CONCEPT_SELECT, Document.FORMAT_FIELD_CODE, null),
                new DocumentFormFieldApi("file", "FILE", null, null)
        );
    }

    private ConceptAutocompleteDTO toAutocomplete(Concept concept, String lang) {
        if (concept == null) {
            return null;
        }
        ConceptDTO dto = conceptMapper.convert(concept);
        String label = dto.getExternalId() != null ? dto.getExternalId() : "";
        return new ConceptAutocompleteDTO(dto, label, lang);
    }
}
