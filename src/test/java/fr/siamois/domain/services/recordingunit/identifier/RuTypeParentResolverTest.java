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

        @Test
        @DisplayName("should return base string if format does not contain the code")
        void resolve_shouldReturnBaseString_whenFormatDoesNotContainCode() {
            // Given
            String baseFormat = "ID-123";

            when(labelService.findLabelOf(any(Concept.class), eq("fr"))).thenReturn(new ConceptPrefLabel());

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should return empty string if language is null")
        void resolve_shouldReturnEmptyString_whenLangIsNull() {
            // Given
            String baseFormat = "{TYPE_PARENT}";
            when(actionUnit.getRecordingUnitIdentifierLang()).thenReturn(null);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should return base string if RU parent type is null")
        void resolve_shouldReturnBaseString_whenRuParentTypeIsNull() {
            // Given
            String baseFormat = "{TYPE_PARENT}";
            when(ruInfo.getRuParentType()).thenReturn(null);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo(baseFormat);
        }

        @Test
        @DisplayName("should resolve with default 3 characters when no format is specified")
        void resolve_shouldUseDefaultLength_whenNoFormatSpecified() {
            // Given
            String baseFormat = "ID-{TYPE_PARENT}-2024";
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruParentType, "fr")).thenReturn(label);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STR-2024");
        }

        @Test
        @DisplayName("should resolve with specified number of characters from format")
        void resolve_shouldUseSpecifiedLength_whenFormatIsPresent() {
            // Given
            String baseFormat = "ID-{TYPE_PARENT:XXXXX}-2024"; // 5 chars
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruParentType, "fr")).thenReturn(label);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STRUC-2024");
        }

        @Test
        @DisplayName("should resolve with default 3 characters for invalid format specifier")
        void resolve_shouldUseDefaultLength_whenFormatIsInvalid() {
            // Given
            String baseFormat = "ID-{TYPE_PARENT:XXX}-2024"; // Invalid format, expects 'X'
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Structure");
            when(labelService.findLabelOf(ruParentType, "fr")).thenReturn(label);

            // When
            String result = ruTypeParentResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-STR-2024");
        }

        @Test
        void resolve_shouldHaveFullLength_whenLabelIsShorterThanSpecified() {
            // Given
            String baseFormat = "{TYPE_PARENT:XXXXX}";
            ConceptLabel label = new ConceptPrefLabel();
            label.setLabel("Mur");
            when(labelService.findLabelOf(ruParentType, "fr")).thenReturn(label);

            assertEquals("MUR", ruTypeParentResolver.resolve(baseFormat, ruInfo));
        }
    }
}