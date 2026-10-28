package fr.siamois.domain.services.exporttemplate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportTemplateNextVersionTest {

    @Test
    void incrementsTheLastNumber() {
        assertEquals("1.0.1", ExportTemplateService.nextVersion("1.0.0"));
        assertEquals("v3", ExportTemplateService.nextVersion("v2"));
        assertEquals("2.0.10", ExportTemplateService.nextVersion("2.0.9"));
    }

    @Test
    void appendsASuffixWhenThereIsNoTrailingNumber() {
        assertEquals("beta.1", ExportTemplateService.nextVersion("beta"));
        assertEquals("1.0.0", ExportTemplateService.nextVersion(""));
    }
}
