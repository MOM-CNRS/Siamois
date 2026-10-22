package fr.siamois.ui.bean.converter;

import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThesaurusInfoConverterTest {

    private final ThesaurusInfoConverter converter = new ThesaurusInfoConverter();

    @Test
    void getAsObject_shouldReadBackWhatGetAsStringWrote() {
        ThesaurusInfo info = new ThesaurusInfo("https://thesaurus.example.org", "th1", "Vocabulaire", "fr");

        ThesaurusInfo read = converter.getAsObject(null, null, converter.getAsString(null, null, info));

        assertThat(read).isEqualTo(info);
    }

    @Test
    void getAsObject_shouldKeepACommaOfTheLabel() {
        ThesaurusInfo info = new ThesaurusInfo("https://thesaurus.example.org", "th1", "Mobilier, céramique, verre", "fr");

        ThesaurusInfo read = converter.getAsObject(null, null, converter.getAsString(null, null, info));

        assertThat(read.label()).isEqualTo("Mobilier, céramique, verre");
        assertThat(read.langLabel()).isEqualTo("fr");
        assertThat(read.idTheso()).isEqualTo("th1");
    }

    @Test
    void getAsObject_shouldAnswerAnEmptyInfo_forAMalformedValue() {
        ThesaurusInfo empty = new ThesaurusInfo("", "", "", "");

        assertThat(converter.getAsObject(null, null, null)).isEqualTo(empty);
        assertThat(converter.getAsObject(null, null, "()")).isEqualTo(empty);
        assertThat(converter.getAsObject(null, null, "(only,two)")).isEqualTo(empty);
    }
}
