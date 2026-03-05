package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.rules.EnabledRulesEngine;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.springframework.core.convert.ConversionService;

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

    @Mock private EnabledRulesEngine enabledRulesEngine;

    private BiConsumer<CustomField, ConceptDTO> scopeCallback;


    private AbstractEntityDTO unit;
    private RecordingUnitDTO unit2;

    @BeforeEach
    void setup() {
        when(formContextServices.getFormService()).thenReturn(formService);
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


    private static SpatialUnitDTO su(long id, String name) {
        SpatialUnitDTO su = mock(SpatialUnitDTO.class);
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
        Set<SpatialUnitDTO> selected = new HashSet<>();
        when(treeAnswer.getValue()).thenReturn(selected);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(mock(CustomField.class), treeAnswer);
        response.setAnswers(answers);

        when(formService.initOrReuseResponse(isNull(), eq(unit), eq(fieldSource), eq(false))).thenReturn(response);
        when(formService.buildEnabledEngine(fieldSource)).thenReturn(enabledRulesEngine);

        TreeNode<SpatialUnitDTO> root = new DefaultTreeNode<>(null, null);
        when(spatialUnitTreeService.buildTree()).thenReturn(root);

        // act
        ctx.init(false);

        // assert
        assertSame(response, ctx.getFormResponse());
        verify(formService).initOrReuseResponse(isNull(), eq(unit), eq(fieldSource), eq(false));
        verify(formService).buildEnabledEngine(fieldSource);
        verify(enabledRulesEngine).applyAll(any(), any());

        // tree UI state created
        assertSame(root, ctx.getRoot(treeAnswer));
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
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(r);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);


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

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        ctx.flushBackToEntity();

        verify(formService).updateJpaEntityFromResponse(response, unit);
    }

    @Test
    void spatialTree_addAndRemove_marksModifiedAndUpdatesSelection() {
        // arrange
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel treeAnswer = mock(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel.class);
        when(treeAnswer.getValue()).thenReturn(null); // force init set in addSUToSelection

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(Map.of(mock(CustomField.class), treeAnswer)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);
        when(spatialUnitTreeService.buildTree()).thenReturn(new DefaultTreeNode<>(null, null));

        ctx.init(false);

        SpatialUnitDTO su1 = mock(SpatialUnitDTO.class);

        // act: add
        ctx.addSUToSelection(treeAnswer, su1);

        // assert: marked modified + global flag
        verify(treeAnswer).setHasBeenModified(true);
        assertTrue(ctx.isHasUnsavedModifications());

        // now selection exists in UI model, removal should work
        boolean removed = ctx.removeSpatialUnit(treeAnswer, su1);
        assertTrue(removed);
        verify(treeAnswer, times(2)).setHasBeenModified(true); // add + remove
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
        SpatialUnitDTO b = mock(SpatialUnitDTO.class);
        when(b.getId()).thenReturn(2L);

        // parents of B include A; parents of A none
        when(spatialUnitService.findDirectParentsOf(2L)).thenReturn(List.of(a));
        when(spatialUnitService.findDirectParentsOf(1L)).thenReturn(Collections.emptyList());

        Set<SpatialUnitDTO> selected = new LinkedHashSet<>(List.of(a, b));

        // act
        List<SpatialUnitDTO> chips = ctx.getNormalizedSelectedUnits(selected);

        // assert: only A remains (B removed because ancestor A selected)
        assertEquals(1, chips.size());
        assertSame(a, chips.get(0));
    }


    @Test
    void getNormalizedSelectedUnits_sortsByName_caseInsensitive_nullsLast() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                unit, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );


        SpatialUnitDTO b = su(2L, "beta");
        SpatialUnitDTO a = su(1L, "Alpha");
        SpatialUnitDTO n = su(3L, null);

        when(spatialUnitService.findDirectParentsOf(anyLong())).thenReturn(Collections.emptyList());

        Set<SpatialUnitDTO> selected = new LinkedHashSet<>(List.of(b, n, a));

        List<SpatialUnitDTO> chips = ctx.getNormalizedSelectedUnits(selected);

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
        ConceptAutocompleteDTO newConceptl = mock(ConceptAutocompleteDTO.class);
        ConceptLabelDTO cL = mock(ConceptLabelDTO.class);
        when(newValue.getConceptLabelToDisplay()).thenReturn(cL);
        when(newValue.getConceptLabelToDisplay().getConcept()).thenReturn(newConcept);

        // act
        ctx.handleConceptChange(scopeField, newValue);

        // assert
        verify(ans).setHasBeenModified(false);
        assertFalse(ctx.isHasUnsavedModifications());
        verify(enabledRulesEngine).onAnswerChange(eq(scopeField), eq(newConceptl), any(), any());
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
        when(otherField.getValueBinding()).thenReturn("otherBinding");
        CustomFieldAnswerSelectOneFromFieldCodeViewModel ans = mock(CustomFieldAnswerSelectOneFromFieldCodeViewModel.class);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(Map.of(otherField, ans)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        ConceptAutocompleteDTO newValue = mock(ConceptAutocompleteDTO.class);
        ConceptDTO newConcept = mock(ConceptDTO.class);
        ConceptLabelDTO cL = mock(ConceptLabelDTO.class);
        when(newValue.getConceptLabelToDisplay()).thenReturn(cL);
        when(newValue.getConceptLabelToDisplay().getConcept()).thenReturn(newConcept);

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
    void getSpatialUnitOptions_returnsEmpty_whenUnitNotRecordingUnit() {
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                new AbstractEntityDTO() {
                    @Override
                    public Long getId() {
                        return 0L;
                    }
                }, fieldSource, formContextServices,conversionService,
                scopeCallback, "scopeBinding"
        );
        assertTrue(ctx.getSpatialUnitOptions().isEmpty());
        verifyNoInteractions(spatialUnitService);
    }

    @Test
    void getSpatialUnitOptions_delegatesToService_whenUnitIsRecordingUnit() {
        RecordingUnitDTO ru = mock(RecordingUnitDTO.class);
        EntityFormContext<AbstractEntityDTO> ctx = new EntityFormContext<>(
                ru, fieldSource, formContextServices, conversionService,
                scopeCallback, "scopeBinding"
        );

        List<SpatialUnitSummaryDTO> opts = List.of(mock(SpatialUnitSummaryDTO.class));
        when(spatialUnitService.getSpatialUnitOptionsFor(ru)).thenReturn(opts);

        assertSame(opts, ctx.getSpatialUnitOptions());
        verify(spatialUnitService).getSpatialUnitOptionsFor(ru);
    }
}
