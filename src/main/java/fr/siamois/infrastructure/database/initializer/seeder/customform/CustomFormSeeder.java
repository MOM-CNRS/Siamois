package fr.siamois.infrastructure.database.initializer.seeder.customform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customform.*;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldAnswerDTO;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.repositories.form.CustomFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomFormSeeder {

    private final CustomFormRepository customFormRepository;
    private final CustomFieldSeeder fieldSeeder;

    private static final ObjectMapper OM = new ObjectMapper();


    private EnabledWhenJson toEnabledWhenJson(EnabledWhenSpecSeedDTO dto) {
        if (dto == null) throw new IllegalArgumentException("enabledWhen must not be null");
        if (dto.field() == null) throw new IllegalArgumentException("enabledWhen.field is required");
        if (dto.expectedValues() == null || dto.expectedValues().isEmpty()) {
            throw new IllegalArgumentException("enabledWhen.expectedValues must not be empty");
        }

        // 1) opérateur
        final EnabledWhenJson.Op op = switch (dto.operator()) {
            case EQUALS     -> EnabledWhenJson.Op.eq;
            case NOT_EQUALS -> EnabledWhenJson.Op.neq;
            case IN         -> EnabledWhenJson.Op.in;
            default         -> throw new IllegalArgumentException("Unsupported operator: " + dto.operator());
        };

        // 2) champ observé = dto.field()
        final Long fieldId = extractFieldId(dto.field()); // <- utilise le seeder pour retrouver/créer et renvoyer l'id

        // 3) toutes les expectedValues doivent référencer le même champ
        for (int i = 0; i < dto.expectedValues().size(); i++) {
            CustomFieldAnswerDTO ev = dto.expectedValues().get(i);
            if (ev == null || ev.field() == null) {
                throw new IllegalArgumentException("expectedValues[" + i + "].field is required");
            }
            Long otherId = extractFieldId(ev.field());
            if (!fieldId.equals(otherId)) {
                throw new IllegalArgumentException(
                        "All expectedValues must reference the same fieldId as enabledWhen.field (expected "
                                + fieldId + ", got " + otherId + " at index " + i + ")"
                );
            }
        }

        // 4) cardinalité selon l’opérateur
        int n = dto.expectedValues().size();
        if ((op == EnabledWhenJson.Op.eq || op == EnabledWhenJson.Op.neq) && n != 1) {
            throw new IllegalArgumentException("EQUALS/NOT_EQUALS require exactly 1 expected value (got " + n + ")");
        }

        // 5) mapping des valeurs -> ValueJson
        List<EnabledWhenJson.ValueJson> values = new ArrayList<>(n);
        for (CustomFieldAnswerDTO ev : dto.expectedValues()) {
            values.add(toValueJson(ev)); // construit {answerClass, value(JsonNode)} selon ton DTO
        }

        // 6) build modèle
        EnabledWhenJson ew = new EnabledWhenJson();
        ew.setOp(op);
        ew.setFieldId(fieldId);
        ew.setValues(values);
        return ew;
    }

    private Long extractFieldId(CustomFieldSeederSpec spec) {
        var field = fieldSeeder.findFieldOrThrow(spec);
        return field.getId();
    }

    private EnabledWhenJson.ValueJson toValueJson(CustomFieldAnswerDTO dto) {
        EnabledWhenJson.ValueJson v = new EnabledWhenJson.ValueJson();
        v.setAnswerClass(dto.answerClass().getName());

        //
        if (dto.valueAsConcept() != null) {
            ObjectNode obj = OM.createObjectNode()
                    .put("vocabularyExtId", dto.valueAsConcept().vocabularyExtId())
                    .put("conceptExtId", dto.valueAsConcept().conceptExtId());
            v.setValue(obj);
            return v;
        }

        // A ajouter si besoin d'autres type de réponse: liste de concept, nombre, texte ...
        // Pour l'instant nous n'avons besoin que du concept

        // fallback: null
        v.setValue(NullNode.getInstance());
        return v;
    }

    private List<CustomFormPanel> convertLayoutFromDto(List<CustomFormPanelDTO> panelDtos) {
        if (panelDtos == null || panelDtos.isEmpty()) return List.of();

        List<CustomFormPanel> panels = new ArrayList<>(panelDtos.size());
        for (CustomFormPanelDTO pDto : panelDtos) {
            panels.add(convertPanel(pDto));
        }
        return panels;
    }

    private CustomFormPanel convertPanel(CustomFormPanelDTO dto) {
        if (dto == null) return null;

        CustomFormPanel panel = new CustomFormPanel.Builder()
                .isSystemPanel(dto.isSystemPanel())
                .name(dto.name())
                .className(dto.className())
                .build();

        List<CustomRow> rows = new ArrayList<>();
        if (dto.rows() != null) {
            for (CustomRowDTO rowDto : dto.rows()) {
                rows.add(convertRow(rowDto));
            }
        }
        panel.setRows(rows);
        return panel;
    }

    private CustomRow convertRow(CustomRowDTO rowDto) {
        CustomRow row = new CustomRow();
        List<CustomCol> cols = new ArrayList<>();
        if (rowDto != null && rowDto.columns() != null) {
            for (CustomColDTO colDto : rowDto.columns()) {
                cols.add(convertCol(colDto));
            }
        }
        row.setColumns(cols);
        return row;
    }

    private CustomCol convertCol(CustomColDTO colDto) {
        CustomCol col = new CustomCol();
        if (colDto == null) return col;

        col.setReadOnly(colDto.readOnly());
        col.setRequired(colDto.isRequired());
        col.setClassName(colDto.className());

        if(colDto.enabledWhen() != null) {
            col.setEnabledWhenSpec(toEnabledWhenJson(colDto.enabledWhen()));
        }

        // Resolve/create field by natural key (code) from your earlier design
        CustomField field = fieldSeeder.findFieldOrThrow(colDto.field());
        col.setField(field);

        return col;
    }

    public CustomForm findOrNull(CustomFormDTO dto) {
        return customFormRepository.findByNameAndDescription(dto.name(), dto.description()).orElse(null);
    }

    public CustomForm findOrThrow(CustomFormDTO dto) {
        return customFormRepository.findByNameAndDescription(dto.name(), dto.description())
                .orElseThrow(() -> new IllegalStateException("Form introuvable"));
    }


    public void seed(List<CustomFormDTO> specs)  {
        for (var s : specs) {
            CustomForm form = findOrNull(s);
            if(form == null) {
                // Verify layout validity
                CustomForm f = null;
                f = new CustomForm();
                f.setName(s.name());
                f.setDescription(s.description());
                f.setLayout(convertLayoutFromDto(s.layout()));
                customFormRepository.save(f);
            }
            else {
                form.setLayout(convertLayoutFromDto(s.layout()));
                // appliquer les modifications
                customFormRepository.save(form);
            }
        }
    }

}
