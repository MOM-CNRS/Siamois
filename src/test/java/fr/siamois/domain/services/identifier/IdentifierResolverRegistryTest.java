package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierResolverRegistryTest {
    private final IdentifierResolverRegistry registry = new IdentifierResolverRegistry();

    @Test
    void catalogs_shouldExposeOnlyTokensSupportedByTheirTable() {
        assertThat(codes(ConfigurableTable.UE))
                .containsExactly("NUM_UE", "NUM_PARENT", "ID_PARENT", "NUM_USPATIAL", "ID_UA");
        assertThat(codes(ConfigurableTable.MOBILIER))
                .containsExactly("NUM_MOBILIER", "NUM_PARENT", "ID_PARENT", "NUM_UE", "ID_UE", "ID_UA");
        assertThat(codes(ConfigurableTable.CONTENANT))
                .containsExactly("NUM_CONTAINER", "NUM_PARENT", "ID_PARENT", "ID_UA");
        assertThat(codes(ConfigurableTable.PHASE))
                .containsExactly("NUM_PHASE", "NUM_PARENT", "ID_PARENT", "PHASE_ORDER", "ID_UA");
    }

    @Test
    void validate_shouldRequireTheTablesOwnNumericalToken() {
        assertThatCode(() -> registry.validate(ConfigurableTable.MOBILIER, "M-{NUM_MOBILIER:000}"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validate(ConfigurableTable.MOBILIER, "{NUM_UE:000}"))
                .isInstanceOf(IdentifierFormatException.class)
                .hasMessageContaining("NUM_MOBILIER");
    }

    @Test
    void validate_shouldRejectTokensFromAnotherTable() {
        assertThatThrownBy(() -> registry.validate(ConfigurableTable.UE, "{NUM_UE}-{NUM_MOBILIER}"))
                .isInstanceOf(IdentifierFormatException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void validate_shouldRejectInvalidSpecifiersAndMalformedBraces() {
        assertThatThrownBy(() -> registry.validate(ConfigurableTable.UE, "{NUM_UE:XXX}"))
                .isInstanceOf(IdentifierFormatException.class);
        assertThatThrownBy(() -> registry.validate(ConfigurableTable.UE, "{NUM_UE}-{ID_UA:XXX}"))
                .isInstanceOf(IdentifierFormatException.class);
        assertThatThrownBy(() -> registry.validate(ConfigurableTable.UE, "{NUM_UE"))
                .isInstanceOf(IdentifierFormatException.class);
    }

    @Test
    void render_shouldUseOneValidatedCatalogForPresentAndMissingValues() {
        String rendered = registry.render(
                ConfigurableTable.MOBILIER,
                "M-{NUM_MOBILIER:000}-{NUM_UE:0000}-{ID_UE}",
                new MapIdentifierRenderContext(Map.of("NUM_MOBILIER", 7)));

        assertThat(rendered).isEqualTo("M-007-0000-XXX");
    }

    private java.util.List<String> codes(ConfigurableTable table) {
        return registry.resolvers(table).stream().map(IdentifierResolver::code).toList();
    }
}
