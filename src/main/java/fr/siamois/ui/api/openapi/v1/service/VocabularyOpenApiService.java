package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.mapper.VocabularyOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vocabulaires contrôlés OpenAPI (field_code → concepts), alignés sur {@link FieldConfigurationService}.
 */
@Service
@RequiredArgsConstructor
public class VocabularyOpenApiService {

    private final ProjectApiService projectApiService;
    private final InstitutionService institutionService;
    private final FieldConfigurationService fieldConfigurationService;
    private final VocabularyService vocabularyService;
    private final VocabularyOpenApiMapper vocabularyOpenApiMapper;

    /**
     * Vocabulaires complets pour une organisation : catalogue des thésaurus et concepts par field_code
     * (configuration institution / utilisateur), pour alimenter les formulaires.
     */
    @Transactional(readOnly = true)
    public VocabulariesResponse listOrganizationVocabularies(ProjectApiCaller caller,
                                                             long organizationId,
                                                             String lang) {
        requireOrganizationInScope(organizationId, caller);
        return new VocabulariesResponse(buildVocabulariesData(organizationId, caller.person(), lang));
    }

    @Transactional(readOnly = true)
    public VocabulariesData listVocabulariesForOrganization(long organizationId,
                                                            PersonDTO personDto,
                                                            String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        return buildVocabulariesData(organizationId, personDto, lang);
    }

    private VocabulariesData buildVocabulariesData(long organizationId, PersonDTO personDto, String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode =
                fieldConfigurationService.fetchAllConfiguredVocabularies(userInfo);
        List<String> fieldCodes = new ArrayList<>(vocabulariesByFieldCode.keySet());
        List<VocabularyResource> catalog = vocabularyService.findAllByInstitutionId(organizationId).stream()
                .map(v -> vocabularyOpenApiMapper.toResource(v, lang))
                .toList();
        return new VocabulariesData(
                String.valueOf(organizationId),
                fieldCodes,
                vocabulariesByFieldCode,
                catalog);
    }

    @Transactional(readOnly = true)
    public List<ConceptAutocompleteDTO> getConceptsForOrganization(long organizationId,
                                                                    String fieldCode,
                                                                    @Nullable String q,
                                                                    String lang,
                                                                    PersonDTO person) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        UserInfo userInfo = new UserInfo(institution, person, lang);
        try {
            return fieldConfigurationService.fetchAutocomplete(userInfo, fieldCode, q);
        } catch (NoConfigForFieldException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucun vocabulaire configuré pour le fieldCode : " + fieldCode);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableFieldCodesForOrganization(long organizationId,
                                                               String lang,
                                                               PersonDTO person) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        UserInfo userInfo = new UserInfo(institution, person, lang);
        return new ArrayList<>(fieldConfigurationService.fetchAllConfiguredVocabularies(userInfo).keySet());
    }

    private void requireOrganizationInScope(long organizationId, ProjectApiCaller caller) {
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }
    }
}
