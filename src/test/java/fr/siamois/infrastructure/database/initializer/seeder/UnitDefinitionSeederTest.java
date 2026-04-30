package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitDefinitionSeederTest {

    @Mock
    private ConceptSeeder conceptSeeder;
    @Mock
    private UnitDefinitionRepository unitDefinitionRepository;
    @Mock
    private UnitDefinitionMapper mapper;

    @InjectMocks
    private UnitDefinitionSeeder unitDefinitionSeeder;

    private Vocabulary vocabulary;
    private UnitDefinitionDTO spec;
    private Concept concept;

    @BeforeEach
    void setUp() {
        vocabulary = new Vocabulary();

        // Setup DTO Hierarchy
        VocabularyDTO vocabDto = new VocabularyDTO();
        vocabDto.setExternalVocabularyId("VOCAB_1");

        ConceptDTO conceptDto = new ConceptDTO();
        conceptDto.setExternalId("CONCEPT_1");
        conceptDto.setVocabulary(vocabDto);

        spec = new UnitDefinitionDTO();
        spec.setConcept(conceptDto);
        spec.setLabel("Kilogram");

        concept = new Concept();
    }

    @Test
    void seed_ShouldCreateConceptAndSaveUnit_WhenNeitherExists() {
        // Arrange
        // 1. First check: Concept is missing
        when(conceptSeeder.findConceptOrReturnNull("VOCAB_1", "CONCEPT_1"))
                .thenReturn(null)    // First call in loop
                .thenReturn(concept); // Second call after seeding

        // 2. Mock Mapper and Repository check
        UnitDefinition entity = new UnitDefinition();
        when(mapper.invertConvert(spec)).thenReturn(entity);
        when(unitDefinitionRepository.findByConcept(concept)).thenReturn(Optional.empty());

        // Act
        unitDefinitionSeeder.seed(vocabulary, List.of(spec));

        // Assert
        verify(conceptSeeder).seed(eq(vocabulary), anyList());
        verify(unitDefinitionRepository).save(entity);
    }

    @Test
    void seed_ShouldNotSaveUnit_WhenUnitAlreadyExists() {
        // Arrange
        when(conceptSeeder.findConceptOrReturnNull("VOCAB_1", "CONCEPT_1")).thenReturn(concept);

        UnitDefinition entity = new UnitDefinition();
        when(mapper.invertConvert(spec)).thenReturn(entity);

        // Unit is found in DB
        when(unitDefinitionRepository.findByConcept(concept)).thenReturn(Optional.of(new UnitDefinition()));

        // Act
        unitDefinitionSeeder.seed(vocabulary, List.of(spec));

        // Assert
        verify(conceptSeeder, never()).seed(any(), any());
        verify(unitDefinitionRepository, never()).save(any());
    }

    @Test
    void findUnitOrReturnNull_ShouldReturnEntity_WhenFound() {
        // Arrange
        UnitDefinition unit = new UnitDefinition();
        when(unitDefinitionRepository.findByConcept(concept)).thenReturn(Optional.of(unit));

        // Act
        UnitDefinition result = unitDefinitionSeeder.findUnitOrReturnNull(concept);

        // Assert
        assert result != null;
        verify(unitDefinitionRepository).findByConcept(concept);
    }
}