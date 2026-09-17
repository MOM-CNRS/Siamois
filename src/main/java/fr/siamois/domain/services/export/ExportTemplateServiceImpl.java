package fr.siamois.domain.services.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.siamois.domain.models.export.ExportTemplate;

@Service
public class ExportTemplateServiceImpl implements ExportTemplateService {
    
    private List<ExportTemplate> templates;
    
    public ExportTemplateServiceImpl() {
        this.templates = new ArrayList<>();
        // Initialize with sample data for demonstration
        initializeSampleTemplates();
    }
    
    private void initializeSampleTemplates() {
        // Sample templates initialization
        ExportTemplate template1 = new ExportTemplate();
        template1.setId(1L);
        template1.setName("SDA - Référentiel national");
        template1.setDescription("Export conforme au référentiel national");
        template1.setSheets(new ArrayList<>());
        
        ExportTemplate template2 = new ExportTemplate();
        template2.setId(2L);
        template2.setName("Export CSV simple");
        template2.setDescription("Export plat sans relations");
        template2.setSheets(new ArrayList<>());
        
        templates.add(template1);
        templates.add(template2);
    }
    
    @Override
    public List<ExportTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }
    
    @Override
    public ExportTemplate getTemplateById(Long id) {
        Optional<ExportTemplate> template = templates.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();
        return template.orElse(null);
    }
    
    @Override
    public ExportTemplate saveTemplate(ExportTemplate template) {
        if (template.getId() == null) {
            // New template
            template.setId(System.currentTimeMillis());
            templates.add(template);
        } else {
            // Update existing template
            int index = templates.indexOf(template);
            if (index >= 0) {
                templates.set(index, template);
            }
        }
        return template;
    }
    
    @Override
    public void deleteTemplate(Long id) {
        templates.removeIf(t -> t.getId().equals(id));
    }
    
    @Override
    public List<ExportTemplate> getTemplatesByProject(Long projectId) {
        // In a real implementation, this would filter by project
        return getAllTemplates();
    }
}