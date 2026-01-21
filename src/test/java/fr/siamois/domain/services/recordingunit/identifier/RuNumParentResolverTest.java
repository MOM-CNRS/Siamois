package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuNumParentResolverTest {

    @Mock
    private RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;

    @InjectMocks
    private RuNumParentResolver ruNumParentResolver;

    @Mock
    private RecordingUnitIdInfo ruInfo;

    @Mock
    private RecordingUnit parentRecordingUnit;

    @Test
    @DisplayName("getCode() should return 'NUM_PARENT'")
    void getCode_shouldReturnConstant() {
        assertThat(ruNumParentResolver.getCode()).isEqualTo("NUM_PARENT");
    }

    @Test
    @DisplayName("getDescriptionLanguageCode() should return the correct message key")
    void getDescriptionLanguageCode_shouldReturnKey() {
        assertThat(ruNumParentResolver.getDescriptionLanguageCode()).isEqualTo("ru.identifier.description.number_parent");
    }

    @Test
    @DisplayName("getButtonStyleClass() should return the correct css class")
    void getButtonStyleClass_shouldReturnCssClass() {
        // When
        String styleClass = ruNumParentResolver.getButtonStyleClass();

        // Then
        assertThat(styleClass).isEqualTo("rounded-button ui-button-warning");
    }

    @Nested
    @DisplayName("formatUsesThisResolver() tests")
    class FormatUsesThisResolverTest {

        @ParameterizedTest
        @CsvSource({
                "ID-{NUM_PARENT}-2024, true",
                "{NUM_PARENT:000}, true",
                "{NUM_UE}-{NUM_PARENT}, true",
                "ID-2024, false",
                "{TYPE_PARENT}, false",
                "'', false"
        })
        @DisplayName("should correctly detect if format string contains the code")
        void formatUsesThisResolver_shouldDetectCode(String format, boolean expected) {
            // When
            boolean result = ruNumParentResolver.formatUsesThisResolver(format);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolve() tests")
    class ResolveTest {

        @Test
        @DisplayName("should resolve with parent number when parent exists and is found")
        void resolve_shouldUseParentNumber_whenParentFound() {
            // Given
            String format = "ID-{NUM_PARENT:000}-END";
            RecordingUnitIdInfo parentInfo = new RecordingUnitIdInfo();
            parentInfo.setRuNumber(42);

            when(ruInfo.getParent()).thenReturn(parentRecordingUnit);
            when(recordingUnitIdInfoRepository.findById(parentRecordingUnit.getId())).thenReturn(Optional.of(parentInfo));

            // When
            String result = ruNumParentResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-042-END");
        }

        @Test
        @DisplayName("should resolve with 0 when parent is null")
        void resolve_shouldUseZero_whenParentIsNull() {
            // Given
            String format = "ID-{NUM_PARENT:0000}-END";
            when(ruInfo.getParent()).thenReturn(null);

            // When
            String result = ruNumParentResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-0000-END");
        }

        @Test
        @DisplayName("should resolve with 0 when parent exists but is not found in repository")
        void resolve_shouldUseZero_whenParentNotFoundInRepo() {
            // Given
            String format = "ID-{NUM_PARENT}-END";
            when(ruInfo.getParent()).thenReturn(parentRecordingUnit);
            when(recordingUnitIdInfoRepository.findById(parentRecordingUnit.getId())).thenReturn(Optional.empty());

            // When
            String result = ruNumParentResolver.resolve(format, ruInfo);

            // Then
            assertThat(result).isEqualTo("ID-0-END");
        }
    }
}