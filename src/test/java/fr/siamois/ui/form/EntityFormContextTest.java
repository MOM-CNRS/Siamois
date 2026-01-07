package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityFormContextTest {

    @Mock private FieldSource fieldSource;
    @Mock private FormService formService;
    @Mock private SpatialUnitTreeService spatialUnitTreeService;
    @Mock private SpatialUnitService spatialUnitService;

    @Mock private EnabledRulesEngine enabledRulesEngine;

    private BiConsumer<CustomField, Concept> scopeCallback;

    private Object unit;

    @BeforeEach
    void setup() {
        unit = new Object();
        scopeCallback = mock(BiConsumer.class);
    }


    private static SpatialUnit su(long id, String name) {
        SpatialUnit su = mock(SpatialUnit.class);
        when(su.getId()).thenReturn(id);
        when(su.getName()).thenReturn(name);
        return su;
    }

    @Test
    void init_callsFormServiceAndBuildsEnabledEngineAndAppliesRules_andInitializesTreeStates() {
        // arrange
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        // build a response containing a spatial-unit-tree answer
        CustomFieldAnswerSelectMultipleSpatialUnitTree treeAnswer = mock(CustomFieldAnswerSelectMultipleSpatialUnitTree.class);
        Set<SpatialUnit> selected = new HashSet<>();
        when(treeAnswer.getValue()).thenReturn(selected);

        CustomFormResponse response = new CustomFormResponse();
        Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();
        answers.put(mock(CustomField.class), treeAnswer);
        response.setAnswers(answers);

        when(formService.initOrReuseResponse(isNull(), eq(unit), eq(fieldSource), eq(false))).thenReturn(response);
        when(formService.buildEnabledEngine(fieldSource)).thenReturn(enabledRulesEngine);

        TreeNode<SpatialUnit> root = new DefaultTreeNode<>(null, null);
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
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomField f = mock(CustomField.class);

        assertNull(ctx.getFieldAnswer(f));

        CustomFormResponse r = new CustomFormResponse();
        r.setAnswers(null);
        // hack: set via init() stubbing
        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(r);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);


        ctx.init(false);
        assertNull(ctx.getFieldAnswer(f));
    }

    @Test
    void isColumnEnabled_defaultsToTrue_whenNoRuleApplied() {
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );
        CustomField f = mock(CustomField.class);
        when(f.getId()).thenReturn(77L);

        assertTrue(ctx.isColumnEnabled(f));
    }

    @Test
    void markFieldModified_marksAnswerAndSetsGlobalFlag() {
        // arrange
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomField field = mock(CustomField.class);
        CustomFieldAnswer ans = mock(CustomFieldAnswer.class);

        CustomFormResponse response = new CustomFormResponse();
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
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(new HashMap<>());

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);


        ctx.init(false);

        CustomField f = mock(CustomField.class);
        Concept c = mock(Concept.class);

        ctx.onConceptChanged(f, c);

        verify(enabledRulesEngine).onAnswerChange(eq(f), eq(c), any(), any());
    }

    @Test
    void flushBackToEntity_callsFormServiceUpdate() {
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(new HashMap<>());

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        ctx.flushBackToEntity();

        verify(formService).updateJpaEntityFromResponse(eq(response), eq(unit));
    }

    @Test
    void spatialTree_addAndRemove_marksModifiedAndUpdatesSelection() {
        // arrange
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomFieldAnswerSelectMultipleSpatialUnitTree treeAnswer = mock(CustomFieldAnswerSelectMultipleSpatialUnitTree.class);
        when(treeAnswer.getValue()).thenReturn(null); // force init set in addSUToSelection

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(new HashMap<>(Map.of(mock(CustomField.class), treeAnswer)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);
        when(spatialUnitTreeService.buildTree()).thenReturn(new DefaultTreeNode<>(null, null));

        ctx.init(false);

        SpatialUnit su1 = mock(SpatialUnit.class);

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
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        SpatialUnit a = mock(SpatialUnit.class);
        when(a.getId()).thenReturn(1L);
        SpatialUnit b = mock(SpatialUnit.class);
        when(b.getId()).thenReturn(2L);

        // parents of B include A; parents of A none
        when(spatialUnitService.findDirectParentsOf(2L)).thenReturn(List.of(a));
        when(spatialUnitService.findDirectParentsOf(1L)).thenReturn(Collections.emptyList());

        Set<SpatialUnit> selected = new LinkedHashSet<>(List.of(a, b));

        // act
        List<SpatialUnit> chips = ctx.getNormalizedSelectedUnits(selected);

        // assert: only A remains (B removed because ancestor A selected)
        assertEquals(1, chips.size());
        assertSame(a, chips.get(0));
    }


    @Test
    void getNormalizedSelectedUnits_sortsByName_caseInsensitive_nullsLast() {
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        SpatialUnit b = su(2L, "beta");
        SpatialUnit a = su(1L, "Alpha");
        SpatialUnit n = su(3L, null);

        when(spatialUnitService.findDirectParentsOf(anyLong())).thenReturn(Collections.emptyList());

        Set<SpatialUnit> selected = new LinkedHashSet<>(List.of(b, n, a));

        List<SpatialUnit> chips = ctx.getNormalizedSelectedUnits(selected);

        assertEquals(List.of(a, b, n), chips);
    }

    @Test
    void handleConceptChange_marksModified_reEvaluatesRules_andInvokesCallback_whenFormScopeField() {
        // arrange
        String scopeBinding = "scopeBinding";
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, scopeBinding
        );

        CustomField scopeField = mock(CustomField.class);
        when(scopeField.getIsSystemField()).thenReturn(true);
        when(scopeField.getValueBinding()).thenReturn(scopeBinding);
        CustomFieldAnswer ans = mock(CustomFieldAnswer.class);

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(new HashMap<>(Map.of(scopeField, ans)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);


        ctx.init(false);

        Concept newValue = mock(Concept.class);

        // act
        ctx.handleConceptChange(scopeField, newValue);

        // assert
        verify(ans).setHasBeenModified(true);
        assertTrue(ctx.isHasUnsavedModifications());
        verify(enabledRulesEngine).onAnswerChange(eq(scopeField), eq(newValue), any(), any());
        verify(scopeCallback).accept(eq(scopeField), eq(newValue));
    }

    @Test
    void handleConceptChange_doesNotInvokeCallback_whenNotFormScopeField() {
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                unit, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        CustomField otherField = mock(CustomField.class);
        when(otherField.getIsSystemField()).thenReturn(true);
        when(otherField.getValueBinding()).thenReturn("otherBinding");
        CustomFieldAnswer ans = mock(CustomFieldAnswer.class);

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(new HashMap<>(Map.of(otherField, ans)));

        when(formService.initOrReuseResponse(any(), any(), any(), anyBoolean())).thenReturn(response);
        when(formService.buildEnabledEngine(any())).thenReturn(enabledRulesEngine);

        ctx.init(false);

        Concept newValue = mock(Concept.class);

        ctx.handleConceptChange(otherField, newValue);

        verify(scopeCallback, never()).accept(any(), any());
    }

    @Test
    void getAutocompleteClass_returnsExpectedCssClass() {
        EntityFormContext<Object> ctx1 = new EntityFormContext<>(
                mock(RecordingUnit.class), fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("recording-unit-autocomplete", ctx1.getAutocompleteClass());

        EntityFormContext<Object> ctx2 = new EntityFormContext<>(
                mock(SpatialUnit.class), fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("spatial-unit-autocomplete", ctx2.getAutocompleteClass());

        EntityFormContext<Object> ctx3 = new EntityFormContext<>(
                new Object(), fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );
        assertEquals("", ctx3.getAutocompleteClass());
    }

    @Test
    void getSpatialUnitOptions_returnsEmpty_whenUnitNotRecordingUnit() {
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                new Object(), fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );
        assertTrue(ctx.getSpatialUnitOptions().isEmpty());
        verifyNoInteractions(spatialUnitService);
    }

    @Test
    void getSpatialUnitOptions_delegatesToService_whenUnitIsRecordingUnit() {
        RecordingUnit ru = mock(RecordingUnit.class);
        EntityFormContext<Object> ctx = new EntityFormContext<>(
                ru, fieldSource, formService, spatialUnitTreeService, spatialUnitService,
                scopeCallback, "scopeBinding"
        );

        List<SpatialUnit> opts = List.of(mock(SpatialUnit.class));
        when(spatialUnitService.getSpatialUnitOptionsFor(ru)).thenReturn(opts);

        assertSame(opts, ctx.getSpatialUnitOptions());
        verify(spatialUnitService).getSpatialUnitOptionsFor(ru);
    }
}
