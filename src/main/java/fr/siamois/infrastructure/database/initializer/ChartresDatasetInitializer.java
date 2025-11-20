package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder.ConceptSpec;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Getter
@Setter
@Order
public class ChartresDatasetInitializer implements DatabaseInitializer {

    public static final String CHARTRES = "chartres";
    public static final String PARCELLE = "4287532";
    public static final String PASCAL_GIBUT_SIAMOIS_FR = "pascal.gibut@siamois.fr";
    public static final String CHARTRES_C_309_01_1015 = "chartres-C309_01-1015";
    public static final String CHARTRES_C_309_01 = "chartres-C309_01";
    public static final String US_EXTERNAL_ID = "4282375";
    public static final String OA_EXT_ID = "4283545";
    public static final String PARCELLE_DA_154 = "Parcelle DA 154";
    public static final String PARCELLE_DA_155 = "Parcelle DA 155";
    public static final String EMPRISE_DE_FOUILLE_DE_C_309_01 = "Emprise de fouille de C309_01";

    public static final String VOCABULARY_ID = "th240";


    List<ConceptSpec> concepts = List.of(
            new ConceptSpec(VOCABULARY_ID, "4287534", "Fouille préventive", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287533", "Diagnostic archéologique", "fr"),
            new ConceptSpec(VOCABULARY_ID, PARCELLE, "Parcelle", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4282370", "Commune", "fr"),
            new ConceptSpec(VOCABULARY_ID, US_EXTERNAL_ID, "Unité stratigraphique", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287539", "Dépôt", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287541", "Couche d'occupation", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287537", "Interface", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287540", "Creusement", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4286252", "Individuel", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4286251", "Lot", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287542", "ANImal", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287543", "METal", "fr"),
            new ConceptSpec(VOCABULARY_ID, OA_EXT_ID, "Code OA", "fr"),
            new ConceptSpec(VOCABULARY_ID, "4287544", "Emprise de fouille", "fr")
    );

    List<ThesaurusSeeder.ThesaurusSpec> thesauri = List.of(
            new ThesaurusSeeder.ThesaurusSpec("https://thesaurus.mom.fr", VOCABULARY_ID)
    );

    
    List<SpatialUnitSeeder.SpatialUnitSpecs> spUnits = List.of(
            new SpatialUnitSeeder.SpatialUnitSpecs(PARCELLE_DA_154, VOCABULARY_ID, PARCELLE,
                    PASCAL_GIBUT_SIAMOIS_FR, CHARTRES, null),
            new SpatialUnitSeeder.SpatialUnitSpecs(PARCELLE_DA_155, VOCABULARY_ID, PARCELLE,
                    PASCAL_GIBUT_SIAMOIS_FR, CHARTRES, null),
            new SpatialUnitSeeder.SpatialUnitSpecs(EMPRISE_DE_FOUILLE_DE_C_309_01, VOCABULARY_ID, "4287544",
                    PASCAL_GIBUT_SIAMOIS_FR, CHARTRES, Set.of(
                    new SpatialUnitSeeder.SpatialUnitKey(PARCELLE_DA_154),
                    new SpatialUnitSeeder.SpatialUnitKey(PARCELLE_DA_155)
            )),
            new SpatialUnitSeeder.SpatialUnitSpecs("Chartres", VOCABULARY_ID, "4282370",
                    PASCAL_GIBUT_SIAMOIS_FR, CHARTRES, Set.of(
                    new SpatialUnitSeeder.SpatialUnitKey(PARCELLE_DA_154),
                    new SpatialUnitSeeder.SpatialUnitKey(PARCELLE_DA_155),
                    new SpatialUnitSeeder.SpatialUnitKey(EMPRISE_DE_FOUILLE_DE_C_309_01)
            ))
    );



    List<ActionUnitSeeder.ActionUnitSpecs> actions = List.of(
            new ActionUnitSeeder.ActionUnitSpecs(CHARTRES_C_309_01, "Pôle Gare - Phase 1", "C309_01",
                    "069260", VOCABULARY_ID, "4287533", PASCAL_GIBUT_SIAMOIS_FR, CHARTRES,
                    OffsetDateTime.of(2012, 6, 12, 0, 0, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2012, 7, 17, 0, 0, 0, 0, ZoneOffset.UTC),
                    Set.of(
                            new SpatialUnitSeeder.SpatialUnitKey(EMPRISE_DE_FOUILLE_DE_C_309_01)
                    )),
            new ActionUnitSeeder.ActionUnitSpecs("chartres-C309_11", "Pôle Gare - rue du Chemin de Fer et rue du Faubourg Saint-Jean (phase 1)",
                    "C309_11",
                    "0610216", VOCABULARY_ID, "4287534", PASCAL_GIBUT_SIAMOIS_FR, CHARTRES,
                    OffsetDateTime.of(2015, 6, 8, 0, 0, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2015, 9, 29, 0, 0, 0, 0, ZoneOffset.UTC),
                    null)
    );

    List<RecordingUnitSeeder.RecordingUnitSpecs> recUnits = List.of(
            new RecordingUnitSeeder.RecordingUnitSpecs("chartres-C309_01-1100", 1100,
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, US_EXTERNAL_ID),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                    PASCAL_GIBUT_SIAMOIS_FR,
                    CHARTRES,
                    PASCAL_GIBUT_SIAMOIS_FR,
                    PASCAL_GIBUT_SIAMOIS_FR,
                    List.of(PASCAL_GIBUT_SIAMOIS_FR, "duflos.franck@siamois.fr"),
                    OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                    null,
                    null,
                    new SpatialUnitSeeder.SpatialUnitKey(EMPRISE_DE_FOUILLE_DE_C_309_01),
                    new ActionUnitSeeder.ActionUnitKey(CHARTRES_C_309_01)
            ),
            new RecordingUnitSeeder.RecordingUnitSpecs(CHARTRES_C_309_01_1015, 1100,
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, US_EXTERNAL_ID),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287537"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287540"),
                    PASCAL_GIBUT_SIAMOIS_FR,
                    CHARTRES,
                    PASCAL_GIBUT_SIAMOIS_FR,
                    PASCAL_GIBUT_SIAMOIS_FR,
                    List.of(PASCAL_GIBUT_SIAMOIS_FR),
                    OffsetDateTime.of(2012, 7, 5, 0, 0, 0, 0, ZoneOffset.UTC),
                    null,
                    null,
                    new SpatialUnitSeeder.SpatialUnitKey(EMPRISE_DE_FOUILLE_DE_C_309_01),
                    new ActionUnitSeeder.ActionUnitKey(CHARTRES_C_309_01)
            )
    );

    List<SpecimenSeeder.SpecimenSpecs> specimens = List.of(
            new SpecimenSeeder.SpecimenSpecs(
                    "chartres-C309_01-1100-1",
                    1,
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287543"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4286252"),
                    PASCAL_GIBUT_SIAMOIS_FR,
                    CHARTRES,
                    List.of(PASCAL_GIBUT_SIAMOIS_FR),
                    List.of(PASCAL_GIBUT_SIAMOIS_FR),
                    OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                    new RecordingUnitSeeder.RecordingUnitKey(CHARTRES_C_309_01_1015)
            ),
            new SpecimenSeeder.SpecimenSpecs(
                    "chartres-C309_01-1100-57",
                    1,
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287542"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4286251"),
                    PASCAL_GIBUT_SIAMOIS_FR,
                    CHARTRES,
                    List.of(PASCAL_GIBUT_SIAMOIS_FR),
                    List.of(PASCAL_GIBUT_SIAMOIS_FR),
                    OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                    new RecordingUnitSeeder.RecordingUnitKey(CHARTRES_C_309_01_1015)
            )
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

/*        Map<String, Vocabulary> result = thesaurusSeeder.seed(thesauri);
        conceptSeeder.seed(result.get(VOCABULARY_ID), concepts);



        actionUnitSeeder.seed(actions);
        recordingUnitSeeder.seed(recUnits);
        specimenSeeder.seed(specimens);*/


    }



}
