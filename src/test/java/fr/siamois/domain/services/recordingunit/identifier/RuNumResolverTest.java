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
@DisplayName("RuNumResolver Unit Tests")
class RuNumResolverTest {

    @InjectMocks
    private RuNumResolver ruNumResolver;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Test
    @DisplayName("getCode() should return 'NUM_UE'")
    void getCode_shouldReturnConstant() {
        assertThat(ruNumResolver.getCode()).isEqualTo("NUM_UE");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(ruNumResolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.number");
    }

    @Test
    @DisplayName("getButtonStyleClass() should return the correct css class")
    void getButtonStyleClass_shouldReturnCssClass() {
        // When
        String styleClass = ruNumResolver.getButtonStyleClass();

        // Then
        assertThat(styleClass).isEqualTo("rounded-button recording-unit");
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {
        @ParameterizedTest
        @CsvSource({
                "'{NUM_UE}-Test', true",
                "'{NUM_UE:000}-Test', true",
                "'{NUM_UA:000}-Test', false",
                "'{NUM_UA}-Test', false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            assertThat(ruNumResolver.formatUsesThisResolver(format)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolve() tests")
    class ResolveTest {

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'simple replacement', 'Test-{NUM_UE}-Test', 12, 'Test-12-Test'",
                "'zero padding', 'Test-{NUM_UE:0000}-Test', 42, 'Test-0042-Test'",
                "'zero padding with larger number', 'Test-{NUM_UE:00}-Test', 123, 'Test-123-Test'",
                "'placeholder at start', '{NUM_UE}-Test', 12, '12-Test'",
                "'placeholder at end', 'Test-{NUM_UE}', 12, 'Test-12'",
                "'multiple placeholders', '{NUM_UE}-{NUM_UE}', 12, '12-12'",
                "'no placeholder', 'Test-NoPlaceholder-Test', 12, '12'",
                "'empty format', '', 12, '12'"
        })
        @DisplayName("should replace placeholder correctly")
        void resolve_shouldReplacePlaceholder(String name, String format, int number, String expected) {
            when(ruInfo.getRuNumber()).thenReturn(number);
            String result = ruNumResolver.resolve(format, ruInfo);
            assertThat(result).isEqualTo(expected);
        }
    }
}