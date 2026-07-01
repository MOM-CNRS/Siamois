package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.ImportResult;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import fr.siamois.infrastructure.dataimport.SheetMetadata;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Data
public class ProjectUploadSettingsBean {

    public static final String TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL = "templateFormCC:templateForm:templateGrowl";

    @Value
    public static class SheetMappingView {
        String sheetName;
        String tableId;
        List<ColumnAliasView> columnAliases;
        public boolean hasAliases() { return !columnAliases.isEmpty(); }
    }

    @Value
    public static class ColumnAliasView {
        String alias;
        String canonical;
    }

    private final OOXMLImportService importService;
    private final ProjectDataSeeder seeder;

    ActionUnitDTO project;
    UploadedFile originalFile;
    StreamedContent templateFile;
    ImportResult importResult;
    boolean readyToUpload = false;

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;

        templateFile = DefaultStreamedContent.builder()
                .name("Import_Chartres_Projet.xlsx")
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .stream(() -> {
                    try {
                        return new ClassPathResource(
                                "datasets/Import_Chartres_Projet.xlsx")
                                .getInputStream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        readyToUpload = false;
        importResult = null;
        originalFile = null;
    }

    public List<SheetMappingView> getSheetMappings() {
        if (importResult == null) return List.of();
        SheetMetadata meta = importResult.meta();
        List<SheetMappingView> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : meta.tableToSheets().entrySet()) {
            String tableId = entry.getKey();
            for (String sheetName : entry.getValue()) {
                Map<String, String> aliases = meta.columnAliases().getOrDefault(sheetName, Map.of());
                List<ColumnAliasView> aliasList = aliases.entrySet().stream()
                        .map(e -> new ColumnAliasView(e.getKey(), e.getValue()))
                        .collect(java.util.stream.Collectors.toList());
                result.add(new SheetMappingView(sheetName, tableId, aliasList));
            }
        }
        return result;
    }

    public String getMappingSummary() {
        if (importResult == null) return "";
        int sheets = getSheetMappings().size();
        long tables = importResult.meta().tableToSheets().size();
        return sheets + " feuille" + (sheets > 1 ? "s" : "") + " → " + tables + " table" + (tables > 1 ? "s" : "");
    }

    public List<ImportError> getImportErrors() {
        if (importResult == null) return List.of();
        return importResult.errors();
    }

    public int getErrorCount() {
        return importResult == null ? 0 : importResult.errors().size();
    }

    public void uploadSpec() {
        if (importResult == null) {
            FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Rien à importer", null));
            PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
            return;
        }
        try {
            seeder.seedAll(importResult.specs(), project);
            FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Données importées avec succès", null));
            PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
            reset();
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Une erreur est survenue", e.getMessage()));
            PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        this.originalFile = null;
        UploadedFile file = event.getFile();

        if (file != null && file.getFileName() != null) {
            try (InputStream is = file.getInputStream()) {
                this.importResult = importService.importFromExcel(is,
                        OOXMLImportService.ImportScope.PROJECT,
                        project);
                readyToUpload = true;

                if (importResult.hasErrors()) {
                    String msg = importResult.errors().size() + " ligne(s) ignorée(s) lors du chargement";
                    FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                            new FacesMessage(FacesMessage.SEVERITY_WARN, "Import partiel", msg));
                    PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
                }
            } catch (Exception e) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Échec du chargement du fichier : " + e.getMessage()));
            }
        }
    }
}
