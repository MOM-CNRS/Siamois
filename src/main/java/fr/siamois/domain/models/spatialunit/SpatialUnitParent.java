package fr.siamois.domain.models.spatialunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Objects;


@Data
@MappedSuperclass
@Audited
public abstract class SpatialUnitParent extends TraceableEntity {

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    protected String name;

    @NotNull
    @OneToOne
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @ManyToOne
    @JoinColumn(name = "fk_concept_category_id")
    protected Concept category;

    @Column(name="geom",columnDefinition = "geometry")
    @JsonIgnore
    protected MultiPolygon geom;

    @ManyToOne
    @JoinColumn(name = "fk_parent_action_unit_id")
    @JsonIgnore
    protected ActionUnit parentActionUnit;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SpatialUnitParent that = (SpatialUnitParent) o;
        return Objects.equals(category, that.category)
                && Objects.equals(geom, that.geom)
                && Objects.equals(parentActionUnit, that.parentActionUnit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, geom, parentActionUnit);
    }
}
