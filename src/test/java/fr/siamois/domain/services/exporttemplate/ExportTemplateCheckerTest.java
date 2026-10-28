package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportTemplateCheckerTest {

    private static final FieldRef CONCEPT = new ConceptField(new ConceptRef("th230", "1", null));
    private static final Source RU = new EntitySource(EntityKind.RECORDING_UNIT, List.of());
    private static final Source STRAT = new TechnicalSource("STRATIGRAPHIC_RELATIONSHIP");

    private static ExportTemplateDefinition template(Rule rule, Source... sources) {
        return new ExportTemplateDefinition(3, "a", "1", "T", null, List.of(
                new Sheet("S", false, List.of(sources), List.of(),
                        List.of(new Column("c", OutputType.TEXT, List.of(rule))))));
    }

    private static Rule direct(FieldRef field, String... path) {
        return new DirectRule(List.of(), field, List.of(path), null);
    }

    @Test
    void validTemplate_hasNoProblem() {
        assertThat(ExportTemplateChecker.check(template(direct(CONCEPT, "project"), RU))).isEmpty();
    }

    @Test
    void constantRule_isAlwaysFine() {
        assertThat(ExportTemplateChecker.check(template(new ConstantRule(List.of(), "x"), STRAT))).isEmpty();
    }

    @Test
    void unknownNavigation_isReportedWithItsLocation() {
        assertThat(ExportTemplateChecker.check(template(direct(CONCEPT, "nope"), RU))).singleElement().asString()
                .startsWith("S / c : ").contains("nope");
    }

    @Test
    void unknownTechnicalSource_isReportedOnceAtTheSource() {
        List<String> problems = ExportTemplateChecker.check(template(direct(CONCEPT, "unit1"), new TechnicalSource("DROP")));

        assertThat(problems).singleElement().asString().contains("unknown technical source 'DROP'");
    }

    @Test
    void technicalRow_conceptFieldNeedsAnEndInThePath() {
        assertThat(ExportTemplateChecker.check(template(direct(CONCEPT), STRAT))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(direct(CONCEPT, "unit1"), STRAT))).isEmpty();
        assertThat(ExportTemplateChecker.check(template(direct(CONCEPT, "unit1", "project"), STRAT))).isEmpty();
    }

    @Test
    void columnField_isOnlyValidOnATechnicalSourceWithKnownColumnAndNoPath() {
        assertThat(ExportTemplateChecker.check(template(direct(new ColumnField("relationType")), STRAT))).isEmpty();
        assertThat(ExportTemplateChecker.check(template(direct(new ColumnField("zzz")), STRAT))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(direct(new ColumnField("relationType"), "unit1"), STRAT))).hasSize(1);
        assertThat(ExportTemplateChecker.check(template(direct(new ColumnField("relationType")), RU))).hasSize(1);
    }

    @Test
    void concatParts_areChecked_literalsAreNot() {
        Rule rule = new ConcatRule(List.of(), List.of(
                new ConcatPart(null, "x", null, List.of()),
                new ConcatPart(null, null, CONCEPT, List.of("nope"))), "", ": ");

        assertThat(ExportTemplateChecker.check(template(rule, RU))).hasSize(1);
    }

    @Test
    void ruleAppliesOnlyToItsSources() {
        ExportTemplateDefinition d = new ExportTemplateDefinition(3, "a", "1", "T", null, List.of(
                new Sheet("S", false, List.of(RU, STRAT), List.of(), List.of(new Column("c", OutputType.TEXT, List.of(
                        new DirectRule(List.of(0), CONCEPT, List.of("project"), null),
                        new DirectRule(List.of(1), CONCEPT, List.of("unit1", "project"), null)))))));

        assertThat(ExportTemplateChecker.check(d)).isEmpty();
    }

    @Test
    void commonCoreOnlyConstructs_areReportedAsUnsupported() {
        assertThat(ExportTemplateChecker.check(template(new ConstantRule(List.of(), "x"), new TableSource("Mobilier"))))
                .singleElement().asString().contains("table source 'Mobilier' is not supported");
        assertThat(ExportTemplateChecker.check(template(direct(new NameField("Num")), RU)))
                .singleElement().asString().contains("named field 'Num' is not supported");
    }
}
