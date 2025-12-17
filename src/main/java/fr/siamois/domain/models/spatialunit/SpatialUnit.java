package fr.siamois.domain.models.spatialunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;
import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

@Data
@Entity
@Table(name = "spatial_unit")
@SQLRestriction("fk_parent_action_unit_id IS NULL")
@Audited
public class SpatialUnit extends SpatialUnitGeneric implements ArkEntity {

    @SuppressWarnings("CopyConstructorMissesField")
    public SpatialUnit (SpatialUnit spatialUnit) {
        name = spatialUnit.getName();
        ark = spatialUnit.getArk();
        category = spatialUnit.getCategory();
        geom = spatialUnit.getGeom();
        validated = spatialUnit.getValidated();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spatial_unit_id", nullable = false)
    private Long id;

    @FieldCode
    public static final String CATEGORY_FIELD_CODE = "SIASU.TYPE";

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "spatial_unit_document",
            joinColumns = { @JoinColumn(name = "fk_spatial_unit_id")},
            inverseJoinColumns = { @JoinColumn(name = "fk_document_id") }
    )
    @JsonIgnore
    private Set<Document> documents = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinTable(
            name="spatial_hierarchy",
            joinColumns = { @JoinColumn(name = "fk_parent_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_child_id") }
    )
    @NotAudited
    private Set<SpatialUnit> children = new HashSet<>();

    @OneToMany(mappedBy="spatialUnit")
    @JsonIgnore
    private Set<RecordingUnit> recordingUnitList;

    @ManyToMany(mappedBy = "children", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<SpatialUnit> parents = new HashSet<>();

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
        return Objects.equals(id, that.id);  // Compare based on RecordingUnit
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);  // Hash based on RecordingUnit
    }

    @Transient
    @JsonIgnore
    public List<String> getBindableFieldNames() {
        return List.of("category", "name");
    }

    // ----------- Concepts for system fields


    // uni category
    @Transient
    @JsonIgnore
    public static final Concept SPATIAL_UNIT_TYPE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282365")
            .build();
    // unit name
    @Transient
    @JsonIgnore
    public static final Concept NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4285848")
            .build();


    // --------------- Fields


    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode SPATIAL_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .id(1L)
            .isSystemField(true)
            .valueBinding("category")
            .styleClass("mr-2 spatial-unit-type-chip")
            .iconClass("bi bi-geo-alt")
            .fieldCode(SpatialUnit.CATEGORY_FIELD_CODE)
            .concept(SPATIAL_UNIT_TYPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText NAME_FIELD = new CustomFieldText.Builder()
            .label("common.label.name")
            .isSystemField(true)
            .valueBinding("name")
            .concept(NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SPATIAL_UNIT_TYPE_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm OVERVIEW_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .isRequired(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SPATIAL_UNIT_TYPE_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(SPATIAL_UNIT_TYPE_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();


}