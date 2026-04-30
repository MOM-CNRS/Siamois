package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.GeoApiService;
import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.rules.EnabledRulesEngine;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;

import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityFormContextTest {

    @Mock private FieldSource fieldSource;
    @Mock private FormContextServices formContextServices;
    @Mock private FormService formService;
    @Mock private SpatialUnitTreeService spatialUnitTreeService;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private RecordingUnitService recordingUnitService;
    private ConversionService conversionService;

    @Mock
    private GeoPlatService geoPlatService;
    @Mock
    private GeoApiService geoApiService;

    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;

    @Mock
    private UIComponent component;

    @Mock
    private FacesContext facesContext;

    @Mock
    private CustomFieldSelectOneSpatialUnit customFieldSelectOneSpatialUnit;

    @Mock
    private CustomFieldSelectMultipleSpatialUnitTree customFieldSelectMultipleSpatialUnitTree;

    @Mock private EnabledRulesEngine enabledRulesEngine;

    @Mock private CustomFieldMeasurementService customFieldMeasurementService;

    @Mock
    private ActionUnitDTO actionUnitDTO;

    @Mock
    private RecordingUnitDTO recordingUnitDTO;

    @Mock
    private InstitutionDTO institutionDTO;

    @Mock
    private Concept concept;

    @Mock
    private ConceptDTO conceptDTO;

    @Mock
    private FullAddress fullAddress;

    @Mock
    private Map<String, Object> attributesMap;



    private BiConsumer<CustomField, ConceptDTO> scopeCallback;


    private AbstractEntityDTO unit;
    private RecordingUnitDTO unit2;

    @BeforeEach
    void setup() {
        when(formContextServices.getFormService()).thenReturn(formService);
        when(formContextServices.getGeoApiService()).thenReturn(geoApiService);
        when(formContextServices.getGeoPlatService()).thenReturn(geoPlatService);
        when(formContextServices.getConceptMapper()).thenReturn(conceptMapper);
        when(formContextServices.getConceptService()).thenReturn(conceptService);
        when(formContextServices.getSpatialUnitTreeService()).thenReturn(spatialUnitTreeService);
        when(formContextServices.getSpatialUnitService()).thenReturn(spatialUnitService);
        when(formContextServices.getRecordingUnitService()).thenReturn(recordingUnitService);


        unit = new AbstractEntityDTO() {
            @Override
            public Long getId() {
                return 0L;
            }
        };
        scopeCallback = mock(BiConsumer.class);
    }


    private static SpatialUnitSummaryDTO suSummary(long id, String name) {
        SpatialUnitSummaryDTO su = mock(SpatialUnitSummaryDTO.class);
        when(su.getId()).thenReturn(id);
        when(su.getName()).thenReturn(name);
        return su;
    }

    @Test
    void init_callsFormServiceAndBuildsEnabledEngineAndAppliesRules_andInitializesTreeStates() {
        // arrange
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );

        // build a response containing a spatial-unit-tree answer
        CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel treeAnswer = mock(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel.class);
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(mock(CustomField.class), treeAnswer);
        response.setAnswers(answers);

        when(formService.initOrReuseResponse(isNull(), eq(unit), eq(fieldSource), eq(false))).thenReturn(response);
        when(formService.buildEnabledEngine(fieldSource)).thenReturn(enabledRulesEngine);


        // act
        ctx.init(false);

        // assert
        assertSame(response, ctx.getFormResponse());
        verify(formService).initOrReuseResponse(isNull(), eq(unit), eq(fieldSource), eq(false));
        verify(formService).buildEnabledEngine(fieldSource);
        verify(enabledRulesEngine).applyAll(any(), any());


    }


    @Test
    void getFieldAnswer_returnsNull_whenNoResponseOrAnswers() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );

        CustomField f = mock(CustomField.class);

        assertNull(ctx.getFieldAnswer(f));

        CustomFormResponseViewModel r = new CustomFormResponseViewModel();
        r.setAnswers(null);
        // hack: set via init() stubbing
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(r);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());

        ctx.init(false);
        assertNull(ctx.getFieldAnswer(f));
    }

    @Test
    void isColumnEnabled_defaultsToTrue_whenNoRuleApplied() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );

        CustomField f = mock(CustomField.class);
        when(f.getId()).thenReturn(77L);

        assertTrue(ctx.isColumnEnabled(f));
    }

    @Test
    void markFieldModified_marksAnswerAndSetsGlobalFlag() {
        // arrange
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        CustomField field = mock(CustomField.class);
        CustomFieldAnswerViewModel ans = mock(CustomFieldAnswerViewModel.class);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(Map.of(field, ans)));
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        // act
        ctx.markFieldModified(field);

        // assert
        verify(ans).setHasBeenModified(true);
        assertTrue(ctx.isHasUnsavedModifications());
    }

    @Test
    void onConceptChanged_delegatesToEnabledEngine() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>());
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);


        ctx.init(false);

        CustomField f = mock(CustomField.class);
        ConceptAutocompleteDTO c = mock(ConceptAutocompleteDTO.class);

        ctx.onConceptChanged(f, c);

        verify(enabledRulesEngine).onAnswerChange(eq(f), eq(c), any(), any());
    }

    @Test
    void flushBackToEntity_callsFormServiceUpdate() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>());
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        ctx.flushBackToEntity();

        verify(formService).updateJpaEntityFromResponse(response, unit);
    }


    @Test
    void getNormalizedSelectedUnits_removesDescendants_whenAncestorSelected() {
        // arrange: A is parent of B
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        SpatialUnitDTO a = mock(SpatialUnitDTO.class);
        when(a.getId()).thenReturn(1L);
        SpatialUnitSummaryDTO aSummary = mock(SpatialUnitSummaryDTO.class);
        when(aSummary.getId()).thenReturn(1L);
        SpatialUnitSummaryDTO bSummary = mock(SpatialUnitSummaryDTO.class);
        when(bSummary.getId()).thenReturn(2L);

        // parents of B include A; parents of A none
        when(spatialUnitService.findDirectParentsOf(2L)).thenReturn(List.of(a));
        when(spatialUnitService.findDirectParentsOf(1L)).thenReturn(Collections.emptyList());

        Set<SpatialUnitSummaryDTO> selected = new LinkedHashSet<>(List.of(aSummary, bSummary));

        // act
        List<SpatialUnitSummaryDTO> chips = ctx.getNormalizedSelectedUnits(selected);

        // assert: only A remains (B removed because ancestor A selected)
        assertEquals(1, chips.size());
        assertSame(aSummary, chips.get(0));
    }


    @Test
    void getNormalizedSelectedUnits_sortsByName_caseInsensitive_nullsLast() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        SpatialUnitSummaryDTO b = suSummary(2L, "beta");
        SpatialUnitSummaryDTO a = suSummary(1L, "Alpha");
        SpatialUnitSummaryDTO n = suSummary(3L, null);

        when(spatialUnitService.findDirectParentsOf(anyLong())).thenReturn(Collections.emptyList());

        Set<SpatialUnitSummaryDTO> selected = new LinkedHashSet<>(List.of(b, n, a));

        List<SpatialUnitSummaryDTO> chips = ctx.getNormalizedSelectedUnits(selected);

        assertEquals(List.of(a, b, n), chips);
    }

    @Test
    void handleConceptChange_marksModified_reEvaluatesRules_andInvokesCallback_whenFormScopeField() {
        // arrange
        String scopeBinding = "scopeBinding";
        unit2 = new RecordingUnitDTO();
        unit2.setType(new ConceptDTO());
        EntityFormContext<RecordingUnitDTO> ctx = new EntityFormContext<>(
                unit2, fieldSource, formContextServices, conversionService,
                scopeCallback, scopeBinding
        );

        CustomField scopeField = mock(CustomField.class);
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        when(scopeField.getIsSystemField()).thenReturn(true);
        when(scopeField.getValueBinding()).thenReturn(scopeBinding);
        CustomFieldAnswerSelectOneFromFieldCodeViewModel ans = mock(CustomFieldAnswerSelectOneFromFieldCodeViewModel.class);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(Map.of(scopeField, ans)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);
        when(recordingUnitService.save(any(RecordingUnitDTO.class))).thenReturn(new RecordingUnitDTO());
        when(recordingUnitService.fullIdentifierAlreadyExistInAction(any(RecordingUnitDTO.class))).thenReturn(false);

        ctx.init(false);

        ConceptAutocompleteDTO newValue = mock(ConceptAutocompleteDTO.class);
        ConceptDTO newConcept = mock(ConceptDTO.class);
        ConceptLabelDTO cL = mock(ConceptLabelDTO.class);
        when(newValue.getConceptLabelToDisplay()).thenReturn(cL);
        when(newValue.getConceptLabelToDisplay().getConcept()).thenReturn(newConcept);

        // act
        ctx.handleConceptChange(scopeField, newValue);

        // assert
        verify(ans).setHasBeenModified(false);
        assertFalse(ctx.isHasUnsavedModifications());
        verify(enabledRulesEngine).onAnswerChange(eq(scopeField), eq(newValue), any(), any());
        verify(scopeCallback).accept(scopeField, newConcept);
    }

    @Test
    void handleConceptChange_doesNotInvokeCallback_whenNotFormScopeField() {
        unit2 = new RecordingUnitDTO();
        unit2.setType(new ConceptDTO());
        EntityFormContext<RecordingUnitDTO> ctx = new EntityFormContext<>(
                unit2, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );

        CustomField otherField = mock(CustomField.class);
        when(otherField.getIsSystemField()).thenReturn(true);
        when(formContextServices.getCustomFieldMeasurementService()).thenReturn(customFieldMeasurementService);
        when(formContextServices.getCustomFieldMeasurementService().find(any(int.class))).thenReturn(Page.empty());
        when(otherField.getValueBinding()).thenReturn("otherBinding");
        CustomFieldAnswerSelectOneFromFieldCodeViewModel ans = mock(CustomFieldAnswerSelectOneFromFieldCodeViewModel.class);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(Map.of(otherField, ans)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        ConceptAutocompleteDTO newValue = mock(ConceptAutocompleteDTO.class);


        ctx.handleConceptChange(otherField, newValue);

        verify(scopeCallback, never()).accept(any(), any());
    }

    @Test
    void getAutocompleteClass_returnsExpectedCssClass() {
        EntityFormContext<AbstractEntityDTO> ctx1 = new EntityFormContext<>(
                mock(RecordingUnitDTO.class), fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("recording-unit-autocomplete", ctx1.getAutocompleteClass());

        EntityFormContext<AbstractEntityDTO> ctx2 = new EntityFormContext<>(
                mock(SpatialUnitDTO.class), fieldSource,formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("spatial-unit-autocomplete", ctx2.getAutocompleteClass());

        EntityFormContext<AbstractEntityDTO> ctx3 = new EntityFormContext<>(
                new AbstractEntityDTO() {
                    @Override
                    public Long getId() {
                        return 0L;
                    }
                }, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("", ctx3.getAutocompleteClass());
    }



    @Test
    void getSpatialUnitOptions_returnsInternalResults_whenSourceIsNotSpecifiedForActionUnit() {
        // Préparation
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                actionUnitDTO, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );
        String query = "test";
        when(actionUnitDTO.getCreatedByInstitution()).thenReturn(institutionDTO);
        when(institutionDTO.getId()).thenReturn(1L);
        when(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, query))
                .thenReturn(List.of(new PlaceSuggestionDTO(), new PlaceSuggestionDTO()));

        // Exécution
        List<PlaceSuggestionDTO> results = ctx.fetchSuggestions(query, "UNKNOWN");

        // Vérification
        assertEquals(2, results.size());
        verify(spatialUnitService).findTop3ByInstitutionIdBySimilarity(1L, query);
        verifyNoInteractions(geoApiService, geoPlatService);
    }

    @Test
    void getSpatialUnitOptions_returnsMergedResults_whenSourceIsINSEEForActionUnit() {
        // Préparation
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                actionUnitDTO, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );
        String query = "Paris";
        when(actionUnitDTO.getCreatedByInstitution()).thenReturn(institutionDTO);
        when(institutionDTO.getId()).thenReturn(1L);
        when(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, query))
                .thenReturn(List.of(createInternalDTO("1", "Paris")));
        when(geoApiService.fetchCommunes(query))
                .thenReturn(List.of(createExternalDTO("2", "Paris 1"), createExternalDTO("1", "Paris")));

        // Exécution
        List<PlaceSuggestionDTO> results =ctx.fetchSuggestions(query, "INSEE");

        // Vérification
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(dto -> dto.getCode().equals("1")));
        assertTrue(results.stream().anyMatch(dto -> dto.getCode().equals("2")));
    }

    @Test
    void getSpatialUnitOptions_returnsMergedResults_whenSourceIsGEOPLATForActionUnit() {
        // Préparation
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                actionUnitDTO, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );
        String query = "Lyon";
        when(actionUnitDTO.getCreatedByInstitution()).thenReturn(institutionDTO);
        when(institutionDTO.getId()).thenReturn(1L);
        when(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, query))
                .thenReturn(List.of(createInternalDTO("1", "Lyon")));
        when(conceptService.findById(418)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(conceptDTO);
        when(geoPlatService.search(query))
                .thenReturn(List.of(createFullAddress("Lyon 1")));

        // Exécution
        List<PlaceSuggestionDTO> results =  ctx.fetchSuggestions(query, "GEOPLAT");

        // Vérification
        assertEquals(2, results.size());

    }

    @Test
    void getSpatialUnitOptions_returnsOnlyUniqueResults_whenDuplicatesExist() {
        // Préparation

        String query = "Marseille";
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                actionUnitDTO, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );

        when(actionUnitDTO.getCreatedByInstitution()).thenReturn(institutionDTO);
        when(institutionDTO.getId()).thenReturn(1L);
        when(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, query))
                .thenReturn(List.of(createInternalDTO("1", "Marseille")));
        when(geoApiService.fetchCommunes(query))
                .thenReturn(List.of(createExternalDTO("1", "Marseille"), createExternalDTO("1", "Marseille")));

        // Exécution
        List<PlaceSuggestionDTO> results = ctx.fetchSuggestions(query, "INSEE");

        // Vérification
        assertEquals(1, results.size());
    }

    @Test
    void getSpatialUnitOptions_returnsEmpty_whenNoResultsFound() {
        // Préparation
        String query = "Unknown";
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                actionUnitDTO, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );
        when(actionUnitDTO.getCreatedByInstitution()).thenReturn(institutionDTO);
        when(institutionDTO.getId()).thenReturn(1L);
        when(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, query))
                .thenReturn(Collections.emptyList());
        when(geoApiService.fetchCommunes(query))
                .thenReturn(Collections.emptyList());

        // Exécution
        List<PlaceSuggestionDTO> results =  ctx.fetchSuggestions(query, "INSEE");

        // Vérification
        assertTrue(results.isEmpty());
    }

    // Méthodes utilitaires pour créer des objets de test
    private PlaceSuggestionDTO createInternalDTO(String code, String name) {
        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setSourceName("SIAMOIS");
        return dto;
    }

    private PlaceSuggestionDTO createExternalDTO(String code, String name) {
        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setSourceName("INSEE");
        return dto;
    }

    private FullAddress createFullAddress(String label) {
        FullAddress address = new FullAddress();
        address.setLabel(label);
        return address;
    }
}
