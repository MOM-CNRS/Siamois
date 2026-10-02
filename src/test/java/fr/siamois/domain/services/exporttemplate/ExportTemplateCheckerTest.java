package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportTemplateCheckerTest {

    private static final String CONCEPT = "{\"concept\":{\"thesaurus\":\"th230\",\"id\":\"1\"}}";

    private static ExportTemplateDefinition template(String source, String rule) {
        return ExportTemplateJson.parse("{\"schemaVersion\":1,\"id\":\"a\",\"version\":\"1\",\"name\":\"T\",\"sheets\":[{"
                + "\"name\":\"S\",\"sources\":[" + source + "],\"columns\":[{\"header\":\"c\",\"rules\":[" + rule + "]}]}]}");
    }

    private static final String RU = "{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\"}";
    private static final String STRAT = "{\"kind\":\"TECHNICAL\",\"key\":\"STRATIGRAPHIC_RELATIONSHIP\"}";

    @Test
    void validTemplate_hasNoProblem() {
        ExportTemplateDefinition d = template(RU, "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + ",\"path\":[\"project\"]}");

        assertThat(ExportTemplateChecker.check(d)).isEmpty();
    }

    @Test
    void constantRule_isAlwaysFine() {
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"CONSTANT\",\"value\":\"x\"}"))).isEmpty();
    }

    @Test
    void unknownNavigation_isReportedWithItsLocation() {
        ExportTemplateDefinition d = template(RU, "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + ",\"path\":[\"nope\"]}");

        assertThat(ExportTemplateChecker.check(d)).singleElement().asString()
                .startsWith("S / c : ").contains("nope");
    }

    @Test
    void unknownTechnicalSource_isReportedOnceAtTheSource() {
        ExportTemplateDefinition d = template("{\"kind\":\"TECHNICAL\",\"key\":\"DROP\"}",
                "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + ",\"path\":[\"unit1\"]}");

        List<String> problems = ExportTemplateChecker.check(d);

        assertThat(problems).singleElement().asString().contains("unknown technical source 'DROP'");
    }

    @Test
    void technicalRow_conceptFieldNeedsAnEndInThePath() {
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + "}"))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + ",\"path\":[\"unit1\"]}"))).isEmpty();
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":" + CONCEPT + ",\"path\":[\"unit1\",\"project\"]}"))).isEmpty();
    }

    @Test
    void columnField_isOnlyValidOnATechnicalSourceWithKnownColumnAndNoPath() {
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":{\"column\":\"relationType\"}}"))).isEmpty();
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":{\"column\":\"zzz\"}}"))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(STRAT, "{\"type\":\"DIRECT\",\"field\":{\"column\":\"relationType\"},\"path\":[\"unit1\"]}"))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(RU, "{\"type\":\"DIRECT\",\"field\":{\"column\":\"relationType\"}}"))).hasSize(1);
    }

    @Test
    void concatParts_areChecked_literalsAreNot() {
        String rule = "{\"type\":\"CONCAT\",\"parts\":[{\"literal\":\"x\"},{\"field\":" + CONCEPT + ",\"path\":[\"nope\"]}]}";

        assertThat(ExportTemplateChecker.check(template(RU, rule))).hasSize(1);
    }

    @Test
    void ruleAppliesOnlyToItsSources() {
        ExportTemplateDefinition d = ExportTemplateJson.parse("{\"schemaVersion\":1,\"id\":\"a\",\"version\":\"1\",\"name\":\"T\",\"sheets\":[{"
                + "\"name\":\"S\",\"sources\":[" + RU + "," + STRAT + "],\"columns\":[{\"header\":\"c\",\"rules\":["
                + "{\"type\":\"DIRECT\",\"sources\":[0],\"field\":" + CONCEPT + ",\"path\":[\"project\"]},"
                + "{\"type\":\"DIRECT\",\"sources\":[1],\"field\":" + CONCEPT + ",\"path\":[\"unit1\",\"project\"]}]}]}]}");

        assertThat(ExportTemplateChecker.check(d)).isEmpty();
    }
}
