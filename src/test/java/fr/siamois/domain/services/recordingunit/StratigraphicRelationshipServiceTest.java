package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StratigraphicRelationshipServiceTest {

    @Mock
    private StratigraphicRelationshipRepository relationshipRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private StratigraphicRelationshipService service;

    private RecordingUnit unit1, unit2;
    private Concept synchronous, asynchronous;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        unit1 = new RecordingUnit();
        unit1.setFullIdentifier("1");
        unit2 = new RecordingUnit();
        unit2.setFullIdentifier("2");

        synchronous = StratigraphicRelationshipService.SYNCHRONOUS;
        asynchronous = StratigraphicRelationshipService.ASYNCHRONOUS;


        service = new StratigraphicRelationshipService(relationshipRepository);

        // Manually inject the entityManager mock
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }



    @Test
    void testGetSynchronousUnits() {
        StratigraphicRelationship rel1 = new StratigraphicRelationship();
        rel1.setUnit1(unit1);
        rel1.setUnit2(unit2);
        rel1.setConcept(synchronous);

        StratigraphicRelationship rel2 = new StratigraphicRelationship();
        rel2.setUnit1(unit2);
        rel2.setUnit2(unit1);
        rel2.setConcept(synchronous);

        when(relationshipRepository.findByUnit1AndConcept(unit1, synchronous)).thenReturn(List.of(rel1));
        when(relationshipRepository.findByUnit2AndConcept(unit1, synchronous)).thenReturn(List.of(rel2));

        List<RecordingUnit> result = service.getSynchronousUnits(unit1);

        assertEquals(2, result.size());
        assertTrue(result.contains(unit2));
    }

    @Test
    void testGetAnteriorUnits() {
        StratigraphicRelationship rel = new StratigraphicRelationship();
        rel.setUnit1(unit1);
        rel.setUnit2(unit2);
        rel.setConcept(asynchronous);

        when(relationshipRepository.findByUnit2AndConcept(unit2, asynchronous)).thenReturn(List.of(rel));

        List<RecordingUnit> result = service.getAnteriorUnits(unit2);

        assertEquals(1, result.size());
        assertTrue(result.contains(unit1));
    }

    @Test
    void testGetPosteriorUnits() {
        StratigraphicRelationship rel = new StratigraphicRelationship();
        rel.setUnit1(unit1);
        rel.setUnit2(unit2);
        rel.setConcept(asynchronous);

        when(relationshipRepository.findByUnit1AndConcept(unit1, asynchronous)).thenReturn(List.of(rel));

        List<RecordingUnit> result = service.getPosteriorUnits(unit1);

        assertEquals(1, result.size());
        assertTrue(result.contains(unit2));
    }

    @Test
    void testSaveOrGet_WhenRelationshipExists() {
        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        existingRel.setUnit1(unit1);
        existingRel.setUnit2(unit2);
        existingRel.setConcept(synchronous);

        when(relationshipRepository.findByUnit1AndUnit2AndConcept(unit1, unit2, synchronous))
                .thenReturn(Optional.of(existingRel));
        when(relationshipRepository.save(existingRel)).thenReturn(existingRel);

        StratigraphicRelationship result = service.saveOrGet(unit1, unit2, synchronous);

        assertNotNull(result);
        assertEquals(unit1, result.getUnit1());
        assertEquals(unit2, result.getUnit2());
        verify(relationshipRepository, never()).delete(any());
        verify(entityManager, never()).flush();
    }

    @Test
    void testSaveOrGet_WhenReversedRelationshipExists() {
        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        existingRel.setUnit1(unit2);
        existingRel.setUnit2(unit1);
        existingRel.setConcept(synchronous);

        when(relationshipRepository.findByUnit1AndUnit2AndConcept(unit1, unit2, synchronous)).thenReturn(Optional.empty());
        when(relationshipRepository.findByUnit1AndUnit2AndConcept(unit2, unit1, synchronous))
                .thenReturn(Optional.of(existingRel));

        StratigraphicRelationship newRel = new StratigraphicRelationship();
        when(relationshipRepository.save(any())).thenReturn(newRel);

        StratigraphicRelationship result = service.saveOrGet(unit1, unit2, synchronous);

        assertNotNull(result);
        verify(relationshipRepository).delete(existingRel);
        verify(entityManager).flush();
        verify(relationshipRepository).save(any());
    }

    @Test
    void testSaveOrGet_WhenNoExistingRelationship() {
        when(relationshipRepository.findByUnit1AndUnit2AndConcept(unit1, unit2, synchronous)).thenReturn(Optional.empty());
        when(relationshipRepository.findByUnit1AndUnit2AndConcept(unit2, unit1, synchronous)).thenReturn(Optional.empty());

        StratigraphicRelationship newRel = new StratigraphicRelationship();
        when(relationshipRepository.save(any())).thenReturn(newRel);

        StratigraphicRelationship result = service.saveOrGet(unit1, unit2, synchronous);

        assertNotNull(result);
        verify(relationshipRepository).save(any());
    }
}
