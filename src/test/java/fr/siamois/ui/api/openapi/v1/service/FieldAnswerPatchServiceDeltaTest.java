package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.*;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.form.dto.*;
import fr.siamois.ui.bean.LabelBean;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An add/remove on a system field (parents…) must reach the DTO: it used to be nulled right after being written. */
class FieldAnswerPatchServiceDeltaTest {
    @Test
    void addOnASystemFieldReachesTheDto() {
        RecordingUnitRepository rus = mock(RecordingUnitRepository.class);
        RecordingUnit p = new RecordingUnit(); p.setId(117L); ActionUnit au = new ActionUnit(); au.setId(7L); p.setActionUnit(au);
        when(rus.findById(117L)).thenReturn(Optional.of(p));
        FormService fs = new FormService(mock(LabelBean.class), mock(UnitDefinitionMapper.class), mock(CustomFieldAnswerService.class));
        FieldAnswerPatchService service = new FieldAnswerPatchService(fs, mock(ConceptRepository.class), mock(ConceptMapper.class),
                mock(PersonRepository.class), mock(PersonMapper.class), mock(ActionUnitRepository.class), mock(ActionUnitSummaryMapper.class),
                mock(ActionCodeRepository.class), mock(ActionCodeMapper.class), mock(SpatialUnitRepository.class), mock(SpatialUnitSummaryMapper.class),
                rus, new RecordingUnitSummaryMapperImpl(mock(fr.siamois.ui.mapper.adapter.ConversionServiceAdapter.class)), mock(PhaseRepository.class), mock(PhaseMapper.class),
                mock(ContainerRepository.class), mock(ContainerMapper.class), mock(SpecimenRepository.class), mock(SpecimenSummaryMapper.class), mock(UnitDefinitionMapper.class));
        CustomFieldSelectMultipleRecordingUnit f = CustomFieldSelectMultipleRecordingUnit.builder().label("x").isSystemField(true).id(-319L).valueBinding("parents").build();
        CustomColUiDto col = new CustomColUiDto(); col.setField(f);
        CustomRowUiDto row = new CustomRowUiDto(); row.setColumns(new ArrayList<>(List.of(col)));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto(); panel.setRows(List.of(row));
        FormUiDto form = new FormUiDto(); form.setLayout(List.of(panel));
        RecordingUnitDTO dto = new RecordingUnitDTO(); dto.setId(146L); dto.setParents(new HashSet<>()); dto.setChildren(new HashSet<>());
        service.applyLenient(dto, form, Map.of("-319", new AnswerInput(null, null, List.of("117"), List.of())), 7L);
        org.assertj.core.api.Assertions.assertThat(dto.getParents()).hasSize(1);
    }
}
