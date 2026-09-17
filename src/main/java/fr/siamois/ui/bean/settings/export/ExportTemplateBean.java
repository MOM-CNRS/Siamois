package fr.siamois.ui.bean.settings.export;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.bean.ManagedProperty;

import fr.siamois.domain.models.export.ExportTemplate;
import fr.siamois.domain.services.export.ExportTemplateService;

@ManagedBean(name = "exportTemplateBean")
@ViewScoped
public class ExportTemplateBean implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @ManagedProperty("#{exportTemplateService}")
    private ExportTemplateService exportTemplateService;
    
    private ExportTemplate template;
    private List<ExportTemplate> templates;
    
    public ExportTemplateBean() {
        this.templates = new ArrayList<>();
        // Initialization handled by Spring
    }
    
    public void init() {
        this.templates = exportTemplateService.getAllTemplates();
    }
    
    public void createTemplate() {
        this.template = new ExportTemplate();
        this.template.setSheets(new ArrayList<>());
    }
    
    public void editTemplate(ExportTemplate template) {
        this.template = template;
    }
    
    public void duplicateTemplate(ExportTemplate template) {
        // Create a copy of the template
        this.template = new ExportTemplate();
        this.template.setName(template.getName() + " (copie)");
        this.template.setDescription(template.getDescription());
        this.template.setSheets(new ArrayList<>(template.getSheets()));
    }
    
    public void deleteTemplate(ExportTemplate template) {
        exportTemplateService.deleteTemplate(template.getId());
        templates.remove(template);
    }
    
    public void saveTemplate() {
        if (template.getId() == null) {
            // New template
            template.setId(System.currentTimeMillis());
        }
        exportTemplateService.saveTemplate(template);
        this.template = null;
        // Refresh the list
        this.templates = exportTemplateService.getAllTemplates();
    }
    
    public void cancel() {
        this.template = null;
    }
    
    public void addSheet() {
        // Add a new sheet to the current template
        if (template != null) {
            // This would normally create a new sheet object
        }
    }
    
    public void editSheet(ExportTemplate.Sheet sheet) {
        // Edit existing sheet
    }
    
    // Getters and setters
    public ExportTemplate getTemplate() {
        return template;
    }
    
    public void setTemplate(ExportTemplate template) {
        this.template = template;
    }
    
    public List<ExportTemplate> getTemplates() {
        return templates;
    }
    
    public void setTemplates(List<ExportTemplate> templates) {
        this.templates = templates;
    }
    
    public ExportTemplateService getExportTemplateService() {
        return exportTemplateService;
    }
    
    public void setExportTemplateService(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }
}