package fr.siamois.ui.controller;

import java.io.Serializable;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;

@ManagedBean(name = "exportController")
@RequestScoped
public class ExportController implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public void downloadExportFile() {
        // This would handle the download functionality in a real implementation
        // For now, it's a placeholder
    }
}