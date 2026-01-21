package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
@Getter
@Setter
public class PervoliaDatasetInitializer  {



    @Value("${siamois.admin.email}")
    private String adminEmail;



    private final ConceptSeeder conceptSeeder;
    private final PersonSeeder personSeeder;
    private final ThesaurusSeeder thesaurusSeeder;
    private final ActionCodeSeeder actionCodeSeeder;
    private final SpatialUnitSeeder spatialUnitSeeder;
    private final ActionUnitSeeder actionUnitSeeder;
    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenSeeder specimenSeeder;
    private final InstitutionSeeder institutionSeeder;
    private final OOXMLImportService ooxmlImportService;


    @Value("${siamois.admin.username}")
    private String adminUsername;

    public PervoliaDatasetInitializer(
            PersonSeeder personSeeder, ActionCodeSeeder actionCodeSeeder,
            ConceptSeeder conceptSeeder, ThesaurusSeeder thesaurusSeeder, SpatialUnitSeeder spatialUnitSeeder, ActionUnitSeeder actionUnitSeeder,
            RecordingUnitSeeder recordingUnitSeeder, SpecimenSeeder specimenSeeder, InstitutionSeeder institutionSeeder, OOXMLImportService ooxmlImportService) {



        this.personSeeder = personSeeder;
        this.actionCodeSeeder = actionCodeSeeder;
        this.conceptSeeder = conceptSeeder;
        this.thesaurusSeeder = thesaurusSeeder;
        this.spatialUnitSeeder = spatialUnitSeeder;
        this.actionUnitSeeder = actionUnitSeeder;
        this.recordingUnitSeeder = recordingUnitSeeder;
        this.specimenSeeder = specimenSeeder;
        this.institutionSeeder = institutionSeeder;
        this.ooxmlImportService = ooxmlImportService;
    }

    /**
     * Insert pervolia test dataset into DB
     */
    @Transactional
    public void initialize() throws DatabaseDataInitException {


        // Init vocabs

        ImportSpecs specs;

        try {
            InputStream is = getClass().getResourceAsStream("/datasets/Import_Pervolia.xlsx");
            if (is == null) {
                throw new IllegalStateException("Impossible de trouver Import Pervolia.xlsx");
            }
            specs = ooxmlImportService.importFromExcel(is);
        } catch (IOException e) {
            throw new DatabaseDataInitException(e.getMessage(), e);
        }


        try {
            personSeeder.seed(specs.getPersons());
            institutionSeeder.seed(specs.getInstitutions());
            spatialUnitSeeder.seed(specs.getSpatialUnits());
            actionCodeSeeder.seed(specs.getActionCodes());
            actionUnitSeeder.seed(specs.getActionUnits());
            recordingUnitSeeder.seed(specs.getRecordingUnits());
            specimenSeeder.seed(specs.getSpecimenSpecs());
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }



    }



}
