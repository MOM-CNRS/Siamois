package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
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
                .isSystemPanel(Boolean.TRUE.equals(dto.isSystemPanel())) // or dto.isSystemPanel()
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

        // Resolve/create field by natural key (code) from your earlier design
        CustomField field = fieldSeeder.findFieldOrThrow(colDto.field());
        col.setField(field);

        return col;
    }

    public CustomForm findOrNull(CustomFormDTO dto) {
        return customFormRepository.findByNameAndDescription(dto.name(), dto.description()).orElse(null);
    }


    public void seed(List<CustomFormDTO> specs) throws DatabaseDataInitException {
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
        }
    }

}
