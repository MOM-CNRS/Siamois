package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.ThesaurusSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.UnitDefinitionSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldAnswerDTO;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.initializer.seeder.customform.*;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Slf4j
@Service
@Order(0)
@RequiredArgsConstructor
public class DefaultFormsDatasetInitializer implements DatabaseInitializer {

    private static final String UI_G_12_UI_MD_6_UI_LG_6 = "ui-g-12 ui-md-6 ui-lg-6";
    private final UnitDefinitionSeeder unitDefinitionSeeder;
    private final VocabularyRepository vocabularyRepository;
    private final ConceptSeeder conceptSeeder;
    private final ThesaurusSeeder thesaurusSeeder;
    private final CustomFieldSeeder customFieldSeeder;
    private final CustomFormScopeSeeder customFormScopeSeeder;

    public static final String BI_BI_PENCIL_SQUARE = "bi bi-pencil-square";
    public static final String MR_2_RECORDING_UNIT_TYPE_CHIP = "mr-2 recording-unit-type-chip";
    public static final String UI_G_12_UI_MD_6_UI_LG_2 = "ui-g-12 ui-md-6 ui-lg-2";
    public static final String COMMON_HEADER_GENERAL = "common.header.general";
    public static final String UI_G_12_UI_MD_6_UI_LG_3 = "ui-g-12 ui-md-6 ui-lg-3";
    public static final String UI_G_12_UI_MD_12_UI_LG_12 = "ui-g-12 ui-md-12 ui-lg-12";


    static final String DEFAULT_VOCABULARY_INSTANCE_URI = "https://thesaurus.mom.fr";
    static final String DEFAULT_VOCABULARY_ID = "th230";
    private final CustomFormSeeder customFormSeeder;

    // Default Siamois Thesaurus
    List<ThesaurusSeeder.ThesaurusSpec> thesauri = List.of(
            new ThesaurusSeeder.ThesaurusSpec(DEFAULT_VOCABULARY_INSTANCE_URI, DEFAULT_VOCABULARY_ID),
            new ThesaurusSeeder.ThesaurusSpec(DEFAULT_VOCABULARY_INSTANCE_URI, "th252")
    );

    // Default Siamois field concept
    List<ConceptSeeder.ConceptSpec> concepts = List.of(

            // Champs
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287605", "Type d'unité", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287606", "Cycle géomorphologique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286197", "Interprétation normalisée", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287629", "Subdivision", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287627", "Unité élémentaire", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287628", "Unité incluante", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287640", "Identifiant de l'unité d'enregistrement", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286244", "Unité d'action d'appartenance d'une unité d'enregistrement", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286245", "Unité spatiale d'appartenance d'une unité d'enregistrement", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286195", "Auteur scientifique/technique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287594", "Contributeur(s) scientifique(s)/technique(s)", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287607", "Cycle géomorphologique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287610", "Composition de la matrice", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287608", "Couleur de la matrice", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287609", "Texture de la matrice", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287641", "Forme de l'érosion", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287643", "Orientation de l'érosion", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287642", "Profil de l'érosion", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287614", "TAQ", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287613", "TPQ", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287612", "Phase chronologique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287611", "Description", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286198", "Date d'ouverture", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286199", "Date de fermeture", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287646", "Relation stratigrahique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4289320", "Z inf", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4289321", "Z sup", "fr"),

            // Réponses de champs
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287636", "Altération", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287637", "Composite", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287638", "Dépôt", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287639", "Erosion", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4289277", "Fait partie de", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4289278", "Contient", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4289279", "Commentaires", "fr")
    );

    CustomFieldSeederSpec notesFields = new CustomFieldSeederSpec(
            CustomFieldText.class,
            true,
            "common.field.comments",
            new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4289279"),
            "comments",
            null,
            null,
            null,
            true,
            null
    );

