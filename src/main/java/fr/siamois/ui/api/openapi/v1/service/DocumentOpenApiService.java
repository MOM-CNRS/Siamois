package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.models.document.Document;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Détail/écriture d'un document ({@code GET}/{@code PATCH}/{@code POST /api/v1/documents}) pour le client
 * web — le pendant, pour Document, de {@link PhaseOpenApiService}. Les champs système se lisent
 * ({@link DocumentAnswersProjector}) et s'écrivent ({@link FieldAnswerPatchService}) par réflexion sur
 * {@link DocumentDTO} ; les champs additionnels de la catégorie sont persistés à part. Les écritures du
 * mobile (métadonnées à plat, fichier) restent dans {@link DocumentWriteOpenApiService}.
 */
@Service
@RequiredArgsConstructor
public class DocumentOpenApiService {

    private final DocumentService documentService;
    private final ActionUnitService actionUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;
    private final DocumentOpenApiMapper documentOpenApiMapper;
    private final DocumentListProjectionService documentListProjectionService;
    private final MultiValueAnswers multiValueAnswers;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EffectiveFormResolver effectiveFormResolver;

    @Transactional(readOnly = true)
    public DocumentResource getDocumentById(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        return loadDocument(id, personDto, accessibleInstitutionIds, lang);
    }

    private DocumentResource loadDocument(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        DocumentDTO document = requireAccessibleDocument(id, personDto, accessibleInstitutionIds);
        return toResourceWithPermissionsAndAnswers(document, personDto, lang);
    }

    @Transactional
    public DocumentResource createDocument(DocumentCreateRequest request, PersonDTO personDto,
                                     Set<Long> accessibleInstitutionIds, String lang) {
        String projectId = requireNonBlank(request.getProjectId(), "projectId");
        String categoryId = requireNonBlank(request.getCategoryId(), "categoryId");

        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectId, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasProjectPermission(userInfo, au.getId(),
                PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                PermissionConstants.PROJECT_EDIT_DOCUMENTS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur ce projet");
        }

        Concept categoryConcept = conceptService.findById(parseLong(categoryId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie de document introuvable"));

        DocumentDTO shell = new DocumentDTO();
        shell.setActionUnit(new ActionUnitSummaryDTO(au));
        // Required by TraceableEntity (NOT NULL); the service does not fill them in — same as the
        // find creation (FindOpenApiService): the caller, in the project's organization.
        shell.setCreatedBy(personDto);
        shell.setCreatedByInstitution(institution);
        shell.setCategory(conceptMapper.convert(categoryConcept));
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            shell.setTitle(request.getTitle().trim());
        }

        DocumentDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> documentService.save(shell));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
    }

    @Transactional
    public DocumentResource patchDocument(long id, DocumentPatchRequest request, PersonDTO personDto,
                                    Set<Long> accessibleInstitutionIds, String lang) {
        DocumentDTO document = requireAccessibleDocument(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = document.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, document.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                PermissionConstants.PROJECT_EDIT_DOCUMENTS);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo, document.getActionUnit().getId());
        boolean answersChange = request.getAnswers() != null && !request.getAnswers().isEmpty();
        boolean statusChange = ValidationOpenApiService.changes(document.getValidated(), request.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((answersChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(document.getValidated(), request.getValidated(), canEdit, canValidate);

        if (answersChange || !statusChange) {
            Long projectId = document.getActionUnit().getId();
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the document-types catalog lays out (system + additional fields).
                FormUiDto form = effectiveFormResolver.resolveEffectiveForm(projectId,
                        ConfigurableTable.DOCUMENT, document.getCategory() != null ? document.getCategory().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(document, form, request.getAnswers(), projectId);
                requireWebUrl(document.getExternalUrl());
                return documentService.save(document, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(Document.class, id, request.getValidated(), personDto);
        return loadDocument(id, personDto, accessibleInstitutionIds, lang);
    }

    private DocumentResource toResourceWithPermissionsAndAnswers(DocumentDTO document, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(document.getActionUnit().getCreatedByInstitution(), personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, document.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                PermissionConstants.PROJECT_EDIT_DOCUMENTS);

        DocumentListProjectionService.DocumentListProjection projection = documentListProjectionService.buildOne(document, lang);
        DocumentResource resource = documentOpenApiMapper.toResource(document, lang, projection.resolvedLabels());
        Map<String, Object> answers = new java.util.LinkedHashMap<>(java.util.Objects.requireNonNullElse(projection.answersFor(document.getId()), Map.of()));
        // The projection reads system fields only; the type's additional fields live in their own answer rows.
        answers.putAll(OpenApiExecutionContext.callWithUserInfo(userInfo, () -> fieldAnswerWireService.additionalAnswers(
                customFieldAnswerService.loadAdditionalFieldAnswers(document), lang)));
        // The additional answers join after the projection's own shaping: cut them the same way.
        resource.setAnswers(multiValueAnswers.shapeOne(Document.class, document.getId(), answers, null,
                ValuesLimit.forDetail(), lang));
        resource.setPermissions(ProjectResourcePermissions.of(canEdit)
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, document.getActionUnit().getId())));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
    }

    /**
     * Document par id numérique, avec vérification d'appartenance institutionnelle et de droit de
     * consultation (même règle que le projet parent — {@code canViewProject}, Document n'a pas de
     * droit de lecture propre). 404 aussi bien si la document n'existe pas que si elle existe mais est
     * hors périmètre — jamais de distinction observable de l'extérieur.
     */
    private DocumentDTO requireAccessibleDocument(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        DocumentDTO document = documentService.findDtoById(id);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        if (document.getActionUnit() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document sans projet de rattachement : rattachez-le à un projet pour l'ouvrir");
        }
        InstitutionDTO institution = document.getActionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null
                || accessibleInstitutionIds == null || !accessibleInstitutionIds.contains(institution.getId())
                || !profilePermissionService.canViewProject(personDto, institution, document.getActionUnit().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable ou hors périmètre");
        }
        return document;
    }

    private static final java.util.regex.Pattern WEB_URL = java.util.regex.Pattern.compile("^https?://\\S+$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** The external URL is opened in the browser: nothing but a web address (no {@code javascript:}…). */
    private static void requireWebUrl(String url) {
        if (url != null && !url.isBlank() && !WEB_URL.matcher(url.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'URL externe doit commencer par http:// ou https://");
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " est requis");
        }
        return value;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu : " + value);
        }
    }

    /**
     * The access check every document-scoped endpoint starts with (404 when out of scope) — public for
     * the endpoints that page something else under a document (its recording units).
     */
    public DocumentDTO requireAccessible(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        return requireAccessibleDocument(id, personDto, accessibleInstitutionIds);
    }

    /** Previous/next document in the same project ({@code GET /api/v1/documents/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        DocumentDTO document = requireAccessibleDocument(id, personDto, accessibleInstitutionIds);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.DOCUMENT,
                document.getActionUnit().getId(), document.getId());
    }
}
