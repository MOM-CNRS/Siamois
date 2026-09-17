package fr.siamois.ui.bean.settings.export;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.bean.ManagedProperty;

import fr.siamois.domain.models.export.ExportTemplate;
import fr.siamois.domain.services.export.ExportTemplateService;

@ManagedBean(name = "exportSheetBean")
@ViewScoped
public class ExportSheetBean implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @ManagedProperty("#{exportTemplateService}")
    private ExportTemplateService exportTemplateService;
    
    private ExportTemplate.Sheet sheet;
    private List<ExportTemplate.Sheet> sheets;
    
    public ExportSheetBean() {
        this.sheets = new ArrayList<>();
        // Initialization handled by Spring
    }
    
    public void init() {
        this.sheets = new ArrayList<>();
        // Initialize with sample data for demonstration
        initializeSampleSheets();
    }
    
    private void initializeSampleSheets() {
        // Sample sheets initialization
        ExportTemplate.Sheet sheet1 = new ExportTemplate.Sheet();
        sheet1.setId(1L);
        sheet1.setName("Opération archéologique");
        sheet1.setSources(new ArrayList<>());
        
        ExportTemplate.Sheet sheet2 = new ExportTemplate.Sheet();
        sheet2.setId(2L);
        sheet2.setName("Unité d'enregistrement");
        sheet2.setSources(new ArrayList<>());
        
        sheets.add(sheet1);
        sheets.add(sheet2);
    }
    
    public void addSource() {
        // Add a new source to the current sheet
        if (sheet != null) {
            // This would normally create a new source object
        }
    }
    
    public void editSource(ExportTemplate.Source source) {
        // Edit existing source
    }
    
    public void saveSheet() {
        if (sheet.getId() == null) {
            // New sheet
            sheet.setId(System.currentTimeMillis());
            sheets.add(sheet);
        }
        this.sheet = null;
    }
    
    public void cancel() {
        this.sheet = null;
    }
    
    // Getters and setters
    public ExportTemplate.Sheet getSheet() {
        return sheet;
    }
    
    public void setSheet(ExportTemplate.Sheet sheet) {
        this.sheet = sheet;
    }
    
    public List<ExportTemplate.Sheet> getSheets() {
        return sheets;
    }
    
    public void setSheets(List<ExportTemplate.Sheet> sheets) {
        this.sheets = sheets;
    }
    
    public ExportTemplateService getExportTemplateService() {
        return exportTemplateService;
    }
    
    public void setExportTemplateService(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }
}