package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldCatalogEntry;
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

    private static final List<FieldCatalogEntry> FIELD_CATALOG = buildFieldCatalog();

    private final Map<Long, Map<ConfigurableTable, Map<String, TypeFormConfig>>> formConfigsByProject = new ConcurrentHashMap<>();
    private final Map<Long, Map<ConfigurableTable, Map<String, TypeFieldsConfig>>> fieldsConfigsByProject = new ConcurrentHashMap<>();

    private static List<FieldCatalogEntry> buildFieldCatalog() {
        List<FieldCatalogEntry> catalog = new ArrayList<>();
        catalog.add(catalogEntry("Technique de fabrication", FieldType.SELECT_ONE, "Procédé de façonnage employé."));
        catalog.add(catalogEntry("Type de décor", FieldType.SELECT_ONE, "Nature du décor observé sur la surface."));
        catalog.add(catalogEntry("Nombre de tessons", FieldType.INTEGER, "Décompte des fragments associés."));
        catalog.add(catalogEntry("Diamètre", FieldType.MEASUREMENT, "Diamètre de la pièce."));
        catalog.add(catalogEntry("Couleur", FieldType.SELECT_ONE, "Couleur dominante de l'objet."));
        catalog.add(catalogEntry("Fonction supposée", FieldType.SELECT_ONE, "Usage présumé de l'objet."));
        return catalog;
    }

    private static FieldCatalogEntry catalogEntry(String name, FieldType type, String description) {
        return FieldCatalogEntry.builder().name(name).type(type).description(description).build();
    }

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
    public TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        return copyOf(internalFormConfig(projectId, table, typeName));
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
                .type(FieldType.TEXT)
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

    @Override
    public List<FieldCatalogEntry> searchFieldCatalog(Long projectId, String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(FIELD_CATALOG);
        }
        String needle = query.toLowerCase();
        return FIELD_CATALOG.stream()
                .filter(e -> e.getName().toLowerCase().contains(needle) || e.getDescription().toLowerCase().contains(needle))
                .toList();
    }

    @Override
    public TypeFieldFormConfig createField(Long projectId, ConfigurableTable table, String typeName, String name, FieldType type, String description) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name(name)
                .type(type)
                .description(description)
                .systemField(false)
                .active(true)
                .mandatory(false)
                .institutionLocked(false)
                .configurable(type.isConfigurable())
                .sourceLabel("—")
                .build();
        config.getFields().add(field);
        return copyOf(field);
    }

    @Override
    public TypeFieldFormConfig addExistingField(Long projectId, ConfigurableTable table, String typeName, String catalogFieldName) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        Optional<TypeFieldFormConfig> existing = findField(config, catalogFieldName);
        if (existing.isPresent()) {
            return copyOf(existing.get());
        }
        FieldCatalogEntry entry = FIELD_CATALOG.stream()
                .filter(e -> e.getName().equals(catalogFieldName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown catalog field: " + catalogFieldName));
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name(entry.getName())
                .type(entry.getType())
                .description(entry.getDescription())
                .systemField(false)
                .active(true)
                .mandatory(false)
                .institutionLocked(false)
                .configurable(entry.getType().isConfigurable())
                .sourceLabel("—")
                .build();
        config.getFields().add(field);
        return copyOf(field);
    }

    @Override
    public TypeFieldFormConfig updateField(Long projectId, ConfigurableTable table, String typeName, String fieldName, String newName, FieldType newType, String description) {
        TypeFieldsConfig config = internalFieldsConfig(projectId, table, typeName);
        TypeFieldFormConfig field = findField(config, fieldName)
                .filter(f -> !f.isSystemField())
                .orElse(null);
        if (field == null) {
            return null;
        }
        field.setName(newName);
        field.setType(newType);
        field.setDescription(description);
        field.setConfigurable(newType.isConfigurable());
        return copyOf(field);
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
        fields.add(sysField("Identifiant", FieldType.TEXT, true, true, true, null));
        fields.add(sysField("Code inventaire", FieldType.TEXT, true, true, false, null));
        fields.add(sysField("Désignation", FieldType.TEXT, true, true, false, null));
        fields.add(sysField("Description", FieldType.TEXT, true, false, false, null));
        fields.add(sysField("Catégorie", FieldType.SELECT_ONE, true, false, false, "Thésaurus des catégories"));
        fields.add(sysField("Matériau", FieldType.SELECT_ONE, true, false, false, "Thésaurus des matériaux"));
        fields.add(sysField("Datation", FieldType.TEXT, true, false, false, null));
        fields.add(sysField("Dimensions", FieldType.MEASUREMENT, true, false, false, null));
        fields.add(sysField("Poids", FieldType.MEASUREMENT, true, false, false, null));
        fields.add(sysField("Quantité", FieldType.INTEGER, true, true, false, null));
        fields.add(sysField("État de conservation", FieldType.SELECT_ONE, true, false, false, "Typologie état de conservation"));
        fields.add(sysField("Localisation", FieldType.TEXT, !hideLocalisationAndInventeur, false, false, null));
        fields.add(sysField("Inventeur", FieldType.TEXT, !hideLocalisationAndInventeur, false, false, null));
        fields.add(sysField("Date de découverte", FieldType.TEXT, true, false, false, null));
        fields.add(sysField("Unité d'enregistrement", FieldType.SELECT_ONE_RECORDING_UNIT, true, true, false, null));
        fields.add(sysField("Lieu", FieldType.SELECT_ONE_SPATIAL_UNIT, true, false, false, null));
        fields.add(sysField("Projet", FieldType.PROJET, true, true, true, null));
        fields.add(sysField("Remarques", FieldType.TEXT, true, false, false, null));
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
        fields.add(additionalField("Technique de fabrication", FieldType.SELECT_ONE, false, "Thésaurus des techniques"));
        fields.add(additionalField("Type de décor", FieldType.SELECT_ONE, false, "Typologie des décors céramiques"));
        fields.add(additionalField("Nombre de tessons", FieldType.INTEGER, true, "—"));
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
