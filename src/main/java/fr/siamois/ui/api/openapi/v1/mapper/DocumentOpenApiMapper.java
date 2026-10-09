package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Document OpenAPI ({@code documents}) from the business DTO, for the web client — the flat counterpart of
 * {@link PhaseOpenApiMapper}. The mobile API keeps its own mapper ({@link ProjectDocumentOpenApiMapper},
 * which reads the entity). The resource's {@code answers} and {@code _permissions} are built at the
 * service level, not here.
 */
@Component
@RequiredArgsConstructor
public class DocumentOpenApiMapper {

    private final ProjectResponseMapper projectResponseMapper;

    public DocumentResource toResource(DocumentDTO document, String lang, Map<Long, String> resolvedLabels) {
        DocumentResource r = new DocumentResource();
        r.setResourceType("documents");
        r.setId(document.getId() != null ? String.valueOf(document.getId()) : null);
        r.setValidationStatus(document.getValidationStatus());
        r.setIdentifier(document.getIdentifier());
        r.setTitle(document.getTitle());
        r.setDescription(document.getDescription());
        r.setLabel(document.getTitle() != null && !document.getTitle().isBlank() ? document.getTitle() : document.getIdentifier());
        r.setFileName(document.getFileName());
        r.setMimeType(document.getMimeType());
        r.setUrl(document.getUrl());
        r.setFileCode(document.getFileCode());
        r.setSize(document.getSize());
        r.setMd5Sum(document.getMd5Sum());
        if (document.getActionUnit() != null && document.getActionUnit().getId() != null) {
            r.setProjectId(String.valueOf(document.getActionUnit().getId()));
            if (document.getActionUnit().getCreatedByInstitution() != null
                    && document.getActionUnit().getCreatedByInstitution().getId() != null) {
                OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
                org.setResourceType("organizations");
                org.setId(String.valueOf(document.getActionUnit().getCreatedByInstitution().getId()));
                r.setOrganization(org);
            }
        }
        if (document.getType() != null) {
            r.setType(projectResponseMapper.toConceptFieldValue(document.getType(), lang, resolvedLabels));
        }
        if (document.getId() != null) {
            r.setResourceUri("/document/" + document.getId());
        }
        return r;
    }
}
