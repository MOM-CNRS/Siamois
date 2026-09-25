package fr.siamois.ui.api.openapi.v1.request.project;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectListFilterTest {

    private static MultiValueMap<String, String> params(String... keyValuePairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.add(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    @Test
    void parse_ignoresParamsWithoutTheFPrefix() {
        ProjectListFilter filter = ProjectListFilter.parse(params("offset", "0", "limit", "20", "sort", "name:asc"));
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void parse_bareKey_isATextContainsFilter() {
        ProjectListFilter filter = ProjectListFilter.parse(params("f.name", "foss"));
        assertThat(filter.containsFilters()).containsEntry("name", "foss");
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void parse_blankContainsValue_isDropped() {
        ProjectListFilter filter = ProjectListFilter.parse(params("f.name", "  "));
        assertThat(filter.containsFilters()).isEmpty();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void parse_repeatedKey_isOrSemanticsForConceptOneIn() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.status", "12");
        params.add("f.status", "44");

        ProjectListFilter filter = ProjectListFilter.parse(params);

        assertThat(filter.conceptOneInFilters()).containsEntry("status", List.of(12L, 44L));
    }

    @Test
    void parse_conceptManyIn_and_spatialOneIn_parseTheSameWayAsConceptOneIn() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.periods", "1");
        params.add("f.periods", "2");
        params.add("f.subjects", "3");
        params.add("f.mainLocation", "9");

        ProjectListFilter filter = ProjectListFilter.parse(params);

        assertThat(filter.conceptManyInFilters()).containsEntry("periods", List.of(1L, 2L));
        assertThat(filter.conceptManyInFilters()).containsEntry("subjects", List.of(3L));
        assertThat(filter.spatialOneInFilters()).containsEntry("mainLocation", List.of(9L));
    }

    @Test
    void parse_numericRange_bothBoundsAndEachBoundAlone() {
        ProjectListFilter both = ProjectListFilter.parse(params("f.openingRate.from", "10", "f.openingRate.to", "40"));
        assertThat(both.numericRangeFilters().get("openingRate"))
                .isEqualTo(new ProjectListFilter.NumericRange(10.0, 40.0));

        ProjectListFilter fromOnly = ProjectListFilter.parse(params("f.openingRate.from", "10"));
        assertThat(fromOnly.numericRangeFilters().get("openingRate"))
                .isEqualTo(new ProjectListFilter.NumericRange(10.0, null));

        ProjectListFilter toOnly = ProjectListFilter.parse(params("f.openingRate.to", "40"));
        assertThat(toOnly.numericRangeFilters().get("openingRate"))
                .isEqualTo(new ProjectListFilter.NumericRange(null, 40.0));
    }

    @Test
    void parse_unknownKey_throws400() {
        assertThatThrownBy(() -> ProjectListFilter.parse(params("f.zmin", "10")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_rangeSuffixOnANonRangeField_throws400() {
        assertThatThrownBy(() -> ProjectListFilter.parse(params("f.name.from", "foss")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_rangeFieldWithoutSuffix_throws400() {
        assertThatThrownBy(() -> ProjectListFilter.parse(params("f.openingRate", "10")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_multipleValuesForAContainsFilter_throws400() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.name", "foss");
        params.add("f.name", "sile");

        assertThatThrownBy(() -> ProjectListFilter.parse(params))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_nonNumericConceptId_throws400() {
        assertThatThrownBy(() -> ProjectListFilter.parse(params("f.status", "not-a-number")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void parse_nonNumericRangeBound_throws400() {
        assertThatThrownBy(() -> ProjectListFilter.parse(params("f.openingRate.from", "abc")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void empty_hasNoFilters() {
        assertThat(ProjectListFilter.EMPTY.isEmpty()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void spatialContext_isAManyInFilter_andWithSpatialContextForcesItKeepingTheRest() {
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("f.spatialContext", "3");
        params.add("f.name", "fouille");

        ProjectListFilter parsed = ProjectListFilter.parse(params);
        org.assertj.core.api.Assertions.assertThat(parsed.conceptManyInFilters()).containsEntry("spatialContext", java.util.List.of(3L));

        ProjectListFilter forced = parsed.withSpatialContext(5L);
        org.assertj.core.api.Assertions.assertThat(forced.conceptManyInFilters()).containsEntry("spatialContext", java.util.List.of(5L));
        org.assertj.core.api.Assertions.assertThat(forced.containsFilters()).containsEntry("name", "fouille");
    }

    @Test
    void withIdIn_restrictsToThoseIds_andIsNoLongerEmpty() {
        ProjectListFilter filter = ProjectListFilter.EMPTY.withIdIn(java.util.Set.of(3L));

        assertThat(filter.idIn()).containsExactly(3L);
        assertThat(filter.isEmpty()).isFalse();
        assertThat(filter.withSpatialContext(9L).idIn()).containsExactly(3L);
    }
}
