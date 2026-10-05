package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Retrouve les champs désignés par un concept dans un modèle d'export : un champ système (aligné sur
 * le thésaurus système) ou un champ additionnel actif dans la configuration du projet. Un concept
 * peut être partagé par plusieurs champs additionnels (un par type) : on renvoie tous les candidats,
 * la lecture d'une ligne prend la première réponse trouvée.
 *
 * <p>Une référence qui ne se résout pas donne une liste vide, jamais une exception : l'export affiche
 * un avertissement et laisse la colonne vide.
 */
@Service
@RequiredArgsConstructor
public class ExportFieldResolver {

    private final TableFieldConfigService tableFieldConfigService;
    private final ConceptRepository conceptRepository;

    /**
     * @param subject   l'entité dont on lit le champ
     * @param ref       le concept du champ
     * @param projectId le projet pour lequel on résout les champs additionnels
     * @param typeRefs  types (concepts) de la source ; vide = tous les types configurés du projet
     * @return les champs candidats, système d'abord ; vide si le concept n'est pas résolu
     */
    @Transactional(readOnly = true)
    public List<CustomField> resolve(ExportSubject subject, ConceptRef ref, Long projectId, List<ConceptRef> typeRefs) {
        List<CustomField> system = systemFields(subject).stream()
                .filter(f -> matches(f, ref))
                .toList();
        if (!system.isEmpty()) {
            return system;
        }
        ConfigurableTable table = subject.table();
        if (table == null) {
            return List.of();
        }
        Map<Long, CustomField> additional = new LinkedHashMap<>();
        for (Concept type : typeConcepts(table, projectId, typeRefs)) {
            for (CustomField f : tableFieldConfigService.getActiveAdditionalFields(projectId, table, type.getId())) {
                if (matches(f, ref)) {
                    additional.putIfAbsent(f.getId(), f);
                }
            }
        }
        return new ArrayList<>(additional.values());
    }

    /** Un champ qu'une colonne peut lire : son libellé (clé de message pour un champ système) et son concept. */
    public record FieldOption(String label, boolean system, ConceptRef concept, boolean measurement) {

        public FieldOption(String label, boolean system, ConceptRef concept) {
            this(label, system, concept, false);
        }
    }

    /**
     * Champs qu'un modèle peut référencer pour une entité d'un projet : les champs système, puis les champs
     * additionnels actifs des types configurés du projet. Seuls les champs alignés sur un concept sont
     * proposés (c'est la clé d'un champ dans un modèle) ; deux champs de même concept ne donnent qu'une option.
     */
    @Transactional(readOnly = true)
    public List<FieldOption> listFields(ExportSubject subject, @Nullable Long projectId) {
        Map<String, FieldOption> options = new LinkedHashMap<>();
        for (CustomField f : systemFields(subject)) {
            addOption(options, f, true);
        }
        ConfigurableTable table = subject.table();
        if (table != null && projectId != null) {
            for (Concept type : tableFieldConfigService.listConfiguredTypeConcepts(projectId, table)) {
                for (CustomField f : tableFieldConfigService.getActiveAdditionalFields(projectId, table, type.getId())) {
                    addOption(options, f, false);
                }
            }
        }
        return List.copyOf(options.values());
    }

    private static void addOption(Map<String, FieldOption> options, CustomField field, boolean system) {
        Concept c = field.getConcept();
        if (c == null || c.getVocabulary() == null || c.getExternalId() == null
                || c.getVocabulary().getExternalVocabularyId() == null) {
            return;
        }
        ConceptRef ref = new ConceptRef(c.getVocabulary().getExternalVocabularyId(), c.getExternalId(), c.getUri());
        String key = (ref.thesaurusId() + "|" + ref.conceptId()).toLowerCase(java.util.Locale.ROOT);
        options.putIfAbsent(key, new FieldOption(field.getLabel(), system, ref,
                field instanceof fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement));
    }

    /** Champs système lisibles pour une entité. */
    public List<CustomField> systemFields(ExportSubject subject) {
        return switch (subject) {
            case PROJECT -> fieldsOf(ActionUnit.DETAILS_FORM);
            case SPATIAL_UNIT -> fieldsOf(SpatialUnit.DETAILS_FORM);
            default -> SystemFieldCatalog.sharedFieldsOf(Objects.requireNonNull(subject.table()));
        };
    }

    private List<Concept> typeConcepts(ConfigurableTable table, Long projectId, List<ConceptRef> typeRefs) {
        if (typeRefs.isEmpty()) {
            return tableFieldConfigService.listConfiguredTypeConcepts(projectId, table);
        }
        return typeRefs.stream()
                .map(r -> conceptRepository.findConceptByExternalIdIgnoreCase(r.thesaurusId(), r.conceptId()))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private static List<CustomField> fieldsOf(FormUiDto form) {
        return form.getLayout().stream()
                .map(CustomFormPanelUiDto::getRows)
                .flatMap(List::stream)
                .map(CustomRowUiDto::getColumns)
                .flatMap(List::stream)
                .map(CustomColUiDto::getField)
                .filter(Objects::nonNull)
                .toList();
    }

    static boolean matches(CustomField field, ConceptRef ref) {
        Concept c = field.getConcept();
        return c != null
                && c.getVocabulary() != null
                && c.getExternalId() != null
                && c.getExternalId().equalsIgnoreCase(ref.conceptId())
                && ref.thesaurusId().equalsIgnoreCase(c.getVocabulary().getExternalVocabularyId());
    }
}
