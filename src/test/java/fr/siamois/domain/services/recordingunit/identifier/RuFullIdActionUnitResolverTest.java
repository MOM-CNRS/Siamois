package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuFullIdActionUnitResolver Unit Tests")
class RuFullIdActionUnitResolverTest {

    @InjectMocks
    private RuFullIdActionUnitResolver resolver;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Mock
    private ActionUnit actionUnit;

    @Test
    @DisplayName("getCode() should return 'ID_UA'")
    void getCode_shouldReturnConstant() {
        assertThat(resolver.getCode()).isEqualTo("ID_UA");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(resolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.id_ua");
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {

        @ParameterizedTest
        @CsvSource({
                "ID-{ID_UA}-2024, true",
                "{NUM_UE}-{ID_UA}, true",
                "ID-2024, false",
                "{TYPE_PARENT}, false",
                "ID_UA, false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            // When
            boolean result = resolver.formatUsesThisResolver(format);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolve() tests")
    class ResolveTest {

        @Test
        @DisplayName("should resolve placeholder with full identifier when it exists")
        void resolve_shouldReplacePlaceholderWithFullIdentifier() {
            // Given
            String format = "PREFIX-{ID_UA}-SUFFIX";
            when(ruInfo.getActionUnit()).thenReturn(actionUnit);
            when(actionUnit.getFullIdentifier()).thenReturn("ACTION-001");

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("PREFIX-ACTION-001-SUFFIX");
        }

        @Test
        @DisplayName("should resolve with star when action unit's full identifier is null")
        void resolve_shouldUseEmptyString_whenFullIdentifierIsNull() {
            // Given
            String format = "PREFIX-{ID_UA}-SUFFIX";
            when(ruInfo.getActionUnit()).thenReturn(actionUnit);
            when(actionUnit.getFullIdentifier()).thenReturn(null);

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("PREFIX-*-SUFFIX");
        }

        @Test
        @DisplayName("should resolve with star when action unit is null")
        void resolve_shouldUseEmptyString_whenActionUnitIsNull() {
            // Given
            String format = "PREFIX-{ID_UA}-SUFFIX";
            when(ruInfo.getActionUnit()).thenReturn(null);

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("PREFIX-*-SUFFIX");
        }

        @Test
        @DisplayName("should return original string when no placeholder is present")
        void resolve_shouldReturnOriginalString_whenNoPlaceholder() {
            // Given
            String format = "PREFIX-NO_PLACEHOLDER-SUFFIX";

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("PREFIX-NO_PLACEHOLDER-SUFFIX");
        }

        @Test
        @DisplayName("should handle multiple placeholders")
        void resolve_shouldHandleMultiplePlaceholders() {
            // Given
            String format = "{ID_UA}-{ID_UA}";
            when(ruInfo.getActionUnit()).thenReturn(actionUnit);
            when(actionUnit.getFullIdentifier()).thenReturn("ACTION-001");

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ACTION-001-ACTION-001");
        }
    }
}