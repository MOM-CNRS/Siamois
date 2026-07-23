package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory mock implementation of {@link TableFieldConfigService}, seeded with demo data.
 * Intended as a stand-in for the UI while the real persistence layer for table/type/field
 * configuration is designed; state lives only for the lifetime of the application.
 */
@Service
public class MockTableFieldConfigService implements TableFieldConfigService {

    private static final String DEFAULT_TYPE = "_default";

    private static final Map<ConfigurableTable, List<String>> TABLE_TYPES = buildTableTypes();

    /** Types a project explicitly asked a configuration for; every other value uses {@code _default}. */
    private final Map<Long, Map<ConfigurableTable, Set<String>>> configuredTypesByProject = new ConcurrentHashMap<>();

    private final Map<Long, Map<ConfigurableTable, Map<String, TypeFormConfig>>> formConfigsByProject = new ConcurrentHashMap<>();
    private final Map<Long, Map<ConfigurableTable, Map<String, TypeFieldsConfig>>> fieldsConfigsByProject = new ConcurrentHashMap<>();

    private static Map<ConfigurableTable, List<String>> buildTableTypes() {
        Map<ConfigurableTable, List<String>> map = new LinkedHashMap<>();
        map.put(ConfigurableTable.UE, List.of(DEFAULT_TYPE, "Creusement", "Construction", "Dépôt", "Démolition", "Sol"));
        map.put(ConfigurableTable.MOBILIER, List.of(DEFAULT_TYPE, "Céramique", "Lithique", "Métal", "Verre", "Os travaillé", "Monnaie", "Faune"));
        map.put(ConfigurableTable.PHASE, List.of(DEFAULT_TYPE, "Occupation", "Abandon", "Construction", "Destruction"));
        map.put(ConfigurableTable.CONTENANT, List.of(DEFAULT_TYPE, "Caisse", "Sachet", "Boîte", "Palette"));
        return map;
    }

    @Override
    public List<ConfigurableTable> listTables() {
        return new ArrayList<>(TABLE_TYPES.keySet());
    }

