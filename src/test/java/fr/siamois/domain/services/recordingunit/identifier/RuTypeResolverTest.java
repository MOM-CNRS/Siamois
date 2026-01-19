package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        @Test
        @DisplayName("should return empty string if format does not contain the code")
        void resolve_shouldReturnBaseString_whenFormatDoesNotContainCode() {
            // Given
            String baseFormat = "ID-123";

            when(labelService.findLabelOf(ruType, "fr")).thenReturn(new ConceptPrefLabel());

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should return empty string if language is null")
        void resolve_shouldReturnEmptyString_whenLangIsNull() {
            // Given
            String baseFormat = "{TYPE_UE}";
            when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn(null);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should return empty string if RU type is null")
        void resolve_shouldReturnEmptyString_whenRuTypeIsNull() {
            // Given
            String baseFormat = "{TYPE_UE}";
            when(ruInfo.getRuType()).thenReturn(null);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should resolve with default 3 characters when no format is specified")
        void resolve_shouldUseDefaultLength_whenNoFormatSpecified() {
            // Given
            String baseFormat = "ID-{TYPE_UE}-2024";
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STR-2024");
        }

        @Test
        @DisplayName("should resolve with specified number of characters from format")
        void resolve_shouldUseSpecifiedLength_whenFormatIsPresent() {
            // Given
            String baseFormat = "ID-{TYPE_UE:XXXXX}-2024"; // 5 chars
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STRUC-2024");
        }

        @Test
        @DisplayName("should resolve with default 3 characters for invalid format specifier")
        void resolve_shouldUseDefaultLength_whenFormatIsInvalid() {
            // Given
            String baseFormat = "ID-{TYPE_UE:000}-2024"; // Invalid format, expects 'X'
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);

            // When
            String result = ruTypeResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STR-2024");
        }

        @Test
        void resolve_shouldHaveFullLength_whenLabelIsShorterThanSpecified() {
            // Given
            String baseFormat = "{TYPE_UE:XXXXX}";
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Mur");
            when(labelService.findLabelOf(ruType, "fr")).thenReturn(label);

            assertEquals("MUR", ruTypeResolver.resolve(baseFormat, ruInfo));
        }
    }
}