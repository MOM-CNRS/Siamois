package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.dto.FilterDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingUnitListFilterTest {

    private static MultiValueMap<String, String> params(String... keyValuePairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.add(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    @Test
    void parse_ignoresParamsWithoutTheFPrefix() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params("offset", "0", "limit", "10", "sort", "creationTime:desc"));
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void parse_bareKey_isATextContainsFilter() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params("f.fullIdentifier", "UE42"));
        assertThat(filter.containsFilters()).containsEntry("fullIdentifier", "UE42");
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void parse_blankContainsValue_isDropped() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params("f.fullIdentifier", "  "));
        assertThat(filter.containsFilters()).isEmpty();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void parse_repeatedKey_isOrSemanticsForIdList() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.type", "12");
        params.add("f.type", "44");

        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params);

        assertThat(filter.idListFilters()).containsEntry("type", List.of(12L, 44L));
    }

    @Test
    void parse_everyIdListField_parsesTheSameWay() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.author", "1");
        params.add("f.contributors", "2");
        params.add("f.spatialUnit", "3");
        params.add("f.parents", "4");
        params.add("f.children", "5");
        params.add("f.geomorphologicalCycle", "6");
        params.add("f.geomorphologicalAgent", "7");
        params.add("f.normalizedInterpretation", "8");

        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params);

        assertThat(filter.idListFilters())
                .containsEntry("author", List.of(1L))
                .containsEntry("contributors", List.of(2L))
                .containsEntry("spatialUnit", List.of(3L))
                .containsEntry("parents", List.of(4L))
                .containsEntry("children", List.of(5L))
                .containsEntry("geomorphologicalCycle", List.of(6L))
                .containsEntry("geomorphologicalAgent", List.of(7L))
                .containsEntry("normalizedInterpretation", List.of(8L));
    }

    @Test
    void parse_matrixColor_isATextContainsFilterToo() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params("f.matrixColor", "brun"));
        assertThat(filter.containsFilters()).containsEntry("matrixColor", "brun");
    }

    @Test
    void parse_dateRange_bothBoundsAndEachBoundAlone() {
        RecordingUnitListFilter both = RecordingUnitListFilter.parse(
                params("f.openingDate.from", "2024-01-01T00:00:00Z", "f.openingDate.to", "2024-12-31T00:00:00Z"));
        assertThat(both.dateRangeFilters().get("openingDate")).isEqualTo(new RecordingUnitListFilter.DateRange(
                OffsetDateTime.parse("2024-01-01T00:00:00Z"), OffsetDateTime.parse("2024-12-31T00:00:00Z")));

        RecordingUnitListFilter fromOnly = RecordingUnitListFilter.parse(params("f.closingDate.from", "2024-01-01T00:00:00Z"));
        assertThat(fromOnly.dateRangeFilters().get("closingDate"))
                .isEqualTo(new RecordingUnitListFilter.DateRange(OffsetDateTime.parse("2024-01-01T00:00:00Z"), null));
    }

    @Test
    void parse_intRange_bothBoundsAndEachBoundAlone() {
        RecordingUnitListFilter both = RecordingUnitListFilter.parse(params("f.tpq.from", "-500", "f.tpq.to", "100"));
        assertThat(both.intRangeFilters().get("tpq")).isEqualTo(new RecordingUnitListFilter.IntRange(-500, 100));

        RecordingUnitListFilter toOnly = RecordingUnitListFilter.parse(params("f.taq.to", "1200"));
        assertThat(toOnly.intRangeFilters().get("taq")).isEqualTo(new RecordingUnitListFilter.IntRange(null, 1200));
    }

    @Test
    void parse_unknownKey_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.description", "x")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_actionUnitFilter_isRejected_becauseThePathAlreadyScopesToOneProject() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.actionUnit", "5")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_rangeSuffixOnANonRangeField_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.fullIdentifier.from", "UE")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_rangeFieldWithoutSuffix_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.tpq", "10")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_multipleValuesForAContainsFilter_throws400() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.fullIdentifier", "UE1");
        params.add("f.fullIdentifier", "UE2");

        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_nonNumericId_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.type", "not-a-number")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_nonIsoDate_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.openingDate.from", "not-a-date")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_nonNumericIntRangeBound_throws400() {
        assertThatThrownBy(() -> RecordingUnitListFilter.parse(params("f.tpq.from", "abc")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void empty_hasNoFilters() {
        assertThat(RecordingUnitListFilter.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void toFilterDTO_searchLandsOnFullIdentifier_andExplicitFilterWins() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.EMPTY;
        FilterDTO dto = filter.toFilterDTO("UE1");
        assertThat(dto.containsColumn("fullIdentifier")).isTrue();
        assertThat(dto.valueOfAsString("fullIdentifier")).isEqualTo("UE1");

        RecordingUnitListFilter withExplicit = RecordingUnitListFilter.parse(params("f.fullIdentifier", "UE2"));
        FilterDTO dtoBoth = withExplicit.toFilterDTO("UE1");
        assertThat(dtoBoth.valueOfAsString("fullIdentifier")).isEqualTo("UE2");
    }

    @Test
    void toFilterDTO_carriesEveryBucketIntoTheFilterDTO() {
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(params(
                "f.type", "12",
                "f.tpq.from", "-100",
                "f.openingDate.to", "2024-06-01T00:00:00Z"));

        FilterDTO dto = filter.toFilterDTO(null);

        assertThat(dto.containsColumn("type")).isTrue();
        assertThat(dto.valueAsIdListOf("type")).containsExactly(12L);
        assertThat(dto.containsColumn("tpq")).isTrue();
        assertThat(dto.valueAsIntRangeOf("tpq")).isEqualTo(new FilterDTO.IntRange(-100, null));
        assertThat(dto.containsColumn("openingDate")).isTrue();
        assertThat(dto.valueAsDateRangeOf("openingDate"))
                .isEqualTo(new FilterDTO.DateRange(null, OffsetDateTime.parse("2024-06-01T00:00:00Z")));
    }
}
