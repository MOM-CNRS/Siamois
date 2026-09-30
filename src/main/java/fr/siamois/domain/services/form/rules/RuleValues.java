package fr.siamois.domain.services.form.rules;

import fr.siamois.dto.entity.MeasurementAnswerDTO;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalises answer values into comparable scalars — the Java twin of
 * {@code frontend/src/rules/values.ts}, same rules:
 * <ul>
 *   <li>a reference / concept (a DTO with an id, or a {@code {resourceId}} / {@code {id}} map) → its id as a string</li>
 *   <li>a measurement → its numeric value</li>
 *   <li>a date (LocalDateTime read as UTC, as the server stores it; an ISO string) → epoch ms</li>
 *   <li>a number → a double; a boolean as is; a non-date string as is</li>
 *   <li>a collection → every scalar it holds</li>
 * </ul>
 * Scalars are {@link String}, {@link Double} or {@link Boolean}.
 */
public final class RuleValues {

    private static final Pattern ISO_DATE =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(?:[T ][\\d:.]+(?:Z|[+-]\\d{2}:?\\d{2})?)?$");

    private RuleValues() {
        throw new UnsupportedOperationException();
    }

    public static List<Object> scalarsOf(@Nullable Object value) {
        List<Object> out = new ArrayList<>();
        if (value instanceof Collection<?> c) {
            for (Object item : c) {
                Object s = scalarOf(item);
                if (s != null) out.add(s);
            }
            return out;
        }
        Object s = scalarOf(value);
        if (s != null) out.add(s);
        return out;
    }

    public static boolean isEmpty(@Nullable Object value) {
        return scalarsOf(value).isEmpty();
    }

    /** The single comparable number of a value (number, measurement, date), or null. */
    @Nullable
    public static Double numberOf(@Nullable Object value) {
        List<Object> scalars = scalarsOf(value);
        return !scalars.isEmpty() && scalars.get(0) instanceof Double d ? d : null;
    }

    /** The single id / scalar of a value as a string, or null. */
    @Nullable
    public static String idOf(@Nullable Object value) {
        List<Object> scalars = scalarsOf(value);
        return scalars.isEmpty() ? null : asString(scalars.get(0));
    }

    public static boolean same(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
        return asString(a).equals(asString(b));
    }

    /** A scalar as the TS side's String(x) would print it (integral doubles without ".0"). */
    static String asString(Object scalar) {
        if (scalar instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString(d.longValue());
        }
        return String.valueOf(scalar);
    }

    @Nullable
    static Object scalarOf(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return value;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) return stringScalar(s);
        if (value instanceof LocalDateTime ldt) return (double) ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
        if (value instanceof OffsetDateTime odt) return (double) odt.toInstant().toEpochMilli();
        if (value instanceof LocalDate ld) return (double) ld.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        if (value instanceof Instant i) return (double) i.toEpochMilli();
        if (value instanceof MeasurementAnswerDTO m) return m.getNumericValue();
        if (value instanceof Map<?, ?> map) return mapScalar(map);
        return idByGetter(value);
    }

    @Nullable
    private static Object stringScalar(String s) {
        if (s.isEmpty()) return null;
        if (ISO_DATE.matcher(s).matches()) {
            try {
                return (double) (s.length() == 10
                        ? LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        : OffsetDateTime.parse(s).toInstant().toEpochMilli());
            } catch (DateTimeParseException e) {
                try {
                    return (double) LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli();
                } catch (DateTimeParseException ignored) {
                    return s;
                }
            }
        }
        return s;
    }

    @Nullable
    private static Object mapScalar(Map<?, ?> map) {
        for (String key : List.of("resourceId", "conceptId", "id")) {
            Object id = map.get(key);
            if (id != null) return asString(id instanceof Number n ? n.doubleValue() : id);
        }
        if (map.containsKey("numericValue")) {
            return map.get("numericValue") instanceof Number n ? n.doubleValue() : null;
        }
        return null;
    }

    @Nullable
    private static Object idByGetter(Object value) {
        try {
            Method getter = value.getClass().getMethod("getId");
            Object id = getter.invoke(value);
            return id == null ? null : asString(id instanceof Number n ? n.doubleValue() : id);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
