package fr.siamois.ui.api.openapi.v1.request.list;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldListQueryTest {

    @Test
    void takesOnlyTheFieldIdKeys_leavingNamedOnesToTheirOwnParser() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.fullIdentifier", "UE");
        params.add("f.-118", "4");
        params.add("f.-118", "7");
        params.add("f.42.from", "2024-01-01");
        params.add("offset", "0");

        FieldListQuery query = FieldListQuery.parse(params, "name:asc", "fr");

        assertThat(query.filters()).containsOnlyKeys(-118L, 42L);
        assertThat(query.filters().get(-118L).values()).containsExactly("4", "7");
        assertThat(query.filters().get(42L).from()).isEqualTo("2024-01-01");
        assertThat(query.filters().get(42L).isRange()).isTrue();
        assertThat(query.sortFieldId()).isNull();
    }

    @Test
    void readsAFieldSort_negativeIdsIncluded() {
        FieldListQuery query = FieldListQuery.parse(null, "-503:desc", "en");

        assertThat(query.sortFieldId()).isEqualTo(-503L);
        assertThat(query.ascending()).isFalse();
        assertThat(query.lang()).isEqualTo("en");
        assertThat(FieldListQuery.isFieldSort("-503:desc")).isTrue();
        assertThat(FieldListQuery.isFieldSort("fullIdentifier:asc")).isFalse();
    }

    @Test
    void dropsBlankValues_andIsEmptyWithoutAnyFieldKey() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.12", " ");
        params.add("f.13.to", "");

        assertThat(FieldListQuery.parse(params, "name:asc", "fr").isEmpty()).isTrue();
    }

    @Test
    void rejectsAValueAndARangeOnTheSameField_andABadDirection() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("f.12", "3");
        params.add("f.12.from", "1");

        assertThatThrownBy(() -> FieldListQuery.parse(params, null, "fr")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> FieldListQuery.parse(null, "12:up", "fr")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void namedSortOr_fallsBackForAFieldSort() {
        assertThat(FieldListQuery.namedSortOr("12:asc", "name:asc")).isEqualTo("name:asc");
        assertThat(FieldListQuery.namedSortOr("identifier:desc", "name:asc")).isEqualTo("identifier:desc");
        assertThat(FieldListQuery.isFieldKey("12")).isTrue();
        assertThat(List.of("status", "1a", "")).noneMatch(FieldListQuery::isFieldKey);
    }
}
