package fr.siamois.domain.services.identifier;

import org.springframework.lang.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A reusable token renderer for one kind of identifier-generation context. */
public interface IdentifierResolver<C extends IdentifierRenderContext> {
    String code();

    /** JavaBean aliases used by JSF EL. */
    default String getCode() {
        return code();
    }

    IdentifierValueKind valueKind();

    default IdentifierValueKind getValueKind() {
        return valueKind();
    }

    String titleCode();

    default String getTitleCode() {
        return titleCode();
    }

    @Nullable
    default String descriptionCode() {
        return null;
    }

    @Nullable
    default String partitionDimensionCode() {
        return null;
    }

    @Nullable
    default Object resolvePartitionValue(C context) {
        String dimension = partitionDimensionCode();
        return dimension == null ? null : context.partitionValue(dimension);
    }

    default boolean isUsedBy(String format) {
        return tokenPattern().matcher(format).find();
    }

    default String render(String format, C context) {
        Matcher matcher = tokenPattern().matcher(format);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = renderValue(context.value(code()), matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Pattern tokenPattern() {
        return Pattern.compile("\\{" + Pattern.quote(code()) + "(?::([^}]*))?}");
    }

    private String renderValue(@Nullable Object value, @Nullable String specifier) {
        if (valueKind() == IdentifierValueKind.TEXT) {
            return value == null || value.toString().isBlank() ? "XXX" : value.toString();
        }

        int width = specifier == null || specifier.isEmpty() ? 1 : specifier.length();
        if (value == null) return "0".repeat(width);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Numerical token " + code() + " requires a Number");
        }
        if (specifier == null || specifier.isEmpty()) return Long.toString(number.longValue());
        return String.format("%0" + width + "d", number.longValue());
    }
}
