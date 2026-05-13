package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOne;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Enrichissement OpenAPI pour le détail UE et le formulaire de création : formulaire effectif, champs et vocabulaires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingUnitOpenApiService {

    private final RecordingUnitService recordingUnitService;
    private final FormService formService;
    private final FieldConfigurationService fieldConfigurationService;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final ConversionService conversionService;
    private final CustomFormLayoutConverter customFormLayoutConverter;
    private final ConceptMapper conceptMapper;
    private final InstitutionService institutionService;
    private final ConceptRepository conceptRepository;
    private final SpecimenService specimenService;

    @Transactional(readOnly = true)
    public RecordingUnitMobileDetailData buildMobileDetail(String recordingUnitKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds,
                                                           List<String> counts, String lang) {
        RecordingUnitService.AccessibleRecordingUnit bundle =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, counts);
        RecordingUnitDTO dto = bundle.dto();
        RecordingUnit entity = bundle.entity();

        RecordingUnitResource recordingUnit = recordingUnitResponseMapper.convert(dto);

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null) {
            return new RecordingUnitMobileDetailData(recordingUnit, null, Map.of(), Map.of());
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(dto.getType(), institution);
        if (customForm == null) {
            return new RecordingUnitMobileDetailData(recordingUnit, null, Map.of(), Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);

        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());

        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        Map<String, RecordingUnitFormFieldApi> fields = buildFieldsWithFallback(entity, dto, customForm, fieldSource, lang);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitMobileDetailData(recordingUnit, formBundle, fields, vocabs);
    }

    /**
     * Formulaire de création d'une UE : même résolution que le détail (type + institution), sans entité persistée.
     */
    @Transactional(readOnly = true)
    public RecordingUnitCreateFormData buildRecordingUnitCreateForm(long organizationId,
                                                                  long recordingUnitTypeConceptId,
                                                                  PersonDTO personDto,
                                                                  String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        Concept typeConcept = conceptRepository.findById(recordingUnitTypeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording unit type not found"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution);
        if (customForm == null) {
            return new RecordingUnitCreateFormData(typeDto, null, Map.of(), Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);

        Map<String, RecordingUnitFormFieldApi> fields = buildCustomFormFieldsForBindTarget(
                shell, fieldSource, "création UE", typeDto.getId());

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitCreateFormData(typeDto, formBundle, fields, vocabs);
    }

    /**
     * Formulaire d'édition d'un mobilier (spécimen) : résolution du formulaire custom par type de spécimen
     * et institution de rattachement (même mécanisme que pour une UE).
     */
    @Transactional(readOnly = true)
    public FindFormData buildFindForm(long findId,
                                      PersonDTO personDto,
                                      Set<Long> accessibleInstitutionIds,
                                      String lang) {
        SpecimenDTO specimen = specimenService.findAccessibleById(findId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Find not found"));
        InstitutionDTO institution = specimen.getCreatedByInstitution();
        ConceptDTO specimenType = specimen.getType();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Find has no owning institution");
        }
        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(specimenType, institution);
        if (customForm == null) {
            return new FindFormData(specimenType, null, Map.of(), Map.of());
        }
        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);
        Long typeIdForLog = specimenType != null ? specimenType.getId() : null;
        Map<String, RecordingUnitFormFieldApi> fields = buildCustomFormFieldsForBindTarget(
                specimen, fieldSource, "mobilité id=" + findId, typeIdForLog);
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);
        return new FindFormData(specimenType, formBundle, fields, vocabs);
    }

    private Map<String, RecordingUnitFormFieldApi> buildCustomFormFieldsForBindTarget(
            Object shell,
            FieldSource fieldSource,
            String logContext,
            Long typeConceptIdForLog) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, shell, fieldSource, true);
            if (response.getAnswers() == null) {
                return buildFieldsMetadataOnly(fieldSource);
            }
            Map<String, RecordingUnitFormFieldApi> fields = new LinkedHashMap<>();
            for (Map.Entry<CustomField, CustomFieldAnswerViewModel> e : response.getAnswers().entrySet()) {
                CustomField field = e.getKey();
                fields.put(String.valueOf(field.getId()), toFieldApi(field, e.getValue()));
            }
            return fields;
        } catch (RuntimeException ex) {
            log.warn(
                    "Impossible d'initialiser les réponses formulaire pour {} (type concept id={}): {}",
                    logContext,
                    typeConceptIdForLog,
                    ex.toString(),
                    ex);
            return buildFieldsMetadataOnly(fieldSource);
        }
    }

    /**
     * Construit les champs avec valeurs ; si le moteur de réponses échoue (type de champ non géré, données incohérentes),
     * retourne au moins les métadonnées des champs du layout pour le mobile (sans currentValue).
     */
    private Map<String, RecordingUnitFormFieldApi> buildFieldsWithFallback(RecordingUnit entity,
                                                                           RecordingUnitDTO dto,
                                                                           CustomForm customForm,
                                                                           FieldSource fieldSource,
                                                                           String lang) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            applyPersistedCustomAnswers(entity, customForm, response, lang);
            if (response.getAnswers() == null) {
                return buildFieldsMetadataOnly(fieldSource);
            }
            Map<String, RecordingUnitFormFieldApi> fields = new LinkedHashMap<>();
            for (Map.Entry<CustomField, CustomFieldAnswerViewModel> e : response.getAnswers().entrySet()) {
                CustomField field = e.getKey();
                fields.put(String.valueOf(field.getId()), toFieldApi(field, e.getValue()));
            }
            return fields;
        } catch (RuntimeException ex) {
            log.warn(
                    "Impossible de construire les réponses formulaire pour l'UE id={} (fallback métadonnées seules): {}",
                    dto.getId(),
                    ex.toString(),
                    ex);
            return buildFieldsMetadataOnly(fieldSource);
        }
    }

    private Map<String, RecordingUnitFormFieldApi> buildFieldsMetadataOnly(FieldSource fieldSource) {
        Map<String, RecordingUnitFormFieldApi> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) {
                continue;
            }
            fields.put(String.valueOf(field.getId()), toFieldApi(field, null));
        }
        return fields;
    }

    private RecordingUnitFormFieldApi toFieldApi(CustomField field, CustomFieldAnswerViewModel answer) {
        String fieldCode = field instanceof CustomFieldSelectOneFromFieldCode f ? f.getFieldCode() : null;
        return new RecordingUnitFormFieldApi(field.getId(), answerTypeDiscriminator(field), field.getLabel(), field.getHint(), field.getValueBinding(),
                field.getIsSystemField(), fieldCode, formService.readAnswerValueForApi(answer));
    }

    private static String answerTypeDiscriminator(CustomField field) {
        DiscriminatorValue dv = field.getClass().getAnnotation(DiscriminatorValue.class);
        return dv != null ? dv.value() : field.getClass().getSimpleName();
    }

    private Map<String, List<ConceptAutocompleteDTO>> loadVocabularies(FieldSource fieldSource, UserInfo userInfo) {
        Set<String> codes = new LinkedHashSet<>();
        for (CustomField f : fieldSource.getAllFields()) {
            if (f instanceof CustomFieldSelectOneFromFieldCode ff
                    && ff.getFieldCode() != null
                    && !ff.getFieldCode().isBlank()) {
                codes.add(ff.getFieldCode());
            }
        }
        Map<String, List<ConceptAutocompleteDTO>> out = new LinkedHashMap<>();
        for (String code : codes) {
            try {
                out.put(code, fieldConfigurationService.fetchAutocomplete(userInfo, code, null));
            } catch (NoConfigForFieldException e) {
                out.put(code, List.of());
            }
        }
        return out;
    }

    private void applyPersistedCustomAnswers(RecordingUnit entity,
                                             CustomForm effectiveForm,
                                             CustomFormResponseViewModel response,
                                             String lang) {
        CustomFormResponse persisted = entity.getFormResponse();
        if (persisted == null || persisted.getForm() == null || response.getAnswers() == null) {
            return;
        }
        if (!Objects.equals(persisted.getForm().getId(), effectiveForm.getId())) {
            return;
        }
        for (Map.Entry<CustomField, CustomFieldAnswer> e : persisted.getAnswers().entrySet()) {
            CustomField field = e.getKey();
            CustomFieldAnswerViewModel vm = findViewModelForField(response.getAnswers(), field);
            if (vm == null) {
                continue;
            }
            CustomFieldAnswer jpa = e.getValue();
            if (jpa instanceof CustomFieldAnswerInteger jpaInt && vm instanceof CustomFieldAnswerIntegerViewModel vmInt) {
                vmInt.setValue(jpaInt.getValue());
            } else if (jpa instanceof CustomFieldAnswerText jpaText && vm instanceof CustomFieldAnswerTextViewModel vmText) {
                vmText.setValue(jpaText.getValue());
            } else if (jpa instanceof CustomFieldAnswerDateTime jpaDt && vm instanceof CustomFieldAnswerDateTimeViewModel vmDt) {
                vmDt.setValue(jpaDt.getValue());
            } else if (jpa instanceof CustomFieldAnswerSelectOne jpaSel
                    && vm instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel vmFc
                    && jpaSel.getValue() != null) {
                ConceptDTO conceptDto = conceptMapper.convert(jpaSel.getValue());
                String label = conceptDto.getExternalId() != null ? conceptDto.getExternalId() : "";
                vmFc.setValue(new ConceptAutocompleteDTO(conceptDto, label, lang));
            }
        }
    }

    /**
     * Les clés {@link CustomField} du layout et celles de la réponse persistée peuvent être des instances différentes ;
     * on aligne sur {@link CustomField#getId()}.
     */
    private static CustomFieldAnswerViewModel findViewModelForField(Map<CustomField, CustomFieldAnswerViewModel> answers,
                                                                    CustomField field) {
        CustomFieldAnswerViewModel direct = answers.get(field);
        if (direct != null) {
            return direct;
        }
        if (field == null || field.getId() == null) {
            return null;
        }
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> en : answers.entrySet()) {
            CustomField k = en.getKey();
            if (k != null && field.getId().equals(k.getId())) {
                return en.getValue();
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public RecordingUnitRelationsData buildRecordingUnitRelations(String recordingUnitKey,
                                                                  Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.RecordingUnitRelationsBundle bundle =
                recordingUnitService.findRelationsForAccessibleRecordingUnit(recordingUnitKey, accessibleInstitutionIds);
        return new RecordingUnitRelationsData(
                bundle.stratigraphicRelationships(),
                bundle.parents(),
                bundle.children());
    }
}
