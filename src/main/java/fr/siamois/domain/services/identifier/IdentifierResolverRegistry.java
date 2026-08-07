package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Table-isolated catalogs used for validation and rendering of configurable identifiers. */
@Service
public class IdentifierResolverRegistry {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private final Map<ConfigurableTable, Map<String, IdentifierResolver<IdentifierRenderContext>>> catalogs;

    public IdentifierResolverRegistry() {
        catalogs = Map.of(
                ConfigurableTable.UE, catalog(
                        numerical("NUM_UE", null),
                        numerical("NUM_PARENT", "PARENT_RU"),
                        text("ID_PARENT", "PARENT_RU"),
                        numerical("NUM_USPATIAL", "SPATIAL_PLACE"),
                        text("ID_UA", null)),
                ConfigurableTable.MOBILIER, catalog(
                        numerical("NUM_MOBILIER", null),
                        numerical("NUM_PARENT", "PARENT_MOBILIER"),
                        text("ID_PARENT", "PARENT_MOBILIER"),
                        numerical("NUM_UE", "PARENT_RU"),
                        text("ID_UE", "PARENT_RU"),
                        text("ID_UA", null)),
                ConfigurableTable.CONTENANT, catalog(
                        numerical("NUM_CONTAINER", null),
                        numerical("NUM_PARENT", "PARENT_CONTAINER"),
                        text("ID_PARENT", "PARENT_CONTAINER"),
                        text("ID_UA", null)),
                ConfigurableTable.PHASE, catalog(
                        numerical("NUM_PHASE", null),
                        numerical("NUM_PARENT", "PARENT_PHASE"),
                        text("ID_PARENT", "PARENT_PHASE"),
                        numerical("PHASE_ORDER", "PHASE_ORDER"),
                        text("ID_UA", null))
        );
    }

    public List<IdentifierResolver<IdentifierRenderContext>> resolvers(ConfigurableTable table) {
        return List.copyOf(requireCatalog(table).values());
    }

    public Optional<IdentifierResolver<IdentifierRenderContext>> resolver(ConfigurableTable table, String code) {
        return Optional.ofNullable(requireCatalog(table).get(code));
    }

    public void validate(ConfigurableTable table, String format) {
        if (format == null || format.isBlank()) {
            throw new IdentifierFormatException("Identifier format is required");
        }

        Map<String, IdentifierResolver<IdentifierRenderContext>> catalog = requireCatalog(table);
        Matcher matcher = TOKEN_PATTERN.matcher(format);
        boolean foundToken = false;
        while (matcher.find()) {
            foundToken = true;
            String[] parts = matcher.group(1).split(":", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new IdentifierFormatException("Invalid identifier token " + matcher.group());
            }
            IdentifierResolver<IdentifierRenderContext> resolver = catalog.get(parts[0]);
            if (resolver == null) {
                throw new IdentifierFormatException("Token " + parts[0] + " is not supported for " + table);
            }
            String specifier = parts.length == 2 ? parts[1] : null;
            validateSpecifier(resolver, specifier);
        }

        String withoutTokens = matcher.reset().replaceAll("");
        if (!foundToken || withoutTokens.indexOf('{') >= 0 || withoutTokens.indexOf('}') >= 0) {
            throw new IdentifierFormatException("Malformed identifier format");
        }

        String ownToken = ownNumericalToken(table);
        IdentifierResolver<IdentifierRenderContext> ownResolver = catalog.get(ownToken);
        if (ownResolver == null || !ownResolver.isUsedBy(format)) {
            throw new IdentifierFormatException("Identifier format must contain " + ownToken);
        }
    }

    public String render(ConfigurableTable table, String format, IdentifierRenderContext context) {
        validate(table, format);
        String result = format;
        for (IdentifierResolver<IdentifierRenderContext> resolver : requireCatalog(table).values()) {
            result = resolver.render(result, context);
        }
        return result;
    }

    public String ownNumericalToken(ConfigurableTable table) {
        return switch (table) {
            case UE -> "NUM_UE";
            case MOBILIER -> "NUM_MOBILIER";
            case CONTENANT -> "NUM_CONTAINER";
            case PHASE -> "NUM_PHASE";
        };
    }

    private static void validateSpecifier(IdentifierResolver<?> resolver, String specifier) {
        if (resolver.valueKind() == IdentifierValueKind.NUMERICAL) {
            if (specifier != null && !specifier.matches("0+")) {
                throw new IdentifierFormatException("Numerical token " + resolver.code() + " requires a zero width specifier");
            }
        } else if (specifier != null) {
            throw new IdentifierFormatException("Text token " + resolver.code() + " does not accept a width specifier");
        }
    }

    private Map<String, IdentifierResolver<IdentifierRenderContext>> requireCatalog(ConfigurableTable table) {
        return Objects.requireNonNull(catalogs.get(table), "No identifier resolver catalog for " + table);
    }

    private static IdentifierResolver<IdentifierRenderContext> numerical(String code, String dimension) {
        return new NumericalIdentifierResolver<>(code, titleCode(code), descriptionCode(code), dimension);
    }

    private static IdentifierResolver<IdentifierRenderContext> text(String code, String dimension) {
        return new TextIdentifierResolver<>(code, titleCode(code), descriptionCode(code), dimension);
    }

    private static String titleCode(String code) {
        return "identifier.token." + code.toLowerCase(Locale.ROOT) + ".title";
    }

    private static String descriptionCode(String code) {
        return "identifier.token." + code.toLowerCase(Locale.ROOT) + ".description";
    }

    @SafeVarargs
    private static Map<String, IdentifierResolver<IdentifierRenderContext>> catalog(
            IdentifierResolver<IdentifierRenderContext>... resolvers) {
        Map<String, IdentifierResolver<IdentifierRenderContext>> result = new LinkedHashMap<>();
        for (IdentifierResolver<IdentifierRenderContext> resolver : resolvers) {
            if (result.put(resolver.code(), resolver) != null) {
                throw new IllegalStateException("Duplicate identifier token " + resolver.code());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
