package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.recordingunit.MaxRecordingUnitIdentifierReached;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierFormatServiceTest {

    @InjectMocks
    IdentifierFormatService service;

    @Mock
    ActionUnitRepository actionUnitRepository;

    @Mock
    LabelService labelService;

    @Mock
    RecordingUnitRepository recordingUnitRepository;

    private ActionUnit actionUnit;
    private RecordingUnit recordingUnit;
    private RecordingUnit recordingUnitParent;

    @BeforeEach
    void setUp() {
        actionUnit = new ActionUnit();
        actionUnit.setId(12L);
        actionUnit.setIdentifier("UA-1");
        actionUnit.setMinRecordingUnitCode(1);
        actionUnit.setMaxRecordingUnitCode(100);
        actionUnit.setType(new Concept());

        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(123L);
        actionUnit.setSpatialContext(Set.of(spatialUnit));

        recordingUnit = new RecordingUnit();
        recordingUnit.setId(13L);
        recordingUnit.setActionUnit(actionUnit);
        recordingUnit.setType(new Concept());
        recordingUnit.setLocalIdentifierLang("en");

        recordingUnit.getType().setId(15L);
        recordingUnit.getType().setExternalId("1212");

        recordingUnitParent = new RecordingUnit();
        recordingUnitParent.setLocalIdentifierCode(42);
    }

    @Test
    void generateIdentifier_shouldThrowException_whenNextValueIsAboveMax() {
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(101);

        assertThrows(MaxRecordingUnitIdentifierReached.class, () -> service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldThrowException_whenNextValueIsBelowMin() {
        actionUnit.setMinRecordingUnitCode(10);
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(9);

        assertThrows(MaxRecordingUnitIdentifierReached.class, () -> service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldThrowException_whenFormatIsMissingNumUe() {
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);

        assertThrows(IllegalStateException.class, () -> service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldReturnNumber_whenFormatIsNullOrEmpty() {
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);

        actionUnit.setRecordingUnitIdentifierFormat(null);
        assertEquals("10", service.generateIdentifier(recordingUnit));

        actionUnit.setRecordingUnitIdentifierFormat("");
        assertEquals("10", service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldFormatWithOnlyNumUe() {
        actionUnit.setRecordingUnitIdentifierFormat("{NUM_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);

        String identifier = service.generateIdentifier(recordingUnit);
        assertEquals("0010", identifier);
    }

    @Test
    void generateIdentifier_shouldFormatWithAllPlaceholdersAndParent() {
        actionUnit.setRecordingUnitIdentifierFormat("{ID_UA}-{TYPE_PARENT}-{NUM_PARENT}-{TYPE_UE}-{NUM_UE}-{NUM_USPATIAL}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(labelService.findLabelOf(eq(recordingUnit.getType()), eq("en"))).thenReturn(new ConceptPrefLabel("RecordingType", "en"));
        when(labelService.findLabelOf(eq(actionUnit.getType()), eq("en"))).thenReturn(new ConceptPrefLabel("ParentType", "en"));

        String identifier = service.generateIdentifier(recordingUnit, recordingUnitParent);

        assertEquals("UA-1-PAR-0042-REC-0010-0123", identifier);
    }

    @Test
    void generateIdentifier_shouldFormatWithAllPlaceholdersAndNoParent() {
        actionUnit.setRecordingUnitIdentifierFormat("{ID_UA}-{TYPE_PARENT}-{NUM_PARENT}-{TYPE_UE}-{NUM_UE}-{NUM_USPATIAL}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(labelService.findLabelOf(eq(recordingUnit.getType()), eq("en"))).thenReturn(new ConceptPrefLabel("RecordingType", "en"));
        when(recordingUnitRepository.findDirectParentsOf(recordingUnit.getId())).thenReturn(Collections.emptyList());

        String identifier = service.generateIdentifier(recordingUnit);

        assertEquals("UA-1--0000-REC-0010-0123", identifier);
    }

    @Test
    void generateIdentifier_shouldFindParent_whenParentIsNull() {
        actionUnit.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(recordingUnitRepository.findDirectParentsOf(recordingUnit.getId())).thenReturn(List.of(recordingUnitParent));

        String identifier = service.generateIdentifier(recordingUnit, null);

        assertEquals("0042-0010", identifier);
    }

    @Test
    void generateIdentifier_shouldThrowException_whenLabelIsTooShort() {
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(new ConceptPrefLabel("AB", "en"));

        assertThrows(StringIndexOutOfBoundsException.class, () -> service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldThrowException_whenLabelStartsWithBracketAndIsEmpty() {
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(new ConceptPrefLabel("[Unlabeled]", "en"));

        assertThrows(StringIndexOutOfBoundsException.class, () -> service.generateIdentifier(recordingUnit));
    }

    @Test
    void generateIdentifier_shouldUseDefaultLang_whenLangIsNull() {
        recordingUnit.setLocalIdentifierLang(null);
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        when(actionUnitRepository.incrementRecordingUnitCodeNextValue(any())).thenReturn(10);
        when(labelService.findLabelOf(eq(recordingUnit.getType()), eq("en"))).thenReturn(new ConceptPrefLabel("TestLabel", "en"));

        String identifier = service.generateIdentifier(recordingUnit);
        assertEquals("TES-0010", identifier);
    }
}
