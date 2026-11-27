package fr.siamois.utils;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAttachmentIdentifierNamerTest {

    @Test
    void buildPrefix_projectOnly() {
        ActionUnitDTO project = project("Fouille A", null);
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(project, List.of(), List.of()))
                .isEqualTo("Fouille-A");
    }

    @Test
    void buildPrefix_projectAndRecordingUnits() {
        ActionUnitDTO project = project("Site", null);
        RecordingUnitSummaryDTO ru1 = ru("UE-1");
        RecordingUnitSummaryDTO ru2 = ru("UE-2");
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(project, List.of(ru1), List.of()))
                .isEqualTo("Site_UE-1");
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(project, List.of(ru1, ru2), List.of()))
                .isEqualTo("Site_UE-1_UE-2");
    }

    @Test
    void buildPrefix_projectAndFind() {
        ActionUnitDTO project = project("Site", null);
        SpecimenSummaryDTO find = find("MOB-9");
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(project, List.of(), List.of(find)))
                .isEqualTo("Site_MOB-9");
    }

    @Test
    void buildPrefix_projectRuAndFind() {
        ActionUnitDTO project = project("Site", null);
        RecordingUnitSummaryDTO ru = ru("UE-1");
        SpecimenSummaryDTO find = find("MOB-9");
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(project, List.of(ru), List.of(find)))
                .isEqualTo("Site_UE-1_MOB-9");
    }

    @Test
    void buildPrefix_nullListsAndBlankProject_returnNullOrIgnoreNullEntries() {
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix((ActionUnitDTO) null, null, null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix("", List.of("UE"), null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix("Site", null, null)).isEqualTo("Site");
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(
                "Site", Arrays.asList(null, " ", "UE-1"), Arrays.asList(null, "MOB-1")))
                .isEqualTo("Site_UE-1_MOB-1");
    }

    @Test
    void buildPrefix_filtersNullDtoSegments() {
        ActionUnitDTO project = project("Site", null);
        assertThat(DocumentAttachmentIdentifierNamer.buildPrefix(
                project,
                Arrays.asList(null, ru(null), ru("UE-1")),
                Arrays.asList(null, find(null), find("MOB-1"))))
                .isEqualTo("Site_UE-1_MOB-1");
    }

    @Test
    void withCounter_padsToSixDigits() {
        assertThat(DocumentAttachmentIdentifierNamer.withCounter("Site_UE-1", 42))
                .isEqualTo("Site_UE-1_000042");
        assertThat(DocumentAttachmentIdentifierNamer.withCounter("Site", 1))
                .isEqualTo("Site_000001");
    }

    @Test
    void withCounter_rejectsBlankPrefixOrNonPositiveNumber() {
        assertThat(DocumentAttachmentIdentifierNamer.withCounter(null, 1)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.withCounter(" ", 1)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.withCounter("Site", 0)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.withCounter("Site", -3)).isNull();
    }

    @Test
    void parseCounter_readsTrailingSixDigitsForExactPrefix() {
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_000042")).isEqualTo(42);
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site_UE-1", "Site_UE-1_000007")).isEqualTo(7);
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_000001")).isEqualTo(1);
    }

    @Test
    void parseCounter_rejectsMismatchedOrMalformedIdentifiers() {
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter(null, "Site_000001")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Other_000001")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_UE_000001")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_42")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_00000A")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.parseCounter("Site", "Site_000000")).isNull();
    }

    @Test
    void maxCounter_takesHighestMatchingIdentifier() {
        assertThat(DocumentAttachmentIdentifierNamer.maxCounter("Site", List.of(
                "Site_000001", "Other_000099", "Site_000015", "Site_bad", "Site_000008")))
                .isEqualTo(15);
        assertThat(DocumentAttachmentIdentifierNamer.maxCounter("Site", List.of())).isZero();
        assertThat(DocumentAttachmentIdentifierNamer.maxCounter("Site", null)).isZero();
    }

    @Test
    void projectSegment_prefersNameThenFullIdentifierThenIdentifier() {
        ActionUnitDTO named = project("Nom", "FULL");
        assertThat(DocumentAttachmentIdentifierNamer.projectSegment(named)).isEqualTo("Nom");

        ActionUnitDTO coded = new ActionUnitDTO();
        coded.setFullIdentifier("P-1");
        assertThat(DocumentAttachmentIdentifierNamer.projectSegment(coded)).isEqualTo("P-1");

        ActionUnitDTO identified = new ActionUnitDTO();
        identified.setIdentifier("ID-9");
        assertThat(DocumentAttachmentIdentifierNamer.projectSegment(identified)).isEqualTo("ID-9");

        assertThat(DocumentAttachmentIdentifierNamer.projectSegment(null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.projectSegment(new ActionUnitDTO())).isNull();
    }

    @Test
    void recordingUnitSegment_prefersFullIdentifierThenIdentifier() {
        RecordingUnitSummaryDTO withFull = ru("UE-FULL");
        withFull.setIdentifier("UE-ID");
        assertThat(DocumentAttachmentIdentifierNamer.recordingUnitSegment(withFull)).isEqualTo("UE-FULL");

        RecordingUnitSummaryDTO idOnly = new RecordingUnitSummaryDTO();
        idOnly.setIdentifier("UE-ID");
        assertThat(DocumentAttachmentIdentifierNamer.recordingUnitSegment(idOnly)).isEqualTo("UE-ID");

        assertThat(DocumentAttachmentIdentifierNamer.recordingUnitSegment(null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.recordingUnitSegment(new RecordingUnitSummaryDTO())).isNull();
    }

    @Test
    void findSegment_usesFullIdentifier() {
        assertThat(DocumentAttachmentIdentifierNamer.findSegment(find("MOB-1"))).isEqualTo("MOB-1");
        assertThat(DocumentAttachmentIdentifierNamer.findSegment(null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.findSegment(new SpecimenSummaryDTO())).isNull();
    }

    @Test
    void sanitize_collapsesWhitespaceAndStripsUnsafeCharacters() {
        assertThat(DocumentAttachmentIdentifierNamer.sanitize("  Fouille  A*B!  ")).isEqualTo("Fouille-AB");
        assertThat(DocumentAttachmentIdentifierNamer.sanitize("___---")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.sanitize(null)).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.sanitize("   ")).isNull();
        assertThat(DocumentAttachmentIdentifierNamer.sanitize("OK_12-x")).isEqualTo("OK_12-x");
    }

    private static ActionUnitDTO project(String name, String fullIdentifier) {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        project.setName(name);
        project.setFullIdentifier(fullIdentifier);
        return project;
    }

    private static RecordingUnitSummaryDTO ru(String fullIdentifier) {
        RecordingUnitSummaryDTO ru = new RecordingUnitSummaryDTO();
        ru.setId(2L);
        ru.setFullIdentifier(fullIdentifier);
        return ru;
    }

    private static SpecimenSummaryDTO find(String fullIdentifier) {
        SpecimenSummaryDTO find = new SpecimenSummaryDTO();
        find.setId(3L);
        find.setFullIdentifier(fullIdentifier);
        return find;
    }
}
