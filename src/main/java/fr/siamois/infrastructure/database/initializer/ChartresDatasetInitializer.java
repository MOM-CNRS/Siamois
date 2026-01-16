package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ChartresDatasetInitializer implements DatabaseInitializer {


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
    private final RecordingUnitRelSeeder recordingUnitRelSeeder;


    @Value("${siamois.admin.username}")
    private String adminUsername;


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
        recordingUnitSeeder.seed(specs.getRecordingUnits());
        recordingUnitRelSeeder.seed(specs.getRecordingUnitRelSpecs());


    }



}
