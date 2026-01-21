package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdLabel;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuTypeParentResolver Unit Tests")
class RuTypeParentResolverTest {

    @Mock
    private LabelService labelService;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Mock
    private ActionUnit actionUnit;

    @Mock
    private Concept ruParentType;

    @Mock
    private RecordingUnitIdLabelRepository repository;

    @InjectMocks
    private RuTypeParentResolver ruTypeParentResolver;

    @Test
    @DisplayName("getCode() should return 'TYPE_PARENT'")
    void getCode_shouldReturnConstant() {
        assertThat(ruTypeParentResolver.getCode()).isEqualTo("TYPE_PARENT");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(ruTypeParentResolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.type_parent");
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {

        @ParameterizedTest
        @CsvSource({
                "ID-{TYPE_PARENT}-2024, true",
                "{TYPE_PARENT:XXXX}, true",
                "{NUM_UE}-{TYPE_PARENT}, true",
                "ID-2024, false",
                "{TYPE_UE}, false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            // When
            boolean result = ruTypeParentResolver.formatUsesThisResolver(format);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolve() tests")
    class ResolveTest {

        @BeforeEach
        void setUp() {
            // Lenient mocking because not all tests need these mocks
            lenient().when(ruInfo.getActionUnit()).thenReturn(actionUnit);
            lenient().when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn("fr");
            lenient().when(ruInfo.getRuParentType()).thenReturn(ruParentType);
        }

        private enum InvalidInputCase {
            NO_PLACEHOLDER, NULL_LANG, NULL_TYPE
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'should return base string when no placeholder', 'ID-123', NO_PLACEHOLDER, 'ID-123'",
                "'should return star when language is null', '{TYPE_PARENT}', NULL_LANG, '*'",
                "'should return star when parent type is null', '{TYPE_PARENT}', NULL_TYPE, '*'"
        })
        @DisplayName("should handle invalid inputs correctly")
        void resolve_shouldHandleInvalidInputs(String testName, String baseFormat, InvalidInputCase invalidCase, String expected) {
            // Given
            switch (invalidCase) {
                case NULL_LANG:
                    when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn(null);
                    break;
                case NULL_TYPE:
                    when(ruInfo.getRuParentType()).thenReturn(null);
                    break;
                case NO_PLACEHOLDER:
                    when(labelService.findLabelOf(any(Concept.class), eq("fr"))).thenReturn(new ConceptPrefLabel());
                    break;
            }

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'should use default 3 chars', 'ID-{TYPE_PARENT}-2024', 'Structure', 'ID-STR-2024'",
                "'should use specified length', 'ID-{TYPE_PARENT:XXXXX}-2024', 'Structure', 'ID-STRUC-2024'",
                "'should use default 3 chars for invalid format', 'ID-{TYPE_PARENT:XXX}-2024', 'Structure', 'ID-STR-2024'",
                "'should use full length if label is shorter', '{TYPE_PARENT:XXXXX}', 'Mur', 'MUR'"
        })
        @DisplayName("should produce correct output for valid inputs")
        void resolve_shouldProduceCorrectOutput(String testName, String baseFormat, String labelText, String expected) {
            // Given
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel(labelText);
            when(labelService.findLabelOf(ruParentType, "fr")).thenReturn(label);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Nested
        @DisplayName("addNumberIfNecessaryAndSaveExisting logic")
        class AddNumberIfNecessaryTest {
            private Concept type1, type2, type3;
            private ConceptLabel label;

            @BeforeEach
            void setup() {
                type1 = new Concept();
                type1.setId(1L);
                type1.setExternalId("type1");
                type2 = new Concept();
                type2.setId(2L);
                type2.setExternalId("type2");
                type3 = new Concept();
                type3.setId(3L);
                type3.setExternalId("type3");
                label = new ConceptPrefLabel();
                label.setLabel("Structure");
                label.setConcept(type1);
                // This mock is already in the parent setUp, but we make it explicit for clarity
                when(ruInfo.getActionUnit()).thenReturn(actionUnit);
                when(ruInfo.getRuType()).thenReturn(type3);
            }

            @Test
            @DisplayName("should save new label when it does not exist")
            void resolve_shouldSaveNewLabel() {
                // Given
                when(ruInfo.getRuParentType()).thenReturn(type1);
                when(labelService.findLabelOf(type1, "fr")).thenReturn(label);
                when(repository.findByExistingAndActionUnit(eq("STR"), any(ActionUnit.class))).thenReturn(Optional.empty());

                // When
                String result = ruTypeParentResolver.resolve("{TYPE_PARENT}", ruInfo);

                // Then
                assertThat(result).isEqualTo("STR");
            }

            @Test
            @DisplayName("should not save label if it exists for the same type")
            void resolve_shouldNotSaveExistingLabelForSameType() {
                // Given
                when(ruInfo.getRuParentType()).thenReturn(type1);
                when(labelService.findLabelOf(type1, "fr")).thenReturn(label);
                RecordingUnitIdLabel existingLabel = new RecordingUnitIdLabel(type2, actionUnit, "STR");
                when(repository.findByExistingAndActionUnit("STR", actionUnit)).thenReturn(Optional.of(existingLabel));

                // When
                String result = ruTypeParentResolver.resolve("{TYPE_PARENT}", ruInfo);

                // Then
                assertThat(result).isEqualTo("STR");
                verify(repository, never()).save(any());
            }

            @Test
            @DisplayName("should append number if label exists for a different type")
            void resolve_shouldAppendNumberForExistingLabelWithDifferentType() {
                // Given
                when(ruInfo.getRuParentType()).thenReturn(type1);
                when(labelService.findLabelOf(type1, "fr")).thenReturn(label);
                RecordingUnitIdLabel existingLabel = new RecordingUnitIdLabel(type2, actionUnit, "STR");
                when(repository.findByExistingAndActionUnit("STR", actionUnit)).thenReturn(Optional.of(existingLabel));
                when(repository.findByExistingAndActionUnit("STR1", actionUnit)).thenReturn(Optional.empty());

                // When
                String result = ruTypeParentResolver.resolve("{TYPE_PARENT}", ruInfo);

                // Then
                assertThat(result).isEqualTo("STR1");
            }

            @Test
            @DisplayName("should increment number if numbered label exists for a different type")
            void resolve_shouldIncrementNumberForExistingLabelWithDifferentType() {
                // Given
                when(ruInfo.getRuParentType()).thenReturn(type2);
                when(labelService.findLabelOf(type2, "fr")).thenReturn(label);
                RecordingUnitIdLabel existingLabelStr = new RecordingUnitIdLabel(type1, actionUnit, "STR");
                when(repository.findByExistingAndActionUnit("STR", actionUnit)).thenReturn(Optional.of(existingLabelStr));
                when(repository.findByExistingAndActionUnit("STR1", actionUnit)).thenReturn(Optional.of(new RecordingUnitIdLabel(type1, actionUnit, "STR1")));
                when(repository.findByExistingAndActionUnit("STR2", actionUnit)).thenReturn(Optional.empty());

                // When
                String result = ruTypeParentResolver.resolve("{TYPE_PARENT}", ruInfo);

                // Then
                assertThat(result).isEqualTo("STR2");
            }
        }
    }
}