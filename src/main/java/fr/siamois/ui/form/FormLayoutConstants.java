package fr.siamois.ui.form;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Constants the system form definitions ({@code *Form} classes of the domain) are built with: the
 * responsive column classes of their layout (PrimeFaces' own grid classes, which the React fiche
 * reproduces — see {@code main-panel.css}) and the system thesaurus their vocabulary fields point to.
 */
public final class FormLayoutConstants {

    /** A regular field column: full width on small screens, half on medium, a quarter on large. */
    public static final String COLUMN_CLASS_NAME = "ui-g-12 ui-md-6 ui-lg-3";
    /** A full-width field column (long texts). */
    public static final String LONG_COLUMN_CLASS_NAME = "ui-g-12 ui-md-12 ui-lg-12";

    public static final VocabularyType THESO_VOCABULARY_TYPE;
    public static final Vocabulary SYSTEM_THESO;

    static {
        THESO_VOCABULARY_TYPE = new VocabularyType();
        THESO_VOCABULARY_TYPE.setLabel("Thesaurus");
        SYSTEM_THESO = new Vocabulary();
        SYSTEM_THESO.setBaseUri("https://thesaurus.mom.fr");
        SYSTEM_THESO.setExternalVocabularyId("th230");
        SYSTEM_THESO.setType(THESO_VOCABULARY_TYPE);
    }

    private FormLayoutConstants() {
        throw new UnsupportedOperationException();
    }

    /** Default identifier of a new project: the current year. */
    public static String generateRandomActionUnitIdentifier() {
        return String.valueOf(LocalDate.now(ZoneOffset.UTC).getYear());
    }
}
