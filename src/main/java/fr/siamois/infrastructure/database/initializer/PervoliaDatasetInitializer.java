package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
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
    private final InstitutionRepository institutionRepository;


    @Value("${siamois.admin.username}")
    private String adminUsername;

    public PervoliaDatasetInitializer(
            PersonSeeder personSeeder, ActionCodeSeeder actionCodeSeeder,
            ConceptSeeder conceptSeeder, ThesaurusSeeder thesaurusSeeder, SpatialUnitSeeder spatialUnitSeeder, ActionUnitSeeder actionUnitSeeder,
            RecordingUnitSeeder recordingUnitSeeder, SpecimenSeeder specimenSeeder, InstitutionSeeder institutionSeeder, OOXMLImportService ooxmlImportService, InstitutionRepository institutionRepository) {



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
        this.institutionRepository = institutionRepository;
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
            personSeeder.seed(specs.persons());
            institutionSeeder.seed(specs.institutions());
            Institution ch = institutionRepository.findInstitutionByIdentifier("pervolia").orElseThrow(() -> new RuntimeException("PERVOLIA NOT FOUND"));
            spatialUnitSeeder.seed(specs.spatialUnits());
            actionCodeSeeder.seed(specs.actionCodes());
            actionUnitSeeder.seed(specs.actionUnits());
            recordingUnitSeeder.seed(specs.recordingUnits());
            specimenSeeder.seed(specs.specimenSpecs(), ch.getId());
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }



    }



}
