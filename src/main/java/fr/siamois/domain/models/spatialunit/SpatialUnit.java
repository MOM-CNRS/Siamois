package fr.siamois.domain.models.spatialunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.SystemConcepts;
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

    @SuppressWarnings("CopyConstructorMissesField")
    public SpatialUnit (SpatialUnit spatialUnit) {
        name = spatialUnit.getName();
        type = spatialUnit.getType();
        geom = spatialUnit.getGeom();
        placeNumber = spatialUnit.getPlaceNumber();
        validationStatus = ValidationStatus.INCOMPLETE;
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

    @Column(name = "code")
    public String code;

    /** INSEE code of the municipality the place lies in (national report inventory). */
    @Column(name = "insee_code")
    private String inseeCode;

    /** Name of the locality (lieu-dit) the place lies in. */
    @Column(name = "locality_name")
    private String localityName;

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
            .externalId(SystemConcepts.id(TYPE_FIELD_CODE))
            .build();
    // unit name
    @Transient
    @JsonIgnore
    public static final Concept NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.name"))
            .build();

    @Transient
    @JsonIgnore
    public static final Concept CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.code"))
            .build();

    @Transient
    @JsonIgnore
    public static final Concept PLACE_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.placeNumber"))
            .build();

    // National report inventory fields: no concept in the system thesaurus yet.
    @Transient
    @JsonIgnore
    public static final Concept INSEE_CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.inseeCode"))
            .build();

    @Transient
    @JsonIgnore
    public static final Concept LOCALITY_NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.localityName"))
            .build();

    @Transient
    @JsonIgnore
    public static final Concept COMMENTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.comments"))
            .build();

    @Transient
    @JsonIgnore
    public static final Concept SHOM_MAP_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.shomMapNumber"))
            .build();

    // address
    @Transient
    @JsonIgnore
    public static final Concept ADDRESS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId(SystemConcepts.id("SIASU.address"))
            .build();


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
    public static final CustomFieldText CODE_FIELD =  CustomFieldText.builder()
            .label("common.label.code")
            .isSystemField(true)
            .id(-203L)
            .valueBinding("code")
            .concept(CODE_CONCEPT)
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
                            .name("common.header.general")
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
                            .name("common.header.general")
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
                                                    .readOnly(true)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(CODE_FIELD)
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
                            ).build()
            )
            .build();



}
