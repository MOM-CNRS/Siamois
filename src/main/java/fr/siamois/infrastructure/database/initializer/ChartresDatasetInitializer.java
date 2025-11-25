package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@Getter
@Setter
@Order
public class ChartresDatasetInitializer implements DatabaseInitializer {

    public static final String CHARTRES = "chartres";
    public static final String PASCAL_GIBUT_SIAMOIS_FR = "pascal.gibut@siamois.fr";
    public static final String CHARTRES_C_309_01_1015 = "chartres-C309_01-1015";

    public static final String VOCABULARY_ID = "th240";


    List<ThesaurusSeeder.ThesaurusSpec> thesauri = List.of(
            new ThesaurusSeeder.ThesaurusSpec("https://thesaurus.mom.fr", VOCABULARY_ID)
    );


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

    public ChartresDatasetInitializer(
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
     * Insert chartres test dataset into DB
     */
    @Override
    @Transactional
    public void initialize() throws DatabaseDataInitException {


        // Init vocabs
        thesaurusSeeder.seed(thesauri);

        ImportSpecs specs;

        try {
            InputStream is = getClass().getResourceAsStream("/datasets/Import_Chartres.xlsx");
            if (is == null) {
                throw new IllegalStateException("Impossible de trouver Import Chartres.xlsx");
            }
            specs = ooxmlImportService.importFromExcel(is);
        } catch (IOException e) {
            throw new DatabaseDataInitException(e.getMessage(), e);
        }

        personSeeder.seed(specs.getPersons());
        institutionSeeder.seed(specs.getInstitutions());
        spatialUnitSeeder.seed(specs.getSpatialUnits());
        actionCodeSeeder.seed(specs.getActionCodes());
        actionUnitSeeder.seed(specs.getActionUnits());
        recordingUnitSeeder.seed(specs.getRecordingUnits());
        specimenSeeder.seed(specs.getSpecimenSpecs());

    }



}
