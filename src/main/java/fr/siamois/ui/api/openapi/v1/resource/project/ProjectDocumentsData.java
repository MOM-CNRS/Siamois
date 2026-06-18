package fr.siamois.ui.api.openapi.v1.resource.project;

import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocumentsData {

    private List<DocumentResource> documents;
}
