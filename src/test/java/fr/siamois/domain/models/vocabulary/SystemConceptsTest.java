package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemConceptsTest {

    @Test
    void aliasedBindingsGetTheThesaurusConcept() {
        assertEquals(SystemConcepts.id("SIAD.name"), SystemConcepts.id("SIAD", "title", "document.title"));
        assertEquals(SystemConcepts.id("SIAD.TYPE"), SystemConcepts.id("SIAD", "type", "document.type"));
        assertEquals(SystemConcepts.id("SIAD.specimens"), SystemConcepts.id("SIAD", "finds", "document.finds"));
        assertEquals(SystemConcepts.id("SIAST.TYPE"), SystemConcepts.id("SIAST", "type", "study.type"));
    }

    @Test
    void fieldsAbsentFromTheThesaurusKeepTheirSymbolicId() {
        assertEquals("document.identifier", SystemConcepts.id("SIAD", "identifier", "document.identifier"));
    }

    @Test
    void legacyIdsAreUpgradedPerTable() {
        assertEquals(SystemConcepts.id("SIAAU.name"), SystemConcepts.upgrade("SIAAU", "4285848"));
        assertEquals(SystemConcepts.id("SIASU.name"), SystemConcepts.upgrade("SIASU", "4285848"));
        assertNull(SystemConcepts.upgrade("SIAAU", "unknown"));
    }

    @Test
    void documentSystemFieldsCarryTheSystemThesaurusConcepts() {
        var fields = new ExportFieldResolver(null, null).systemFields(ExportSubject.DOCUMENT);
        CustomField title = fields.stream().filter(f -> "title".equals(f.getValueBinding())).findFirst().orElseThrow();
        assertEquals(SystemConcepts.id("SIAD.name"), title.getConcept().getExternalId());
        assertEquals("th337", title.getConcept().getVocabulary().getExternalVocabularyId());
        assertTrue(fields.stream().allMatch(f -> f.getConcept() == null
                || "th337".equals(f.getConcept().getVocabulary().getExternalVocabularyId())));
    }
}
