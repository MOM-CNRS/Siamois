package fr.siamois.ui.api.openapi.v1.request.list;

import fr.siamois.dto.FilterDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchModeTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void parse_defaultsToContains_andAcceptsBothModes() {
        assertThat(SearchMode.parse(null)).isEqualTo(SearchMode.CONTAINS);
        assertThat(SearchMode.parse(" ")).isEqualTo(SearchMode.CONTAINS);
        assertThat(SearchMode.parse("contains")).isEqualTo(SearchMode.CONTAINS);
        assertThat(SearchMode.parse("PREFIX")).isEqualTo(SearchMode.PREFIX);
    }

    @Test
    void parse_rejectsUnknownMode() {
        assertThatThrownBy(() -> SearchMode.parse("exact")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void outsideARequest_containsApplies() {
        assertThat(SearchMode.fromRequest()).isEqualTo(SearchMode.CONTAINS);
    }

    @Test
    void addSearch_usesTheRequestMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(SearchMode.PARAM, "prefix");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FilterDTO filter = new FilterDTO();

        SearchMode.addSearch(filter, "fullIdentifier", "EXEMPLE");

        assertThat(filter.filterOf("fullIdentifier").getType()).isEqualTo(FilterDTO.FilterType.START_WITH);
        assertThat(filter.valueOfAsString("fullIdentifier")).isEqualTo("EXEMPLE");
    }

    @Test
    void addSearch_blankSearch_addsNothing() {
        FilterDTO filter = new FilterDTO();

        SearchMode.addSearch(filter, "fullIdentifier", "  ");

        assertThat(filter.filterOf("fullIdentifier")).isNull();
    }
}
