package fr.siamois.domain.services.recordingunit.identifier;

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
@DisplayName("RuNumSpatialUnitResolver Unit Tests")
class RuNumSpatialUnitResolverTest {

    @InjectMocks
    private RuNumSpatialUnitResolver ruNumSpatialUnitResolver;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Test
    @DisplayName("getCode() should return 'NUM_USPATIAL'")
    void getCode_shouldReturnConstant() {
        assertThat(ruNumSpatialUnitResolver.getCode()).isEqualTo("NUM_USPATIAL");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(ruNumSpatialUnitResolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.num_uspatial");
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {

        @ParameterizedTest
        @CsvSource({
                "ID-{NUM_USPATIAL}-2024, true",
                "{NUM_USPATIAL:000}, true",
                "{NUM_UE}-{NUM_USPATIAL}, true",
                "ID-2024, false",
                "{TYPE_PARENT}, false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            // When
            boolean result = ruNumSpatialUnitResolver.formatUsesThisResolver(format);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolve() tests")
    class ResolveTest {

        @Test
        @DisplayName("should resolve with spatial unit number and padding when it exists")
        void resolve_shouldUseSpatialUnitNumber_whenItExists() {
            // Given
            String format = "ID-{NUM_USPATIAL:000}-END";
            when(ruInfo.getSpatialUnitNumber()).thenReturn(77);

            // When
            String result = ruNumSpatialUnitResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-077-END");
        }

        @Test
        @DisplayName("should resolve with 0 and padding when spatial unit number is null")
        void resolve_shouldUseZero_whenSpatialUnitNumberIsNull() {
            // Given
            String format = "ID-{NUM_USPATIAL:00}-END";
            when(ruInfo.getSpatialUnitNumber()).thenReturn(null);

            // When
            String result = ruNumSpatialUnitResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-00-END");
        }

        @Test
        @DisplayName("should resolve without padding when no format is specified")
        void resolve_shouldNotPad_whenNoFormatIsSpecified() {
            // Given
            String format = "ID-{NUM_USPATIAL}-END";
            when(ruInfo.getSpatialUnitNumber()).thenReturn(77);

            // When
            String result = ruNumSpatialUnitResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-77-END");
        }

        @Test
        @DisplayName("should return number as string if format does not contain the code")
        void resolve_shouldReturnNumberAsString_whenFormatDoesNotContainCode() {
            // Given
            String baseFormat = "ID-123";
            when(ruInfo.getSpatialUnitNumber()).thenReturn(77);

            // When
            String result = ruNumSpatialUnitResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("77");
        }

        @Test
        @DisplayName("should return 0 as string if format does not contain code and number is null")
        void resolve_shouldReturnZeroAsString_whenFormatDoesNotContainCodeAndNumberIsNull() {
            // Given
            String baseFormat = "ID-123";
            when(ruInfo.getSpatialUnitNumber()).thenReturn(null);

            // When
            String result = ruNumSpatialUnitResolver.resolve(baseFormat, ruInfo);

            // Then
            assertThat(result).isEqualTo("0");
        }
    }
}