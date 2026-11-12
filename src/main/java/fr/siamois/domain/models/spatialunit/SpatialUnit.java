package fr.siamois.domain.models.spatialunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldGeometry;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.attributeconverter.FullAddressConverter;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.envers.Audited;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static fr.siamois.ui.form.FormLayoutConstants.COLUMN_CLASS_NAME;
import static fr.siamois.ui.form.FormLayoutConstants.SYSTEM_THESO;

@Data
@Entity
@Table(name = "spatial_unit", indexes = {
        @Index(columnList = "name", name = "idx_spatial_unit_name"),
        @Index(columnList = "fk_institution_id", name = "idx_spatial_unit_institution")
})
@Audited
public class SpatialUnit extends TraceableEntity implements ArkEntity {

    /**
     * Creates a new, unsaved spatial unit from the descriptive data of {@code spatialUnit}: its name, type,
     * geometry and place number. Everything else (identity, hierarchy, links, address and cadastral data,
     * comments) is deliberately reset, and the copy starts as {@link ValidationStatus#INCOMPLETE}.
     *
     * @param spatialUnit the spatial unit to copy
     */
    public SpatialUnit (SpatialUnit spatialUnit) {
        name = spatialUnit.getName();
        type = spatialUnit.getType();
        geom = spatialUnit.getGeom();
        placeNumber = spatialUnit.getPlaceNumber();
        validationStatus = ValidationStatus.INCOMPLETE;

        // Intentional resets: these fields are not part of a copy.
        id = null;
        children = new HashSet<>();
        parents = new HashSet<>();
        recordingUnitList = null;
        ark = null;
        address = null;
        inseeCode = null;
        localityName = null;
        addressStreet = null;
        addressPostcode = null;
        addressCity = null;
        parcelSection = null;
        parcelNumber = null;
        comments = null;
        shomMapNumber = null;
        relatedActionUnitList = new HashSet<>();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spatial_unit_id", nullable = false)
    private Long id;

    @FieldCode
    public static final String TYPE_FIELD_CODE = "SIASU.TYPE";

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinTable(
            name="spatial_hierarchy",
            joinColumns = { @JoinColumn(name = "fk_parent_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_child_id") }
    )
    private Set<SpatialUnit> children = new HashSet<>();

    @ManyToMany(mappedBy = "children", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<SpatialUnit> parents = new HashSet<>();

    @OneToMany(mappedBy="spatialUnit")
    @JsonIgnore
    private Set<RecordingUnit> recordingUnitList;

    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    protected String name;

    @OneToOne
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @ManyToOne
    @JoinColumn(name = "fk_concept_type_id")
    protected Concept type;

    @Column(name="geom",columnDefinition = "geometry")
    @JsonIgnore
    protected MultiPolygon geom;

    @Column(name = "address", columnDefinition = "jsonb")
    @Convert(converter = FullAddressConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    public FullAddress address;

    /** INSEE code of the municipality the place lies in (national report inventory). */
    @Column(name = "insee_code")
    private String inseeCode;

    /** Name of the locality (lieu-dit) the place lies in. */
    @Column(name = "locality_name")
    private String localityName;

    /** Street line of the place's address; the export concatenates it with the postcode and the city. */
    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_postcode")
    private String addressPostcode;

    /** Name of the municipality: the address city of an address, the municipality of a parcel. */
    @Column(name = "address_city")
    private String addressCity;

    /** Cadastral section of a parcel (e.g. AZ). */
    @Column(name = "parcel_section")
    private String parcelSection;

    /** Number of a parcel in its cadastral section (e.g. 128). */
    @Column(name = "parcel_number")
    private String parcelNumber;

    @Column(name = "comments", length = 5000)
    private String comments;

    /** Number of the SHOM chart used to locate an underwater place. */
    @Column(name = "shom_map_number")
    private String shomMapNumber;

    /** Manually assigned grouping number; deliberately not unique. */
    @Column(name = "place_number")
    private Integer placeNumber;

    @ManyToMany(mappedBy = "spatialContext")
    @JsonIgnore
    private Set<ActionUnit> relatedActionUnitList = new HashSet<>();

    public SpatialUnit() {

    }

    @Override
    public String toString() {
        return String.format("Spatial unit n°%s : %s", id, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpatialUnit that = (SpatialUnit) o;
        return Objects.equals(id, that.id);  // Compare based on id
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);  // Hash based on id
    }


    // ----------- Concepts for system fields


    // uni category
    @Transient
    @JsonIgnore
    public static final Concept SPATIAL_UNIT_TYPE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1154")
            .build();
    // unit name
    @Transient
    @JsonIgnore
    public static final Concept NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1155")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept PLACE_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1157")
            .build();

    // National report inventory fields: no concept in the system thesaurus yet.
    @Transient
    @JsonIgnore
    public static final Concept INSEE_CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1158")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept LOCALITY_NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1159")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept COMMENTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1163")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept SHOM_MAP_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1160")
            .build();

