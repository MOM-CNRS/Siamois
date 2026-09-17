package fr.siamois.ui.bean.settings.export;

import java.io.Serializable;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;

import fr.siamois.domain.models.export.ExportTemplate;

@ManagedBean(name = "columnMappingBean")
@ViewScoped
public class ColumnMappingBean implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private ExportTemplate.ColumnMapping mapping;
    
    public ColumnMappingBean() {
        this.mapping = new ExportTemplate.ColumnMapping();
    }
    
    public void initMapping(ExportTemplate.ColumnMapping mapping) {
        this.mapping = mapping != null ? mapping : new ExportTemplate.ColumnMapping();
    }
    
    public void saveMapping() {
        // Save the mapping - in a real implementation this would persist to the backend
    }
    
    public void cancel() {
        this.mapping = new ExportTemplate.ColumnMapping();
    }
    
    // Getters and setters
    public ExportTemplate.ColumnMapping getMapping() {
        return mapping;
    }
    
    public void setMapping(ExportTemplate.ColumnMapping mapping) {
        this.mapping = mapping;
    }
}