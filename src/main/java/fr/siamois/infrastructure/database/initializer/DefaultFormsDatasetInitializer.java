package fr.siamois.infrastructure.database.initializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.ThesaurusSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.initializer.seeder.customform.*;
import jakarta.persistence.Transient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Slf4j
@Service
@Order(0)
public class DefaultFormsDatasetInitializer implements DatabaseInitializer {

    public static final String BI_BI_PENCIL_SQUARE = "bi bi-pencil-square";
    public static final String MR_2_RECORDING_UNIT_TYPE_CHIP = "mr-2 recording-unit-type-chip";
    public static final String UI_G_12_UI_MD_6_UI_LG_2 = "ui-g-12 ui-md-6 ui-lg-2";
    public static final String COMMON_HEADER_GENERAL = "common.header.general";
    public static final String UI_G_12_UI_MD_6_UI_LG_3 = "ui-g-12 ui-md-6 ui-lg-3";
    private final ConceptSeeder conceptSeeder;
    private final ThesaurusSeeder thesaurusSeeder;
    private final CustomFieldSeeder customFieldSeeder;
    private final CustomFormScopeSeeder customFormScopeSeeder;

    static final String DEFAULT_VOCABULARY_INSTANCE_URI = "https://thesaurus.mom.fr";
    static final String DEFAULT_VOCABULARY_ID = "th230";
    private final CustomFormSeeder customFormSeeder;

    // Default Siamois Thesaurus
    List<ThesaurusSeeder.ThesaurusSpec> thesauri = List.of(
            new ThesaurusSeeder.ThesaurusSpec(DEFAULT_VOCABULARY_INSTANCE_URI, DEFAULT_VOCABULARY_ID)
    );

    // Default Siamois field concept
    List<ConceptSeeder.ConceptSpec> concepts = List.of(
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287605", "Type d'unité", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287606", "Cycle géomorphologique", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286197", "Interprétation normalisée", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287629", "Subdivision", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287627", "Unité élémentaire", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287628", "Unité incluante", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4287640", "Identifiant de l'unité d'enregistrement", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286244", "Unité d'action d'appartenance d'une unité d'enregistrement", "fr"),
            new ConceptSeeder.ConceptSpec(DEFAULT_VOCABULARY_ID, "4286245", "Unité spatiale d'appartenance d'une unité d'enregistrement", "fr")
    );

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
                    CustomFieldInteger.class,
                    true,
                    "common.label.identifier",
                    new ConceptSeeder.ConceptKey(DEFAULT_VOCABULARY_ID, "4287640"),
                    "identifier",
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
            )
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
                                                            true,
                                                            fields.get(5),
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
                                                            true,
                                                            true,
                                                            fields.get(4),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    ),
                                                    new CustomColDTO(
                                                            false,
                                                            true,
                                                            fields.get(5),
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
                                                            UI_G_12_UI_MD_6_UI_LG_2
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
                                                            fields.get(2),
                                                            UI_G_12_UI_MD_6_UI_LG_3
                                                    )
                                            )
                                    )),
                            true
                    ))
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
                                                            true,
                                                            fields.get(5),
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
                    forms.get(2)
            )
    );

    public DefaultFormsDatasetInitializer(ConceptSeeder conceptSeeder, ThesaurusSeeder thesaurusSeeder, CustomFieldSeeder customFieldSeeder, CustomFormScopeSeeder customFormScopeSeeder, CustomFormSeeder customFormSeeder) {
        this.conceptSeeder = conceptSeeder;
        this.thesaurusSeeder = thesaurusSeeder;
        this.customFieldSeeder = customFieldSeeder;
        this.customFormScopeSeeder = customFormScopeSeeder;
        this.customFormSeeder = customFormSeeder;
    }

    @Override
    public void initialize() throws DatabaseDataInitException {
        Map<String, Vocabulary> result = thesaurusSeeder.seed(thesauri);
        conceptSeeder.seed(result.get(DEFAULT_VOCABULARY_ID), concepts);
        customFieldSeeder.seed(fields);
        customFormSeeder.seed(forms);
        customFormScopeSeeder.seed(scopes);
    }
}