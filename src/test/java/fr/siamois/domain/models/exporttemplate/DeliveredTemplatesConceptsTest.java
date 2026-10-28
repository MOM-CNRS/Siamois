package fr.siamois.domain.models.exporttemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Every concept a delivered template designates is the concept of a system field: no column silently stays empty. */
class DeliveredTemplatesConceptsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"rapport-operation-national", "rapport-operation-national-sous-marin"})
    void everyDesignatedConceptBelongsToASystemField(String template) throws IOException {
        Set<String> known = new TreeSet<>();
        ExportFieldResolver resolver = new ExportFieldResolver(null, null);
        for (ExportSubject subject : ExportSubject.values()) {
            for (CustomField field : resolver.systemFields(subject)) {
                if (field.getConcept() != null) {
                    known.add(field.getConcept().getExternalId());
                }
            }
        }

        List<String> designated = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/export-templates/" + template + ".json")) {
            assertThat(in).isNotNull();
            collectConceptIds(MAPPER.readTree(in).get("fields"), designated);
        }

        assertThat(designated).isNotEmpty();
        assertThat(designated.stream().filter(id -> !known.contains(id)).distinct().toList())
                .as("concepts of %s that no system field carries", template).isEmpty();
    }

    private static void collectConceptIds(JsonNode node, List<String> out) {
        if (node.isObject()) {
            JsonNode concept = node.get("concept");
            if (concept != null && concept.has("id")) {
                out.add(concept.get("id").asText());
            }
            node.elements().forEachRemaining(child -> collectConceptIds(child, out));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectConceptIds(child, out));
        }
    }
}
