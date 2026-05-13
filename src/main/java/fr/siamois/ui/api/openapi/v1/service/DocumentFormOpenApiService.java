package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormCurrentValuesApi;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormData;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormFieldApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formulaire OpenAPI document : mêmes vocabulaires que le dialogue web (codes SIAD.* via {@link FieldConfigurationService}).
 */
@Service
@RequiredArgsConstructor
public class DocumentFormOpenApiService {

    private final FieldConfigurationService fieldConfigurationService;
    private final InstitutionService institutionService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final ConceptMapper conceptMapper;

    @Transactional(readOnly = true)
    public DocumentFormData buildForm(PersonDTO person,
                                      long organizationId,
                                      Set<Long> accessibleInstitutionIds,
                                      String lang,
                                      Long documentId) {
        var institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        UserInfo userInfo = new UserInfo(institution, person, lang);

        Map<String, List<ConceptAutocompleteDTO>> vocabs = new LinkedHashMap<>();
        putVocabulary(userInfo, vocabs, Document.NATURE_FIELD_CODE);
        putVocabulary(userInfo, vocabs, Document.SCALE_FIELD_CODE);
        putVocabulary(userInfo, vocabs, Document.FORMAT_FIELD_CODE);

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

        return new DocumentFormData(staticFields(), vocabs, current);
    }

    private static List<DocumentFormFieldApi> staticFields() {
        List<DocumentFormFieldApi> fields = new ArrayList<>();
        fields.add(new DocumentFormFieldApi("title", "TEXT", null, Document.MAX_TITLE_LENGTH));
        fields.add(new DocumentFormFieldApi("description", "TEXTAREA", null, Document.MAX_DESCRIPTION_LENGTH));
        fields.add(new DocumentFormFieldApi("nature", "CONCEPT_SELECT", Document.NATURE_FIELD_CODE, null));
        fields.add(new DocumentFormFieldApi("scale", "CONCEPT_SELECT", Document.SCALE_FIELD_CODE, null));
        fields.add(new DocumentFormFieldApi("format", "CONCEPT_SELECT", Document.FORMAT_FIELD_CODE, null));
        fields.add(new DocumentFormFieldApi("file", "FILE", null, null));
        return List.copyOf(fields);
    }

    private void putVocabulary(UserInfo userInfo, Map<String, List<ConceptAutocompleteDTO>> out, String fieldCode) {
        try {
            out.put(fieldCode, fieldConfigurationService.fetchAutocomplete(userInfo, fieldCode, null));
        } catch (NoConfigForFieldException e) {
            out.put(fieldCode, List.of());
        }
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
