package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
    }
}