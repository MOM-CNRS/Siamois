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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuTypeResolver Unit Tests")
class RuTypeResolverTest {

    @Mock
    private LabelService labelService;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Mock
    private ActionUnit actionUnit;

    @Mock
    private Concept ruType;

    @Mock
    private RecordingUnitIdLabelRepository repository;

    @InjectMocks
    private RuTypeResolver ruTypeResolver;

    @Test
    @DisplayName("getCode() should return 'TYPE_UE'")
    void getCode_shouldReturnConstant() {
        assertThat(ruTypeResolver.getCode()).isEqualTo("TYPE_UE");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(ruTypeResolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.type");
    }

    @Test
    @DisplayName("getButtonStyleClass() should return the correct css class")
    void getButtonStyleClass_shouldReturnCssClass() {
        // When
        String styleClass = ruTypeResolver.getButtonStyleClass();

        // Then
        assertThat(styleClass).isEqualTo("rounded-button ui-button-warning");
    }

    @Test
    @DisplayName("getAssociatedConcept() should return ruType from info")
    void getAssociatedConcept_shouldReturnRuTypeFromInfo() {
        // Given
        when(ruInfo.getRuType()).thenReturn(ruType);

        // When
        Concept result = ruTypeResolver.getAssociatedConcept(ruInfo);

        // Then
        assertThat(result).isSameAs(ruType);
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {

        @ParameterizedTest
        @CsvSource({
                "ID-{TYPE_UE}-2024, true",
                "{TYPE_UE:XXXX}, true",
                "{NUM_UE}-{TYPE_UE}, true",
                "ID-2024, false",
                "{TYPE_PARENT}, false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            // When
            boolean result = ruTypeResolver.formatUsesThisResolver(format);

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
            lenient().when(ruInfo.getRuType()).thenReturn(ruType);
        }

        private enum InvalidInputCase {
            NO_PLACEHOLDER, NULL_LANG, NULL_TYPE
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'should return base string when format does not contain code', 'ID-123', NO_PLACEHOLDER",
                "'should return base string when language is null', '{TYPE_UE}', NULL_LANG",
                "'should return base string when RU type is null', '{TYPE_UE}', NULL_TYPE"
        })
        @DisplayName("should return base string for invalid inputs")
        void resolve_shouldReturnBaseString_forInvalidInputs(String testName, String baseFormat, InvalidInputCase invalidCase) {
            // Given
            switch (invalidCase) {
                case NULL_LANG:
                    when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn(null);
                    break;
                case NULL_TYPE:
                    when(ruInfo.getRuType()).thenReturn(null);
                    break;
                case NO_PLACEHOLDER:
                    // This mock is kept for consistency with the original test, even if it might not be called.
                    when(labelService.findLabelOf(ruType, "fr")).thenReturn(new ConceptPrefLabel());
                    break;
            }

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'should resolve with default 3 characters when no format is specified', 'ID-{TYPE_UE}-2024', 'Structure', 'ID-STR-2024'",
                "'should resolve with specified number of characters from format', 'ID-{TYPE_UE:XXXXX}-2024', 'Structure', 'ID-STRUC-2024'",
                "'should resolve with default 3 characters for invalid format specifier', 'ID-{TYPE_UE:000}-2024', 'Structure', 'ID-STR-2024'",
                "'should resolve with full length when label is shorter than specified', '{TYPE_UE:XXXXX}', 'Mur', 'MUR'"
        })
        @DisplayName("should produce correct output for valid inputs")
        void resolve_shouldProduceCorrectOutput_forValidInputs(String testName, String baseFormat, String labelText, String expected) {
            // Given
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel(labelText);
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("textValue() tests")
    class TextValueTest {

        @BeforeEach
        void setUp() {
            lenient().when(ruInfo.getActionUnit()).thenReturn(actionUnit);
            lenient().when(ruInfo.getRuType()).thenReturn(ruType);
            lenient().when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn("fr");
        }

        @Test
        @DisplayName("should return empty string when ruType is null")
        void textValue_shouldReturnEmptyString_whenRuTypeIsNull() {
            when(ruInfo.getRuType()).thenReturn(null);
            String result = ruTypeResolver.textValue(ruInfo);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when actionUnit is null")
        void textValue_shouldReturnEmptyString_whenActionUnitIsNull() {
            when(ruInfo.getActionUnit()).thenReturn(null);
            String result = ruTypeResolver.textValue(ruInfo);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when identifier lang is null")
        void textValue_shouldReturnEmptyString_whenIdentifierLangIsNull() {
            when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn(null);
            String result = ruTypeResolver.textValue(ruInfo);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return label when inputs are valid")
        void textValue_shouldReturnLabel_whenInputsAreValid() {
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("MyLabel");
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);
            String result = ruTypeResolver.textValue(ruInfo);
            assertThat(result).isEqualTo("MyLabel");
        }
    }
}