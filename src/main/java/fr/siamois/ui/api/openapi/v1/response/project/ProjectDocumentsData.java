package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocumentsData {

    private List<ProjectDocumentResource> documents;
}
