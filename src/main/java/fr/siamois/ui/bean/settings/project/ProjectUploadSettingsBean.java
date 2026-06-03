package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ImportSpecs;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Data
public class ProjectUploadSettingsBean {

    private final OOXMLImportService importService;
    private final ProjectDataSeeder seeder;

    ActionUnitDTO project;
    UploadedFile originalFile;
    StreamedContent templateFile;
    ImportSpecs specs;
    boolean readyToUpload = false;

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;

        templateFile = DefaultStreamedContent.builder()
                .name("import_projet_sample.xlsx")
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .stream(() -> {
                    try {
                        return new ClassPathResource(
                                "datasets/import_projet_sample.xlsx")
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
        readyToUpload=false;
        specs = null;
        originalFile = null;
    }

    public void uploadSpec() {
        if(specs == null) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Rien à importer", null));
        }
        try {
            seeder.seedAll(specs,project);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Données importées avec succès", null));
            reset();
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Une erreur est survenue", e.getMessage()));
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        this.originalFile = null;
        UploadedFile file = event.getFile();

        // Vérification de base sur le fichier
        if (file != null && file.getFileName() != null) {

            // Utilisation du try-with-resources pour ouvrir et fermer le flux proprement
            try (InputStream is = file.getInputStream()) {

                // Appel de votre service d'import avec le flux d'entrée
                this.specs = importService.importFromExcel(is,
                        OOXMLImportService.ImportScope.PROJECT,
                        project);
                readyToUpload = true;

            } catch (Exception e) {
                // Gestion de l'erreur d'import ou de lecture de fichier
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Échec du chargement du fichier : " + e.getMessage()));
            }
        }
    }


}
