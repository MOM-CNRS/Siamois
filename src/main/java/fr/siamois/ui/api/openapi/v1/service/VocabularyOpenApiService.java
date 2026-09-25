package fr.siamois.ui.api.openapi.v1.service;

import org.hibernate.Hibernate;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
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
    private final CustomFieldRepository customFieldRepository;

    /**
     * Vocabulaires complets pour une organisation : catalogue des thésaurus et concepts par field_code
     * (configuration institution / utilisateur), pour alimenter les formulaires.
     *
     * @deprecated Non scopé par projet, sans pagination/suggestion. À remplacer par
     * {@link #getAvailableFieldCodesForOrganization} et {@link #getConceptsForOrganization}, utilisés
     * par {@code GET /api/v1/projects/{id}/field-codes} et {@code GET /api/v1/projects/{id}/concepts}.
     */
    @Deprecated(forRemoval = true)
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

    /**
     * The suggestions of one vocabulary field, resolved the way the JSF form resolves them
     * ({@link FieldConfigurationService#fetchAutocomplete(CustomFieldConcept, String, Long, Long)}):
     * the field's own branch/collection restriction first, then the project's thesaurus, then its
     * field code. The only path for a vocabulary field that has no field code of its own.
     *
     * @param projectId      the project the edited entity belongs to, if any (project thesaurus)
     * @param valueConceptId the entity's type concept, if any (type-scoped restriction)
     */
    @Transactional(readOnly = true)
    public List<ConceptAutocompleteDTO> getConceptsForField(long organizationId,
                                                            long fieldId,
                                                            @Nullable Long projectId,
                                                            @Nullable Long valueConceptId,
                                                            @Nullable String q,
                                                            String lang,
                                                            PersonDTO person) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        CustomField field = customFieldRepository.findById(fieldId)
                .map(f -> (CustomField) Hibernate.unproxy(f))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Champ introuvable : " + fieldId));
        if (!(field instanceof CustomFieldConcept conceptField)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ " + fieldId + " n'est pas un champ de vocabulaire");
        }
        UserInfo userInfo = new UserInfo(institution, person, lang);
        return OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            try {
                return fieldConfigurationService.fetchAutocomplete(conceptField, q, projectId, valueConceptId);
            } catch (NoConfigForFieldException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun vocabulaire configuré pour le champ : " + fieldId);
            }
        });
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
