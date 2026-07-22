package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.AdditionalFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.SystemFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeGeneralConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    private final Map<Long, Map<ConfigurableTable, Map<String, TypeGeneralConfig>>> generalConfigsByProject = new ConcurrentHashMap<>();
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
        return typeNamesOf(table).stream()
                .map(name -> new TypeSummary(name, DEFAULT_TYPE.equals(name)))
                .toList();
    }

    @Override
    public TypeGeneralConfig getGeneralConfig(Long projectId, ConfigurableTable table, String typeName) {
        return copyOf(internalGeneralConfig(projectId, table, typeName));
    }

    @Override
    public void saveGeneralConfig(Long projectId, ConfigurableTable table, TypeGeneralConfig config) {
        requireType(table, config.getTypeName());
        generalConfigsOf(projectId, table).put(config.getTypeName(), copyOf(config));
    }

    @Override
    public TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName) {
        return copyOf(internalFieldsConfig(projectId, table, typeName));
    }

    @Override
    public void setSystemFieldVisible(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean visible) {
        internalFieldsConfig(projectId, table, typeName).getSystemFields().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .ifPresent(f -> f.setVisible(visible));
    }

    @Override
    public void setFieldRequired(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean isSystemField, boolean required) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        if (isSystemField) {
            config.getSystemFields().stream()
                    .filter(f -> f.getName().equals(fieldName))
                    .findFirst()
                    .ifPresent(f -> f.setRequired(required));
        } else {
            config.getAdditionalFields().stream()
                    .filter(f -> f.getName().equals(fieldName))
                    .findFirst()
                    .ifPresent(f -> f.setRequired(required));
        }
    }

    @Override
    public AdditionalFieldConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        AdditionalFieldConfig field = AdditionalFieldConfig.builder()
                .name(nextNewFieldName(config))
                .type(FieldType.TEXTE)
                .required(false)
                .sourceLabel("—")
                .build();
        config.getAdditionalFields().add(field);
        return copyOf(field);
    }

    @Override
    public void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        internalFieldsConfig(projectId, table, typeName).getAdditionalFields()
                .removeIf(f -> f.getName().equals(fieldName));
    }

    private TypeGeneralConfig internalGeneralConfig(Long projectId, ConfigurableTable table, String typeName) {
        requireType(table, typeName);
        return generalConfigsOf(projectId, table).computeIfAbsent(typeName, name -> seedGeneralConfig(table, name));
    }

    private TypeFieldsConfig internalFieldsConfig(Long projectId, ConfigurableTable table, String typeName) {
        requireType(table, typeName);
        return fieldsConfigsOf(projectId, table).computeIfAbsent(typeName, name -> seedFieldsConfig(table, name));
    }

    private TypeGeneralConfig copyOf(TypeGeneralConfig source) {
        return source.toBuilder().build();
    }

    private SystemFieldConfig copyOf(SystemFieldConfig source) {
        return source.toBuilder().build();
    }

    private AdditionalFieldConfig copyOf(AdditionalFieldConfig source) {
        return source.toBuilder().build();
    }

    private TypeFieldsConfig copyOf(TypeFieldsConfig source) {
        TypeFieldsConfig copy = new TypeFieldsConfig();
        copy.setSystemFields(source.getSystemFields().stream().map(this::copyOf).collect(Collectors.toCollection(ArrayList::new)));
        copy.setAdditionalFields(source.getAdditionalFields().stream().map(this::copyOf).collect(Collectors.toCollection(ArrayList::new)));
        return copy;
    }

    private String nextNewFieldName(TypeFieldsConfig config) {
        String base = "Nouveau champ";
        List<String> existing = config.getAdditionalFields().stream().map(AdditionalFieldConfig::getName).toList();
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

    private Map<String, TypeGeneralConfig> generalConfigsOf(Long projectId, ConfigurableTable table) {
        return generalConfigsByProject
                .computeIfAbsent(projectId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(table, t -> new ConcurrentHashMap<>());
    }

    private Map<String, TypeFieldsConfig> fieldsConfigsOf(Long projectId, ConfigurableTable table) {
        return fieldsConfigsByProject
                .computeIfAbsent(projectId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(table, t -> new ConcurrentHashMap<>());
    }

    private TypeGeneralConfig seedGeneralConfig(ConfigurableTable table, String typeName) {
        return TypeGeneralConfig.builder()
                .typeName(typeName)
                .linkedConceptLabel("")
                .description("")
                .inheritsDefaultFields(!DEFAULT_TYPE.equals(typeName))
                .visibleInApp(true)
                .build();
    }

    private TypeFieldsConfig seedFieldsConfig(ConfigurableTable table, String typeName) {
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setSystemFields(seedSystemFields(table, typeName));
        config.setAdditionalFields(seedAdditionalFields(table, typeName));
        return config;
    }

    private List<SystemFieldConfig> seedSystemFields(ConfigurableTable table, String typeName) {
        boolean hideLocalisationAndInventeur = table == ConfigurableTable.MOBILIER && "Céramique".equals(typeName);
        List<SystemFieldConfig> fields = new ArrayList<>();
        fields.add(sysField("Identifiant", FieldType.TEXTE, true, true, null));
        fields.add(sysField("Code inventaire", FieldType.TEXTE, true, true, null));
        fields.add(sysField("Désignation", FieldType.TEXTE, true, true, null));
        fields.add(sysField("Description", FieldType.TEXTE, true, false, null));
        fields.add(sysField("Catégorie", FieldType.VOCABULAIRE_CONTROLE, true, false, "Thésaurus des catégories"));
        fields.add(sysField("Matériau", FieldType.VOCABULAIRE_CONTROLE, true, false, "Thésaurus des matériaux"));
        fields.add(sysField("Datation", FieldType.TEXTE, true, false, null));
        fields.add(sysField("Dimensions", FieldType.MESURE, true, false, null));
        fields.add(sysField("Poids", FieldType.MESURE, true, false, null));
        fields.add(sysField("Quantité", FieldType.NUMERIQUE, true, true, null));
        fields.add(sysField("État de conservation", FieldType.TYPOLOGIE, true, false, "Typologie état de conservation"));
        fields.add(sysField("Localisation", FieldType.TEXTE, !hideLocalisationAndInventeur, false, null));
        fields.add(sysField("Inventeur", FieldType.TEXTE, !hideLocalisationAndInventeur, false, null));
        fields.add(sysField("Date de découverte", FieldType.TEXTE, true, false, null));
        fields.add(sysField("Unité d'enregistrement", FieldType.UNITE_ENREGISTREMENT, true, true, null));
        fields.add(sysField("Lieu", FieldType.LIEU, true, false, null));
        fields.add(sysField("Projet", FieldType.PROJET, true, true, null));
        fields.add(sysField("Remarques", FieldType.TEXTE, true, false, null));
        return fields;
    }

    private SystemFieldConfig sysField(String name, FieldType type, boolean visible, boolean required, String sourceLabel) {
        return SystemFieldConfig.builder()
                .name(name)
                .type(type)
                .visible(visible)
                .required(required)
                .configurable(type.isConfigurable())
                .sourceLabel(sourceLabel)
                .build();
    }

    private List<AdditionalFieldConfig> seedAdditionalFields(ConfigurableTable table, String typeName) {
        if (table != ConfigurableTable.MOBILIER || !"Céramique".equals(typeName)) {
            return new ArrayList<>();
        }
        List<AdditionalFieldConfig> fields = new ArrayList<>();
        fields.add(AdditionalFieldConfig.builder()
                .name("Technique de fabrication").type(FieldType.VOCABULAIRE_CONTROLE).required(false)
                .sourceLabel("Thésaurus des techniques").build());
        fields.add(AdditionalFieldConfig.builder()
                .name("Type de décor").type(FieldType.TYPOLOGIE).required(false)
                .sourceLabel("Typologie des décors céramiques").build());
        fields.add(AdditionalFieldConfig.builder()
                .name("Nombre de tessons").type(FieldType.NUMERIQUE).required(true)
                .sourceLabel("—").build());
        fields.add(AdditionalFieldConfig.builder()
                .name("Remontage").type(FieldType.PARENTS).required(false)
                .sourceLabel("Céramique").build());
        return fields;
    }
}
