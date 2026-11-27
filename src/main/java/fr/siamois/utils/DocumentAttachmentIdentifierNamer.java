package fr.siamois.utils;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds document identifiers from direct attachments:
 * <ul>
 *   <li>project only → {@code <project>_<######>}</li>
 *   <li>project + UE(s) → {@code <project>_<UE1>_…_<UEn>_<######>}</li>
 *   <li>project + find(s) → {@code <project>_<find1>_…_<######>}</li>
 *   <li>project + UE(s) + find(s) → {@code <project>_<UE…>_<find…>_<######>}</li>
 * </ul>
 */
public final class DocumentAttachmentIdentifierNamer {

    public static final int COUNTER_WIDTH = 6;
    private static final Pattern UNSAFE = Pattern.compile("[^\\p{Alnum}_\\-]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DocumentAttachmentIdentifierNamer() {
    }

    public static String projectSegment(ActionUnitDTO project) {
        if (project == null) {
            return null;
        }
        if (StringUtils.hasText(project.getName())) {
            return sanitize(project.getName());
        }
        if (StringUtils.hasText(project.getFullIdentifier())) {
            return sanitize(project.getFullIdentifier());
        }
        return sanitize(project.getIdentifier());
    }

    public static String recordingUnitSegment(RecordingUnitSummaryDTO recordingUnit) {
        if (recordingUnit == null) {
            return null;
        }
        if (StringUtils.hasText(recordingUnit.getFullIdentifier())) {
            return sanitize(recordingUnit.getFullIdentifier());
        }
        return sanitize(recordingUnit.getIdentifier());
    }

    public static String findSegment(SpecimenSummaryDTO find) {
        if (find == null) {
            return null;
        }
        return sanitize(find.getFullIdentifier());
    }

    /**
     * Prefix without the trailing counter, e.g. {@code FouilleA_UE12_MOB3}.
     */
    public static String buildPrefix(String projectSegment,
                                     List<String> recordingUnitSegments,
                                     List<String> findSegments) {
        if (!StringUtils.hasText(projectSegment)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(projectSegment.trim());
        appendNonBlank(parts, recordingUnitSegments);
        appendNonBlank(parts, findSegments);
        return String.join("_", parts);
    }

    public static String buildPrefix(ActionUnitDTO project,
                                     List<RecordingUnitSummaryDTO> recordingUnits,
                                     List<SpecimenSummaryDTO> finds) {
        List<String> ruSegments = recordingUnits == null
                ? List.of()
                : recordingUnits.stream()
                .map(DocumentAttachmentIdentifierNamer::recordingUnitSegment)
                .filter(Objects::nonNull)
                .toList();
        List<String> findSegs = finds == null
                ? List.of()
                : finds.stream()
                .map(DocumentAttachmentIdentifierNamer::findSegment)
                .filter(Objects::nonNull)
                .toList();
        return buildPrefix(projectSegment(project), ruSegments, findSegs);
    }

    public static String withCounter(String prefix, int number) {
        if (!StringUtils.hasText(prefix) || number < 1) {
            return null;
        }
        return prefix + "_" + String.format(Locale.ROOT, "%0" + COUNTER_WIDTH + "d", number);
    }

    /**
     * Extracts the trailing {@value #COUNTER_WIDTH}-digit counter when {@code identifier} is
     * exactly {@code prefix_######}; otherwise {@code null}.
     */
    public static Integer parseCounter(String prefix, String identifier) {
        if (!StringUtils.hasText(prefix) || !StringUtils.hasText(identifier)) {
            return null;
        }
        String expectedLead = prefix + "_";
        if (!identifier.startsWith(expectedLead)) {
            return null;
        }
        String suffix = identifier.substring(expectedLead.length());
        if (suffix.length() != COUNTER_WIDTH) {
            return null;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return null;
            }
        }
        try {
            int value = Integer.parseInt(suffix);
            return value < 1 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Highest counter among identifiers that match {@code prefix_######}, or {@code 0} if none.
     */
    public static int maxCounter(String prefix, Collection<String> identifiers) {
        if (!StringUtils.hasText(prefix) || identifiers == null || identifiers.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (String identifier : identifiers) {
            Integer counter = parseCounter(prefix, identifier);
            if (counter != null && counter > max) {
                max = counter;
            }
        }
        return max;
    }

    public static String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(raw.trim()).replaceAll("-");
        String cleaned = UNSAFE.matcher(collapsed).replaceAll("");
        cleaned = cleaned.replaceAll("^[_\\-]+|[_\\-]+$", "");
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private static void appendNonBlank(List<String> parts, List<String> segments) {
        if (segments == null) {
            return;
        }
        for (String segment : segments) {
            if (StringUtils.hasText(segment)) {
                parts.add(segment.trim());
            }
        }
    }
}
