package fr.siamois.ui.api.openapi.v1.service.map;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldGeometry;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import org.hibernate.Hibernate;

/**
 * What the map sources of the collections with a configurable form share: an additional field of type
 * geometry is a column their rows can be placed by.
 */
final class MapGeometryColumns {

    private MapGeometryColumns() {
    }

    /**
     * Whether a field is a geometry column: the collection's own geometry field, or a field of type geometry.
     *
     * @param ownGeometry           the collection's own geometry field id
     * @param fieldId               the field asked for
     * @param customFieldRepository where the additional fields are
     * @return true when the rows can be placed by it
     */
    static boolean isGeometryColumn(long ownGeometry, long fieldId, CustomFieldRepository customFieldRepository) {
        return fieldId == ownGeometry || customFieldRepository.findById(fieldId)
                .map(Hibernate::unproxy)
                .filter(CustomFieldGeometry.class::isInstance)
                .isPresent();
    }
}
