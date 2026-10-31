package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.VocabularyOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import fr.siamois.ui.form.dto.CustomColUiDto;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Stream;

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
    private final ConceptService conceptService;

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
        return conceptsForField(organizationId, fieldId, projectId, valueConceptId, q, lang, person);
    }

    private List<ConceptAutocompleteDTO> conceptsForField(long organizationId,
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
        CustomField field = findField(fieldId);
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

    /**
     * The suggestions of a field whose list depends on the type of the record (rule {@code options: RELATED_TO_TYPE}):
     * the values the thesaurus relates to {@code typeConceptId}. A type related to none of the field's values, or a
     * field with no field code to read the relation from, gets the field's whole list
     * ({@link #getConceptsForField}).
     */
    @Transactional
    public List<ConceptAutocompleteDTO> getConceptsForFieldRelatedToType(long organizationId,
                                                                         long fieldId,
                                                                         @Nullable Long projectId,
                                                                         long typeConceptId,
                                                                         @Nullable String q,
                                                                         String lang,
                                                                         PersonDTO person) {
        if (findField(fieldId) instanceof CustomFieldConceptFromFieldCode) {
            List<ConceptAutocompleteDTO> unfiltered = relatedConcepts(organizationId, null, fieldId, typeConceptId, projectId, null, lang, person);
            if (!unfiltered.isEmpty()) {
                return q == null || q.isBlank()
                        ? unfiltered
                        : relatedConcepts(organizationId, null, fieldId, typeConceptId, projectId, q, lang, person);
            }
        }
        return conceptsForField(organizationId, fieldId, projectId, typeConceptId, q, lang, person);
    }

    /**
     * The suggestions of a field whose list depends on another field's answer (rule
     * {@code options: RELATED_CONCEPTS}): the concepts related to {@code relatedToConceptId} among
     * those configured for the field's code ({@link FieldConfigurationService#fetchAutocompleteRelated}).
     * Not read-only: related concepts still stubbed from an import are fetched from the thesaurus
     * on first read.
     *
     * @param fieldCode the field's code, or null when {@code fieldId} names a field-code field
     * @param fieldId   alternative to {@code fieldCode}
     * @param projectId the project the edited entity belongs to, if any (project override)
     */
    @Transactional
    public List<ConceptAutocompleteDTO> getRelatedConcepts(long organizationId,
                                                           @Nullable String fieldCode,
                                                           @Nullable Long fieldId,
                                                           long relatedToConceptId,
                                                           @Nullable Long projectId,
                                                           @Nullable String q,
                                                           String lang,
                                                           PersonDTO person) {
        return relatedConcepts(organizationId, fieldCode, fieldId, relatedToConceptId, projectId, q, lang, person);
    }

    private List<ConceptAutocompleteDTO> relatedConcepts(long organizationId,
                                                         @Nullable String fieldCode,
                                                         @Nullable Long fieldId,
                                                         long relatedToConceptId,
                                                         @Nullable Long projectId,
                                                         @Nullable String q,
                                                         String lang,
                                                         PersonDTO person) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        String code = fieldCode;
        if ((code == null || code.isBlank()) && fieldId != null) {
            CustomField field = findField(fieldId);
            if (field instanceof CustomFieldConceptFromFieldCode fromFieldCode) {
                code = fromFieldCode.getFieldCode();
            }
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "relatedToConceptId demande un champ rattaché à un fieldCode");
        }
        Concept base = conceptService.findById(relatedToConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + relatedToConceptId));
        UserInfo userInfo = new UserInfo(institution, person, lang);
        try {
            return fieldConfigurationService.fetchAutocompleteRelated(userInfo, code, base, q, projectId);
        } catch (NoConfigForFieldException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucun vocabulaire configuré pour le fieldCode : " + code);
        }
    }

    /**
     * A field by its id: the persisted one, or — for the system fields no table configures, whose forms are static
     * (the places', the projects') — the one the form declares, so a client reads every vocabulary field the same way.
     */
    private CustomField findField(long fieldId) {
        return customFieldRepository.findById(fieldId)
                .map(f -> (CustomField) Hibernate.unproxy(f))
                .or(() -> staticSystemField(fieldId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Champ introuvable : " + fieldId));
    }

    private static Optional<CustomField> staticSystemField(long fieldId) {
        return Stream.of(SpatialUnit.DETAILS_FORM, ActionUnit.DETAILS_FORM)
                .flatMap(form -> form.getLayout().stream())
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .map(CustomColUiDto::getField)
                .filter(field -> field != null && Objects.equals(field.getId(), fieldId))
                .findFirst();
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
