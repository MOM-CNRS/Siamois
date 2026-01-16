package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RuNumberResolverTest {

    @InjectMocks
    private RuNumberResolver ruNumberResolver;

    private RecordingUnitIdInfo ruInfo;

    @BeforeEach
    void setUp() {
        ruInfo = new RecordingUnitIdInfo();
        ruInfo.setRuNumber(12);
    }

    @Test
    void testGetCode() {
        assertEquals("NUM_UE", ruNumberResolver.getCode());
    }

    @Test
    void testformatUsesThisResolver_valid() {
        assertTrue(ruNumberResolver.formatUsesThisResolver("{NUM_UE}-Test"));
    }

    @Test
    void testformatUsesThisResolver_valid_withDigits() {
        assertTrue(ruNumberResolver.formatUsesThisResolver("{NUM_UE:000}-Test"));
    }

    @Test
    void testformatUsesThisResolver_invalid_withDigits() {
        assertFalse(ruNumberResolver.formatUsesThisResolver("{NUM_UA:000}-Test"));
    }

    @Test
    void testformatUsesThisResolver_invalid() {
        assertFalse(ruNumberResolver.formatUsesThisResolver("{NUM_UA}-Test"));
    }

    @Test
    void testGetDescriptionLanguageCode() {
        assertEquals("ru.identifier.description.number", ruNumberResolver.getDescriptionLanguageCode());
    }

    @Test
    void testResolve_simpleReplacement() {
        String format = "Test-{NUM_UE}-Test";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("Test-12-Test", result);
    }

    @Test
    void testResolve_withZeroPadding() {
        String format = "Test-{NUM_UE:0000}-Test";
        ruInfo = new RecordingUnitIdInfo();
        ruInfo.setRuNumber(42);
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("Test-0042-Test", result);
    }

    @Test
    void testResolve_withZeroPadding_numberTooLarge() {
        String format = "Test-{NUM_UE:00}-Test";
        ruInfo = new RecordingUnitIdInfo();
        ruInfo.setRuNumber(123);
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("Test-123-Test", result);
    }

    @Test
    void testResolve_noPlaceholder() {
        String format = "Test-NoPlaceholder-Test";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("12", result);
    }

    @Test
    void testResolve_emptyFormat() {
        String format = "";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("12", result);
    }

    @Test
    void testResolve_placeholderAtStart() {
        String format = "{NUM_UE}-Test";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("12-Test", result);
    }

    @Test
    void testResolve_placeholderAtEnd() {
        String format = "Test-{NUM_UE}";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("Test-12", result);
    }

    @Test
    void testResolve_multiplePlaceholders() {
        String format = "{NUM_UE}-{NUM_UE}";
        String result = ruNumberResolver.resolve(format, ruInfo);
        assertEquals("12-12", result);
    }

    @Test
    void testDescriptionIsNotNull() {
        assertNotNull(ruNumberResolver.getDescriptionLanguageCode());
    }
}