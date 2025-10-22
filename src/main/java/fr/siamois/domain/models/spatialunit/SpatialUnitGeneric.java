package fr.siamois.domain.models.spatialunit;

import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.envers.Audited;

@EqualsAndHashCode(callSuper = true)
@Data
@Table(name = "spatial_unit")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("CASE WHEN fk_parent_action_unit_id IS NOT NULL THEN 'ExcavationUnit' ELSE 'SpatialUnit' END")
@Audited
public abstract class SpatialUnitGeneric extends SpatialUnitParent {


}