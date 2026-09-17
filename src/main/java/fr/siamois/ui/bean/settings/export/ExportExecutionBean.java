package fr.siamois.ui.bean.settings.export;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.bean.ManagedProperty;

import fr.siamois.domain.models.export.ExportTemplate;
import fr.siamois.domain.services.export.ExportEngineService;
import fr.siamois.domain.services.export.ExportTemplateService;

@ManagedBean(name = "exportExecutionBean")
@ViewScoped
public class ExportExecutionBean implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @ManagedProperty("#{exportTemplateService}")
    private ExportTemplateService exportTemplateService;
    
    @ManagedProperty("#{exportEngineService}")
    private ExportEngineService exportEngineService;
    
    private ExportTemplate selectedTemplate;
    private List<ExportTemplate> templates;
    private List<ExportResult> exportResults;
    
    public ExportExecutionBean() {
        this.exportResults = new ArrayList<>();
    }
    
    public void init() {
        this.templates = exportTemplateService.getAllTemplates();
    }
    
    public void executeExport() {
        try {
            // In a real implementation, this would execute the export and return a downloadable file
            // For now, we'll simulate the process
            
            // Simulate processing each sheet
            for (ExportTemplate.Sheet sheet : selectedTemplate.getSheets()) {
                ExportResult result = new ExportResult();
                result.setSheetName(sheet.getName());
                result.setRowCount(0); // Would be actual count in real implementation
                result.setStatus("Terminé");
                exportResults.add(result);
            }
            
        } catch (Exception e) {
            // Handle exception
            e.printStackTrace();
        }
    }
    
    // Inner class to hold export results
    public static class ExportResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String sheetName;
        private int rowCount;
        private String status;
        
        // Getters and setters
        public String getSheetName() {
            return sheetName;
        }
        
        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }
        
        public int getRowCount() {
            return rowCount;
        }
        
        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
    
    // Getters and setters
    public ExportTemplate getSelectedTemplate() {
        return selectedTemplate;
    }
    
    public void setSelectedTemplate(ExportTemplate selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }
    
    public List<ExportTemplate> getTemplates() {
        return templates;
    }
    
    public void setTemplates(List<ExportTemplate> templates) {
        this.templates = templates;
    }
    
    public List<ExportResult> getExportResults() {
        return exportResults;
    }
    
    public void setExportResults(List<ExportResult> exportResults) {
        this.exportResults = exportResults;
    }
    
    public ExportTemplateService getExportTemplateService() {
        return exportTemplateService;
    }
    
    public void setExportTemplateService(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }
    
    public ExportEngineService getExportEngineService() {
        return exportEngineService;
    }
    
    public void setExportEngineService(ExportEngineService exportEngineService) {
        this.exportEngineService = exportEngineService;
    }
}