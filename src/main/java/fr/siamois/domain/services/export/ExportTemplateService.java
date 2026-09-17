package fr.siamois.domain.services.export;

import java.util.List;

import fr.siamois.domain.models.export.ExportTemplate;

public interface ExportTemplateService {
    
    List<ExportTemplate> getAllTemplates();
    
    ExportTemplate getTemplateById(Long id);
    
    ExportTemplate saveTemplate(ExportTemplate template);
    
    void deleteTemplate(Long id);
    
    List<ExportTemplate> getTemplatesByProject(Long projectId);
}