    // Flat address and parcel components.
    @Transient
    @JsonIgnore
    public static final Concept ADDRESS_STREET_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4291354")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept ADDRESS_POSTCODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4291355")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept ADDRESS_CITY_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4291356")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept PARCEL_SECTION_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4291357")
            .build();

    @Transient
    @JsonIgnore
    public static final Concept PARCEL_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4291358")
            .build();

    // address
    @Transient
    @JsonIgnore
    public static final Concept ADDRESS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1161")
            .build();


    // geometry
    @Transient
    @JsonIgnore
    public static final Concept GEOM_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1162")
            .build();

    private static final String GENERAL_PANEL = "common.header.general";

    // --------------- Fields


    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode SPATIAL_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.type")
            .id(-201L)
            .isSystemField(true)
            .valueBinding("type")
            .styleClass("mr-2 spatial-unit-type-chip")
            .iconClass("bi bi-geo-alt")
            .fieldCode(SpatialUnit.TYPE_FIELD_CODE)
            .concept(SPATIAL_UNIT_TYPE_CONCEPT)
            .build();



    @Transient
    @JsonIgnore
    public static final CustomFieldText NAME_FIELD =  CustomFieldText.builder()
            .label("common.label.name")
            .isSystemField(true)
            .id(-202L)
            .valueBinding("name")
            .concept(NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldInteger PLACE_NUMBER_FIELD = CustomFieldInteger.builder()
            .label("spatialUnit.field.placeNumber")
            .isSystemField(true)
            .id(-205L)
            .valueBinding("placeNumber")
            .concept(PLACE_NUMBER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText INSEE_CODE_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.inseeCode")
            .isSystemField(true)
            .id(-206L)
            .valueBinding("inseeCode")
            .concept(INSEE_CODE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText LOCALITY_NAME_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.localityName")
            .isSystemField(true)
            .id(-207L)
            .valueBinding("localityName")
            .concept(LOCALITY_NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText COMMENTS_FIELD = CustomFieldText.builder()
            .label("common.field.comments")
            .isSystemField(true)
            .id(-208L)
            .isTextArea(true)
            .valueBinding("comments")
            .concept(COMMENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText SHOM_MAP_NUMBER_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.shomMapNumber")
            .isSystemField(true)
            .id(-209L)
            .valueBinding("shomMapNumber")
            .concept(SHOM_MAP_NUMBER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText ADDRESS_STREET_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.addressStreet")
            .isSystemField(true)
            .id(-211L)
            .valueBinding("addressStreet")
            .concept(ADDRESS_STREET_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText ADDRESS_POSTCODE_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.addressPostcode")
            .isSystemField(true)
            .id(-212L)
            .valueBinding("addressPostcode")
            .concept(ADDRESS_POSTCODE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText ADDRESS_CITY_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.addressCity")
            .isSystemField(true)
            .id(-213L)
            .valueBinding("addressCity")
            .concept(ADDRESS_CITY_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText PARCEL_SECTION_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.parcelSection")
            .isSystemField(true)
            .id(-214L)
            .valueBinding("parcelSection")
            .concept(PARCEL_SECTION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText PARCEL_NUMBER_FIELD = CustomFieldText.builder()
            .label("spatialUnit.field.parcelNumber")
            .isSystemField(true)
            .id(-215L)
            .valueBinding("parcelNumber")
            .concept(PARCEL_NUMBER_CONCEPT)
            .build();

    /** The place's geometry, shown as WKT: it is displayed by the form, never edited through it. */
    @Transient
    @JsonIgnore
    public static final CustomFieldGeometry GEOM_FIELD = CustomFieldGeometry.of(-210L, GEOM_CONCEPT);

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneAddress ADDRESS_FIELD =  CustomFieldSelectOneAddress.builder()
            .label("common.label.address")
            .isSystemField(true)
            .valueBinding("address")
            .concept(ADDRESS_CONCEPT)
            .id(-204L)
            .build();

    @Transient
    @JsonIgnore
    public static final FormUiDto NEW_UNIT_FORM = new FormUiDto.Builder()
            .addPanel(
                    new CustomFormPanelUiDto.Builder()
                            .name(GENERAL_PANEL)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRowUiDto.Builder()
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SPATIAL_UNIT_TYPE_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ADDRESS_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();


    @Transient
    @JsonIgnore
    public static final FormUiDto DETAILS_FORM = new FormUiDto.Builder()
            .addPanel(
                    new CustomFormPanelUiDto.Builder()
                            .name(GENERAL_PANEL)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRowUiDto.Builder()
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SPATIAL_UNIT_TYPE_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ADDRESS_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(PLACE_NUMBER_FIELD)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRowUiDto.Builder()
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(INSEE_CODE_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(LOCALITY_NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SHOM_MAP_NUMBER_FIELD)
                                                    .build())
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(false)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(COMMENTS_FIELD)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRowUiDto.Builder()
                                            .addColumn(textColumn(ADDRESS_STREET_FIELD))
                                            .addColumn(textColumn(ADDRESS_POSTCODE_FIELD))
                                            .addColumn(textColumn(ADDRESS_CITY_FIELD))
                                            .addColumn(textColumn(PARCEL_SECTION_FIELD))
                                            .addColumn(textColumn(PARCEL_NUMBER_FIELD))
                                            .build()
                            ).addRow(
                                    new CustomRowUiDto.Builder()
                                            .addColumn(new CustomColUiDto.Builder()
                                                    .readOnly(true)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(GEOM_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    private static CustomColUiDto textColumn(CustomField field) {
        return new CustomColUiDto.Builder()
                .readOnly(false)
                .isRequired(false)
                .className(COLUMN_CLASS_NAME)
                .field(field)
                .build();
    }

    private static CustomFormPanelUiDto.Builder generalPanel(CustomField... firstRow) {
        CustomRowUiDto.Builder row = new CustomRowUiDto.Builder();
        for (CustomField field : firstRow) {
            boolean required = field == NAME_FIELD || field == SPATIAL_UNIT_TYPE_FIELD;
            row.addColumn(new CustomColUiDto.Builder()
                    .readOnly(false)
                    .isRequired(required)
                    .className(COLUMN_CLASS_NAME)
                    .field(field)
                    .build());
        }
        return new CustomFormPanelUiDto.Builder().name(GENERAL_PANEL).isSystemPanel(true).addRow(row.build());
    }

    private static CustomRowUiDto rowOf(CustomField... fields) {
        CustomRowUiDto.Builder row = new CustomRowUiDto.Builder();
        for (CustomField field : fields) {
            row.addColumn(field == GEOM_FIELD
                    ? new CustomColUiDto.Builder().readOnly(true).isRequired(false).className(COLUMN_CLASS_NAME).field(field).build()
                    : textColumn(field));
        }
        return row.build();
    }

    private static final String SYSTEM_THESAURUS_COMMUNE = "1164";
    private static final String SYSTEM_THESAURUS_PARCEL = "1165";
    private static final String SYSTEM_THESAURUS_ADDRESS = "1167";

    @Transient
    @JsonIgnore
    private static final FormUiDto COMMUNE_FORM = new FormUiDto.Builder()
            .addPanel(generalPanel(NAME_FIELD, SPATIAL_UNIT_TYPE_FIELD, PLACE_NUMBER_FIELD)
                    .addRow(rowOf(INSEE_CODE_FIELD, COMMENTS_FIELD))
                    .addRow(rowOf(GEOM_FIELD))
                    .build())
            .build();

    @Transient
    @JsonIgnore
    private static final FormUiDto PARCEL_FORM = new FormUiDto.Builder()
            .addPanel(generalPanel(NAME_FIELD, SPATIAL_UNIT_TYPE_FIELD, PLACE_NUMBER_FIELD)
                    .addRow(rowOf(PARCEL_SECTION_FIELD, PARCEL_NUMBER_FIELD, INSEE_CODE_FIELD, ADDRESS_CITY_FIELD))
                    .addRow(rowOf(LOCALITY_NAME_FIELD, COMMENTS_FIELD))
                    .addRow(rowOf(GEOM_FIELD))
                    .build())
            .build();

    @Transient
    @JsonIgnore
    private static final FormUiDto ADDRESS_FORM = new FormUiDto.Builder()
            .addPanel(generalPanel(NAME_FIELD, SPATIAL_UNIT_TYPE_FIELD, PLACE_NUMBER_FIELD)
                    .addRow(rowOf(ADDRESS_STREET_FIELD, ADDRESS_POSTCODE_FIELD, ADDRESS_CITY_FIELD, INSEE_CODE_FIELD))
                    .addRow(rowOf(LOCALITY_NAME_FIELD, COMMENTS_FIELD))
                    .addRow(rowOf(GEOM_FIELD))
                    .build())
            .build();

    /**
     * The details form of a place of the given type (external id in the system thesaurus): a commune has no
     * address, a parcel has a cadastral reference, an address has its components. Any other type keeps the full
     * {@link #DETAILS_FORM}, which is also the catalogue of every field.
     */
    public static FormUiDto detailsFormFor(String typeExternalId) {
        if (typeExternalId == null) {
            return DETAILS_FORM;
        }
        return switch (typeExternalId) {
            case SYSTEM_THESAURUS_COMMUNE -> COMMUNE_FORM;
            case SYSTEM_THESAURUS_PARCEL -> PARCEL_FORM;
            case SYSTEM_THESAURUS_ADDRESS -> ADDRESS_FORM;
            default -> DETAILS_FORM;
        };
    }



}
