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

    @Test
    @DisplayName("getTitleCode() should return the correct message key")
    void getTitleCode_shouldReturnKey() {
        assertThat(resolver.getTitleCode()).isEqualTo("ru.identifier.title.id_ua");
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
        private enum ResolveCase {VALID, NULL_IDENTIFIER, NULL_ACTION_UNIT, NO_PLACEHOLDER}

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
                "'should resolve with full identifier', 'PREFIX-{ID_UA}-SUFFIX', 'ACTION-001', VALID, 'PREFIX-ACTION-001-SUFFIX'",
                "'should handle multiple placeholders', '{ID_UA}-{ID_UA}', 'ACTION-001', VALID, 'ACTION-001-ACTION-001'",
                "'should resolve with star when identifier is null', 'PREFIX-{ID_UA}-SUFFIX', '<null>', NULL_IDENTIFIER, 'PREFIX-*-SUFFIX'",
                "'should resolve with star when action unit is null', 'PREFIX-{ID_UA}-SUFFIX', '<null>', NULL_ACTION_UNIT, 'PREFIX-*-SUFFIX'",
                "'should return original string when no placeholder', 'PREFIX-NO_PLACEHOLDER-SUFFIX', '<null>', NO_PLACEHOLDER, 'PREFIX-NO_PLACEHOLDER-SUFFIX'"
        })
        @DisplayName("should work for all resolve cases")
        void resolve_shouldWorkForAllCases(String testName, String format, String identifier, ResolveCase testCase, String expected) {
            // Given
            switch (testCase) {
                case VALID:
                    when(ruInfo.getActionUnit()).thenReturn(actionUnit);
                    when(actionUnit.getFullIdentifier()).thenReturn(identifier);
                    break;
                case NULL_IDENTIFIER:
                    when(ruInfo.getActionUnit()).thenReturn(actionUnit);
                    when(actionUnit.getFullIdentifier()).thenReturn(null);
                    break;
                case NULL_ACTION_UNIT:
                    when(ruInfo.getActionUnit()).thenReturn(null);
                    break;
                case NO_PLACEHOLDER:
                    // No mock setup needed
                    break;
            }

            // When
            String result = resolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }
}