    @Override
    public List<TypeSummary> listTypes(Long projectId, ConfigurableTable table) {
        List<TypeSummary> types = new ArrayList<>();
        types.add(new TypeSummary(DEFAULT_TYPE, true));
        configuredTypesOf(projectId, table).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new TypeSummary(name, false))
                .forEach(types::add);
        return types;
    }

    @Override
    public List<String> listConfigurableTypes(Long projectId, ConfigurableTable table, String input) {
        Set<String> configured = configuredTypesOf(projectId, table);
        String needle = input == null ? "" : input.trim().toLowerCase();
        return typeNamesOf(table).stream()
                .filter(name -> !DEFAULT_TYPE.equals(name))
                .filter(name -> !configured.contains(name))
                .filter(name -> name.toLowerCase().contains(needle))
                .toList();
    }

    @Override
    public TypeSummary addConfiguration(Long projectId, ConfigurableTable table, String typeName) {
        if (DEFAULT_TYPE.equals(typeName)) {
            throw new IllegalArgumentException("The default configuration always exists and cannot be added");
        }
        requireType(table, typeName);
        configuredTypesOf(projectId, table).add(typeName);
        return new TypeSummary(typeName, false);
    }

    private Set<String> configuredTypesOf(Long projectId, ConfigurableTable table) {
        return configuredTypesByProject
                .computeIfAbsent(projectId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(table, t -> ConcurrentHashMap.newKeySet());
    }

    @Override
    public TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        return copyOf(internalFormConfig(projectId, table, typeName));
    }

    @Override
    public void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config) {
        requireType(table, config.getTypeName());
        formConfigsOf(projectId, table).put(config.getTypeName(), copyOf(config));
    }

    @Override
    public TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName) {
        return copyOf(internalFieldsConfig(projectId, table, typeName));
    }

    @Override
    public void setFieldActive(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean active) {
        findField(internalFieldsConfig(projectId, table, typeName), fieldName)
                .filter(f -> !f.isInstitutionLocked())
                .ifPresent(f -> f.setActive(active));
    }

    @Override
    public void setFieldMandatory(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean mandatory) {
        findField(internalFieldsConfig(projectId, table, typeName), fieldName)
                .filter(f -> !f.isInstitutionLocked())
                .ifPresent(f -> f.setMandatory(mandatory));
    }

    @Override
    public TypeFieldFormConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name(nextNewFieldName(config))
                .type(FieldType.TEXTE)
                .systemField(false)
                .active(true)
                .mandatory(false)
                .institutionLocked(false)
                .sourceLabel("—")
                .build();
        config.getFields().add(field);
        return copyOf(field);
    }

    @Override
    public void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        internalFieldsConfig(projectId, table, typeName).getFields()
                .removeIf(f -> !f.isSystemField() && f.getName().equals(fieldName));
    }

    private Optional<TypeFieldFormConfig> findField(TypeFieldsConfig config, String fieldName) {
        return config.getFields().stream().filter(f -> f.getName().equals(fieldName)).findFirst();
    }

    private String nextNewFieldName(TypeFieldsConfig config) {
        String base = "Nouveau champ";
        List<String> existing = config.getFields().stream().map(TypeFieldFormConfig::getName).toList();
        if (!existing.contains(base)) return base;
        int suffix = 2;
        while (existing.contains(base + " " + suffix)) suffix++;
        return base + " " + suffix;
    }

    private List<String> typeNamesOf(ConfigurableTable table) {
        List<String> types = TABLE_TYPES.get(table);
        if (types == null) throw new NoSuchElementException("Unknown table: " + table);
        return types;
    }

    private void requireType(ConfigurableTable table, String typeName) {
        if (!typeNamesOf(table).contains(typeName)) {
            throw new NoSuchElementException("Unknown type '" + typeName + "' for table " + table);
        }
    }

    private Map<String, TypeFormConfig> formConfigsOf(Long projectId, ConfigurableTable table) {
        return formConfigsByProject
                .computeIfAbsent(projectId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(table, t -> new ConcurrentHashMap<>());
    }

    private Map<String, TypeFieldsConfig> fieldsConfigsOf(Long projectId, ConfigurableTable table) {
        return fieldsConfigsByProject
                .computeIfAbsent(projectId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(table, t -> new ConcurrentHashMap<>());
    }

    private TypeFormConfig internalFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        requireType(table, typeName);
        return formConfigsOf(projectId, table).computeIfAbsent(typeName, name -> seedFormConfig(table, name));
    }

    private TypeFieldsConfig internalFieldsConfig(Long projectId, ConfigurableTable table, String typeName) {
        requireType(table, typeName);
        return fieldsConfigsOf(projectId, table).computeIfAbsent(typeName, name -> seedFieldsConfig(table, name));
    }

    private TypeFormConfig copyOf(TypeFormConfig source) {
        return source.toBuilder().build();
    }

    private TypeFieldFormConfig copyOf(TypeFieldFormConfig source) {
        return source.toBuilder().build();
    }

    private TypeFieldsConfig copyOf(TypeFieldsConfig source) {
        TypeFieldsConfig copy = new TypeFieldsConfig();
        copy.setFields(source.getFields().stream().map(this::copyOf).collect(Collectors.toCollection(ArrayList::new)));
        return copy;
    }

    private TypeFormConfig seedFormConfig(ConfigurableTable table, String typeName) {
        return TypeFormConfig.builder()
                .typeName(typeName)
                .valueConceptLabel(DEFAULT_TYPE.equals(typeName) ? "" : typeName)
                .description("")
                .inheritsDefaultFields(!DEFAULT_TYPE.equals(typeName))
                .visibleInApp(true)
                .build();
    }

    private TypeFieldsConfig seedFieldsConfig(ConfigurableTable table, String typeName) {
        TypeFieldsConfig config = new TypeFieldsConfig();
        List<TypeFieldFormConfig> fields = new ArrayList<>(seedSystemFields(table, typeName));
        fields.addAll(seedAdditionalFields(table, typeName));
        config.setFields(fields);
        return config;
    }

    private List<TypeFieldFormConfig> seedSystemFields(ConfigurableTable table, String typeName) {
        boolean hideLocalisationAndInventeur = table == ConfigurableTable.MOBILIER && "Céramique".equals(typeName);
        List<TypeFieldFormConfig> fields = new ArrayList<>();
        fields.add(sysField("Identifiant", FieldType.TEXTE, true, true, true, null));
        fields.add(sysField("Code inventaire", FieldType.TEXTE, true, true, false, null));
        fields.add(sysField("Désignation", FieldType.TEXTE, true, true, false, null));
        fields.add(sysField("Description", FieldType.TEXTE, true, false, false, null));
        fields.add(sysField("Catégorie", FieldType.VOCABULAIRE_CONTROLE, true, false, false, "Thésaurus des catégories"));
        fields.add(sysField("Matériau", FieldType.VOCABULAIRE_CONTROLE, true, false, false, "Thésaurus des matériaux"));
        fields.add(sysField("Datation", FieldType.TEXTE, true, false, false, null));
        fields.add(sysField("Dimensions", FieldType.MESURE, true, false, false, null));
        fields.add(sysField("Poids", FieldType.MESURE, true, false, false, null));
        fields.add(sysField("Quantité", FieldType.NUMERIQUE, true, true, false, null));
        fields.add(sysField("État de conservation", FieldType.TYPOLOGIE, true, false, false, "Typologie état de conservation"));
        fields.add(sysField("Localisation", FieldType.TEXTE, !hideLocalisationAndInventeur, false, false, null));
        fields.add(sysField("Inventeur", FieldType.TEXTE, !hideLocalisationAndInventeur, false, false, null));
        fields.add(sysField("Date de découverte", FieldType.TEXTE, true, false, false, null));
        fields.add(sysField("Unité d'enregistrement", FieldType.UNITE_ENREGISTREMENT, true, true, false, null));
        fields.add(sysField("Lieu", FieldType.LIEU, true, false, false, null));
        fields.add(sysField("Projet", FieldType.PROJET, true, true, true, null));
        fields.add(sysField("Remarques", FieldType.TEXTE, true, false, false, null));
        return fields;
    }

    private TypeFieldFormConfig sysField(String name, FieldType type, boolean active, boolean mandatory, boolean institutionLocked, String sourceLabel) {
        return TypeFieldFormConfig.builder()
                .name(name)
                .type(type)
                .systemField(true)
                .active(active)
                .mandatory(mandatory)
                .institutionLocked(institutionLocked)
                .configurable(type.isConfigurable())
                .sourceLabel(sourceLabel)
                .build();
    }

    private List<TypeFieldFormConfig> seedAdditionalFields(ConfigurableTable table, String typeName) {
        if (table != ConfigurableTable.MOBILIER || !"Céramique".equals(typeName)) {
            return new ArrayList<>();
        }
        List<TypeFieldFormConfig> fields = new ArrayList<>();
        fields.add(additionalField("Technique de fabrication", FieldType.VOCABULAIRE_CONTROLE, false, "Thésaurus des techniques"));
        fields.add(additionalField("Type de décor", FieldType.TYPOLOGIE, false, "Typologie des décors céramiques"));
        fields.add(additionalField("Nombre de tessons", FieldType.NUMERIQUE, true, "—"));
        fields.add(additionalField("Remontage", FieldType.PARENTS, false, "Céramique"));
        return fields;
    }

    private TypeFieldFormConfig additionalField(String name, FieldType type, boolean mandatory, String sourceLabel) {
        return TypeFieldFormConfig.builder()
                .name(name)
                .type(type)
                .systemField(false)
                .active(true)
                .mandatory(mandatory)
                .institutionLocked(false)
                .configurable(type.isConfigurable())
                .sourceLabel(sourceLabel)
                .build();
    }
}
