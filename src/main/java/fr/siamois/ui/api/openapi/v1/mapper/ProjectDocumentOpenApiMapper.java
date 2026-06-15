package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.document.Document;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// TODO [ARCH] utiliser mapstruct
public class ProjectDocumentOpenApiMapper {

    private final ConceptMapper conceptMapper;
    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;

    public ProjectDocumentResource toResource(Document doc) {
        ProjectDocumentResource r = new ProjectDocumentResource();
        r.setResourceType("documents");
        r.setId(doc.getId() == null ? null : String.valueOf(doc.getId()));
        r.setTitle(doc.getTitle());
        r.setDescription(doc.getDescription());
        r.setFileName(doc.getFileName());
        r.setMimeType(doc.getMimeType());
        r.setUrl(doc.getUrl());
        r.setFileCode(doc.getFileCode());
        r.setSize(doc.getSize());
        r.setMd5Sum(doc.getMd5Sum());
        if (doc.getNature() != null) {
            r.setNature(
                    conceptResourceIdentifierMapper.convert(conceptMapper.convert(doc.getNature())));
        }
        if (doc.getScale() != null) {
            r.setScale(
                    conceptResourceIdentifierMapper.convert(conceptMapper.convert(doc.getScale())));
        }
        if (doc.getFormat() != null) {
            r.setFormat(
                    conceptResourceIdentifierMapper.convert(conceptMapper.convert(doc.getFormat())));
        }
        return r;
    }
}