    CustomFieldSeederSpec zInfField = CustomFieldSeederSpec.builder()
            .isSystemField(true)
            .answerClass(CustomFieldMeasurement.class)
            .label("recordingunit.property.zInf")
            .valueBinding("zInf")
            .conceptKey(new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4289320"))
            .unitDefinitionDTO(
                    UnitDefinitionDTO.builder()
                            .id(0L)
                            .label("Mètres")
                            .concept(ConceptDTO.builder()
                                    .externalId("4289327")
                                    .vocabulary(
                                            VocabularyDTO.builder()
                                                    .baseUri(DEFAULT_VOCABULARY_INSTANCE_URI)
                                                    .externalVocabularyId("th252")
                                                    .build()
                                    )
                                    .build())
                            .symbol("m")
                            .factorToBase(1.0)
                            .systemBase(true)
                            .dimension(UnitDefinition.Dimension.LENGTH)
                            .build()
            ).build();
    CustomFieldSeederSpec zSupField = CustomFieldSeederSpec.builder()
            .isSystemField(true)
            .answerClass(CustomFieldMeasurement.class)
            .label("recordingunit.property.zSup")
            .valueBinding("zSup")
            .conceptKey(new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4289321"))
            .unitDefinitionDTO(
                    UnitDefinitionDTO.builder()
                            .id(0L)
                            .label("Mètres")
                            .concept(ConceptDTO.builder()
                                    .externalId("4289327")
                                    .vocabulary(
                                            VocabularyDTO.builder()
                                                    .baseUri(DEFAULT_VOCABULARY_INSTANCE_URI)
                                                    .externalVocabularyId("th252")
                                                    .build()
                                    )
                                    .build())
                            .symbol("m")
                            .factorToBase(1.0)
                            .systemBase(true)
                            .dimension(UnitDefinition.Dimension.LENGTH)
                            .build()
            ).build();

    // Default Siamois field
    List<CustomFieldSeederSpec> fields = List.of(
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.property.type",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287605"),
                    "type",
                    BI_BI_PENCIL_SQUARE,
                    MR_2_RECORDING_UNIT_TYPE_CHIP,
                    "SIARU.TYPE"
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.property.geomorpho",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287606"),
                    "geomorphologicalCycle",
                    BI_BI_PENCIL_SQUARE,
                    MR_2_RECORDING_UNIT_TYPE_CHIP,
                    "SIARU.GEOMORPHO"
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.property.interpretation",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286197"),
                    "normalizedInterpretation",
                    BI_BI_PENCIL_SQUARE,
                    MR_2_RECORDING_UNIT_TYPE_CHIP,
                    "SIARU.INTERPRETATION"
            ),
            new CustomFieldSeederSpec(
                    CustomFieldText.class,
                    true,
                    "common.label.identifier",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287640"),
                    "fullIdentifier",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneActionUnit.class,
                    true,
                    "recordingunit.field.actionUnit",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286244"),
                    "actionUnit",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneSpatialUnit.class,
                    true,
                    "recordingunit.field.spatialUnit",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286245"),
                    "spatialUnit",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOnePerson.class,
                    true,
                    "recordingunit.field.author",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286195"),
                    "author",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectMultiplePerson.class,
                    true,
                    "recordingunit.field.contributors",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287594"),
                    "contributors",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.field.geomorphoAgent",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287607"),
                    "geomorphologicalAgent",
                    BI_BI_PENCIL_SQUARE,
                    MR_2_RECORDING_UNIT_TYPE_CHIP,
                    "SIARU.GEOMORPHOAGENT"
            ),
            new CustomFieldSeederSpec(
                    CustomFieldText.class,
                    true,
                    "recordingunit.field.matrixComposition",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287610"),
                    "matrixComposition",
                    null,
                    null,
                    null,
                    true,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldText.class,
                    true,
                    "recordingunit.field.matrixColor",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287608"),
                    "matrixColor",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldText.class,
                    true,
                    "recordingunit.field.matrixTexture",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287609"),
                    "matrixTexture",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.field.erosionShape",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287641"),
                    "erosionShape",
                    null,
                    null,
                    RecordingUnit.EROSION_SHAPE_FIELD_CODE
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.field.erosionProfile",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287642"),
                    "erosionProfile",
                    null,
                    null,
                    RecordingUnit.EROSION_PROFILE_FIELD_CODE
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.field.erosionOrientation",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287643"),
                    "erosionOrientation",
                    null,
                    null,
                    RecordingUnit.EROSION_ORIENTATION_FIELD_CODE
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectOneFromFieldCode.class,
                    true,
                    "recordingunit.field.chronologicalPhase",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287612"),
                    "chronologicalPhase",
                    BI_BI_PENCIL_SQUARE,
                    MR_2_RECORDING_UNIT_TYPE_CHIP,
                    "SIARU.CHRONO"
            ),
            new CustomFieldSeederSpec(
                    CustomFieldInteger.class,
                    true,
                    "recordingunit.field.taq",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287614"),
                    "taq",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldInteger.class,
                    true,
                    "recordingunit.field.tpq",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287613"),
                    "tpq",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldText.class,
                    true,
                    "recordingunit.field.description",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287611"),
                    "description",
                    null,
                    null,
                    null,
                    true,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldDateTime.class,
                    true,
                    "recordingunit.field.openingDate",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286198"),
                    "openingDate",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldDateTime.class,
                    true,
                    "recordingunit.field.closingDate",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4286199"),
                    "closingDate",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectMultipleRecordingUnit.class,
                    true,
                    "common.field.parents",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4289277"),
                    "parents",
                    null,
                    null,
                    null
            ),
            new CustomFieldSeederSpec(
                    CustomFieldSelectMultipleRecordingUnit.class,
                    true,
                    "common.field.children",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4289278"),
                    "children",
                    null,
                    null,
                    null
            ),
            notesFields,
            zInfField,
            zSupField
    );


    // Regle d'activation du champ basé sur la valeur de la réponse à un autre champ
    EnabledWhenSpecSeedDTO erosionEnabledWhenDTO = new EnabledWhenSpecSeedDTO(
            EnabledWhenSpecSeedDTO.Operator.EQUALS,
            fields.get(1),
            List.of(
                    new CustomFieldAnswerDTO(
                            CustomFieldAnswerSelectOneFromFieldCode.class,
                            fields.get(1),
                            new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287639")
                    )
            )
    );
    EnabledWhenSpecSeedDTO matrixEnabledWhenDTO = new EnabledWhenSpecSeedDTO(
            EnabledWhenSpecSeedDTO.Operator.IN,
            fields.get(1),
            List.of(
                    new CustomFieldAnswerDTO(
                            CustomFieldAnswerSelectOneFromFieldCode.class,
                            fields.get(1),
                            new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287636")
                    ),
                    new CustomFieldAnswerDTO(
                            CustomFieldAnswerSelectOneFromFieldCode.class,
                            fields.get(1),
                            new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287637")
                    ),
                    new CustomFieldAnswerDTO(
                            CustomFieldAnswerSelectOneFromFieldCode.class,
                            fields.get(1),
                            new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287638")
                    )
            )
    );

    CustomColDTO matrixColorColDTO = new CustomColDTO(
            false,
            false,
            fields.get(10),
            UI_G_12_UI_MD_6_UI_LG_3,
            matrixEnabledWhenDTO
    );

    CustomColDTO matrixCompositionColDTO = new CustomColDTO(
            false,
            false,
            fields.get(9),
            UI_G_12_UI_MD_12_UI_LG_12,
            matrixEnabledWhenDTO
    );

    CustomColDTO matrixTextureColDTO =
            new CustomColDTO(
                    false,
                    false,
                    fields.get(11),
                    UI_G_12_UI_MD_6_UI_LG_3,
                    matrixEnabledWhenDTO
            );

    CustomColDTO erosionShapeCol = new CustomColDTO(
            false,
            false,
            fields.get(12),
            UI_G_12_UI_MD_6_UI_LG_3,
            erosionEnabledWhenDTO
    );

    CustomColDTO erosionProfileCol = new CustomColDTO(
            false,
            false,
            fields.get(13),
            UI_G_12_UI_MD_6_UI_LG_3,
            erosionEnabledWhenDTO
    );
    CustomColDTO erosionOrientationCol = new CustomColDTO(
            false,
            false,
            fields.get(14),
            UI_G_12_UI_MD_6_UI_LG_3,
            erosionEnabledWhenDTO
    );


    // Default form DTOs
    List<CustomFormDTO> forms = List.of(
            new CustomFormDTO(
                    "Le formulaire par défaut pour les unités d'enregistrements non stratigraphique",
                    "Formulaire d'unité non stratigraphique",
                    List.of(new CustomFormPanelDTO(
                            "",
                            COMMON_HEADER_GENERAL,
                            List.of(
                                    new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            true,
                                                            true,
                                                            fields.get(4),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(5),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(6),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(7),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(19),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(20),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    ),
                                    new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(3),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(0),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(2),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(18),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )

                                            )
                                    )
                            ),
                            true
                    ))
            ),
            new CustomFormDTO(
                    "Le formulaire par défaut pour les unités d'enregistrements stratigraphique",
                    "Formulaire d'unité stratigraphique",
                    List.of(new CustomFormPanelDTO(
                                    "",
                                    COMMON_HEADER_GENERAL,
                                    List.of(new CustomRowDTO(
                                                    List.of(
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    fields.get(5),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    true,
                                                                    fields.get(21),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    true,
                                                                    fields.get(22),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    true,
                                                                    fields.get(3),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    true,
                                                                    fields.get(0),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    fields.get(1),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    fields.get(8),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            ),
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    fields.get(2),
                                                                    UI_G_12_UI_MD_6_UI_LG_3
                                                            )
                                                    )
                                            ),
                                            new CustomRowDTO(
                                                    List.of(
                                                            erosionShapeCol,
                                                            erosionProfileCol,
                                                            erosionOrientationCol
                                                    )
                                            ),
                                            new CustomRowDTO(
                                                    List.of(
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    fields.get(18),
                                                                    UI_G_12_UI_MD_12_UI_LG_12
                                                            )
                                                    )
                                            ),
                                            new CustomRowDTO(
                                                    List.of(
                                                            new CustomColDTO(
                                                                    false,
                                                                    false,
                                                                    notesFields,
                                                                    UI_G_12_UI_MD_12_UI_LG_12
                                                            )
                                                    )
                                            )
                                    ),
                                    true
                            ),
                            new CustomFormPanelDTO(
                                    "",
                                    "recordingunit.panel.chronology",
                                    List.of(new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(15),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(17),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(16),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    )),
                                    true
                            ),
                            new CustomFormPanelDTO(
                                    "",
                                    "recordingunit.panel.measurements",
                                    List.of(new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            zInfField,
                                                            UI_G_12_UI_MD_6_UI_LG_6
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            zSupField,
                                                            UI_G_12_UI_MD_6_UI_LG_6
                                                    )
                                            )
                                    )),
                                    true,
                                    true
                            ),
                            new CustomFormPanelDTO(
                                    "",
                                    COMMON_HEADER_GENERAL,
                                    List.of(new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(19),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(20),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(6),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(7),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    )),
                                    true
                            )
                    )
            ),
            new CustomFormDTO(
                    "Le formulaire par défaut pour les unités d'enregistrements sans type",
                    "Formulaire d'unité d'enregistrements sans type",
                    List.of(new CustomFormPanelDTO(
                            "",
                            COMMON_HEADER_GENERAL,
                            List.of(new CustomRowDTO(
                                            List.of(
                                                    new CustomColDTO(
                                                            true,
                                                            true,
                                                            fields.get(4),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(5),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(6),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(7),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(19),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(20),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    ),
                                    new CustomRowDTO(

                                            List.of(
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(3),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(0),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            false,
                                                            fields.get(18),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    )),
                            true
                    ))
            )
    );

    List<CustomFormScopeDTO> scopes = List.of(
            new CustomFormScopeDTO(
                    FormScope.ScopeLevel.GLOBAL_DEFAULT.toString(),
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, concepts.get(3).externalId()),
                    forms.get(0)
            ),
            new CustomFormScopeDTO(
                    FormScope.ScopeLevel.GLOBAL_DEFAULT.toString(),
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, concepts.get(4).externalId()),
                    forms.get(1)
            ),
            new CustomFormScopeDTO(
                    FormScope.ScopeLevel.GLOBAL_DEFAULT.toString(),
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, concepts.get(5).externalId()),
                    forms.get(1)
            ),
            new CustomFormScopeDTO(
                    FormScope.ScopeLevel.GLOBAL_DEFAULT.toString(),
                    null,
                    forms.get(1)
            )
    );


    @Override
    public void initialize() throws DatabaseDataInitException {

        Map<String, Vocabulary> result = thesaurusSeeder.seed(thesauri);


        Vocabulary vocabulary = result.get("th252");
        VocabularyDTO vocabulary2 = new VocabularyDTO();
        vocabulary2.setExternalVocabularyId("th252");
        ConceptDTO meterConcept = new ConceptDTO();
        meterConcept.setVocabulary(vocabulary2);
        meterConcept.setExternalId("4289327");

        // Define meter
        UnitDefinitionDTO meter = UnitDefinitionDTO.builder()
                .id(0L)
                .label("Mètres")
                .concept(meterConcept)
                .symbol("m")
                .factorToBase(1.0)
                .systemBase(true)
                .dimension(UnitDefinition.Dimension.LENGTH)
                .build();

        unitDefinitionSeeder.seed(vocabulary, List.of(meter));


        conceptSeeder.seed(result.get(DEFAULT_VOCABULARY_ID), concepts);
        customFieldSeeder.seed(fields);
        customFormSeeder.seed(forms);
        customFormScopeSeeder.seed(scopes);


    }
}
