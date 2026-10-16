package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerBoolean;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDecimal;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigAnswerRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The attributes of a link itself (its shared form: "uncertain", a comment...): ordinary form answers, in the
 * answer set the link owns ({@code form_config_answer.fk_relation_link_id}), whichever side they are read from.
 * <p>
 * The form is the definition's {@link fr.siamois.domain.models.relation.RelationDefinition#getLinkFormConfig()},
 * fixed (no per-project or per-type variant). Only the scalar field types are supported: a link to another
 * entity is a relation of its own, never an attribute of a link.
 */
@Service
@RequiredArgsConstructor
public class RelationLinkAttributesService {

    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final FormConfigAnswerRepository formConfigAnswerRepository;
    private final CustomFieldAnswerRepository customFieldAnswerRepository;
    private final PersonRepository personRepository;

    /** The active fields of the definition's link form, by id; empty when the definition has no link form. */
    @Transactional(readOnly = true)
    public Map<Long, CustomField> activeFields(RelationLink link) {
        FormConfig config = link.getDefinition().getLinkFormConfig();
        Map<Long, CustomField> fields = new LinkedHashMap<>();
        if (config == null) return fields;
        for (FieldFormConfig fieldConfig : fieldFormConfigRepository.findAllByFormConfigId(config.getId())) {
            if (fieldConfig.isActive()) {
                fields.put(fieldConfig.getField().getId(), fieldConfig.getField());
            }
        }
        return fields;
    }

    /** The stored attribute values of a link, by field id. */
    @Transactional(readOnly = true)
    public Map<Long, Object> read(RelationLink link) {
        FormConfig config = link.getDefinition().getLinkFormConfig();
        Map<Long, Object> values = new HashMap<>();
        if (config == null || link.getId() == null) return values;
        formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link).ifPresent(set -> {
            if (set.getAnswers() == null) return;
            for (CustomFieldAnswer answer : set.getAnswers()) {
                values.put(answer.getCustomField().getId(), answer.getValue());
            }
        });
        return values;
    }

    /**
     * Stores the given attribute values, a {@code null} one deleting the answer. Every field must be active on the
     * link form and given a value of its own type.
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(RelationLink link, Map<Long, Object> values, PersonDTO author) {
        if (values == null || values.isEmpty()) return;
        save(link, values, personRepository.findById(author.getId())
                .orElseThrow(() -> new IllegalStateException("Person " + author.getId() + " not found")));
    }

    /** {@link #save(RelationLink, Map, PersonDTO)} for an author already loaded (an import writes on behalf of a unit's creator). */
    @Transactional(rollbackFor = Exception.class)
    public void save(RelationLink link, Map<Long, Object> values, Person author) {
        if (values == null || values.isEmpty()) return;
        Map<Long, CustomField> fields = activeFields(link);
        for (Map.Entry<Long, Object> entry : values.entrySet()) {
            CustomField field = fields.get(entry.getKey());
            if (field == null) {
                throw new RelationLinkException(RelationLinkException.Reason.INVALID_ATTRIBUTE,
                        "Le champ " + entry.getKey() + " n'est pas un attribut de ce lien");
            }
            checkType(field, entry.getValue());
        }

        FormConfigAnswer set = answerSetOf(link, author);
        for (Map.Entry<Long, Object> entry : values.entrySet()) {
            CustomField field = fields.get(entry.getKey());
            Optional<CustomFieldAnswer> existing = customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(set, field);
            if (entry.getValue() == null) {
                existing.ifPresent(customFieldAnswer -> {
                    if (set.getAnswers() != null) set.getAnswers().remove(customFieldAnswer);
                    customFieldAnswerRepository.delete(customFieldAnswer);
                });
                continue;
            }
            CustomFieldAnswer answer = existing.orElseGet(() -> newAnswer(field, set));
            answer.setValue(normalized(field, entry.getValue()));
            CustomFieldAnswer saved = customFieldAnswerRepository.save(answer);
            // the set is read back in the same transaction (the response of the write): keep it in step
            if (existing.isEmpty() && set.getAnswers() != null) set.getAnswers().add(saved);
        }
    }

    /** Deletes the answers and the answer set of a link that is about to be deleted. */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(RelationLink link) {
        if (link.getId() == null) return;
        for (FormConfigAnswer set : formConfigAnswerRepository.findByRelationLink(link)) {
            if (set.getAnswers() != null) {
                customFieldAnswerRepository.deleteAll(List.copyOf(set.getAnswers()));
            }
            formConfigAnswerRepository.delete(set);
        }
    }

    private FormConfigAnswer answerSetOf(RelationLink link, Person author) {
        FormConfig config = link.getDefinition().getLinkFormConfig();
        return formConfigAnswerRepository.findByFormConfigAndRelationLink(config, link).orElseGet(() -> {
            FormConfigAnswer set = new FormConfigAnswer();
            set.setPerson(author);
            set.setFormConfig(config);
            set.setRelationLink(link);
            set.setAnswers(new HashSet<>());
            return formConfigAnswerRepository.save(set);
        });
    }

    private static CustomFieldAnswer newAnswer(CustomField field, FormConfigAnswer set) {
        CustomFieldAnswer answer = answerOf(field);
        answer.setCustomField(field);
        answer.setFormConfigAnswer(set);
        return answer;
    }

    private static CustomFieldAnswer answerOf(CustomField field) {
        Class<?> type = Hibernate.getClass(field);
        if (CustomFieldBoolean.class.isAssignableFrom(type)) return new CustomFieldAnswerBoolean();
        if (CustomFieldText.class.isAssignableFrom(type)) return new CustomFieldAnswerText();
        if (CustomFieldInteger.class.isAssignableFrom(type)) return new CustomFieldAnswerInteger();
        if (CustomFieldDecimal.class.isAssignableFrom(type)) return new CustomFieldAnswerDecimal();
        throw new RelationLinkException(RelationLinkException.Reason.INVALID_ATTRIBUTE,
                "Le champ " + field.getId() + " n'est pas d'un type supporté pour un lien");
    }

    /** Numbers arrive as whatever the JSON parser made of them; the answers hold an Integer or a Double. */
    private static Object normalized(CustomField field, Object value) {
        Class<?> type = Hibernate.getClass(field);
        if (value instanceof Number number) {
            if (CustomFieldInteger.class.isAssignableFrom(type)) return number.intValue();
            if (CustomFieldDecimal.class.isAssignableFrom(type)) return number.doubleValue();
        }
        return value;
    }

    private static void checkType(CustomField field, Object value) {
        if (value == null) return;
        Class<?> type = Hibernate.getClass(field);
        boolean ok = (CustomFieldBoolean.class.isAssignableFrom(type) && value instanceof Boolean)
                || (CustomFieldText.class.isAssignableFrom(type) && value instanceof String)
                || (CustomFieldInteger.class.isAssignableFrom(type) && value instanceof Number)
                || (CustomFieldDecimal.class.isAssignableFrom(type) && value instanceof Number);
        if (!ok) {
            throw new RelationLinkException(RelationLinkException.Reason.INVALID_ATTRIBUTE,
                    "Valeur invalide pour le champ " + field.getId());
        }
    }
}
