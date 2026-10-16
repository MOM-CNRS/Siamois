package fr.siamois.domain.services.placesource;

import fr.siamois.dto.entity.FullAddress;
import org.springframework.lang.Nullable;

/** A place suggested by an external source, not (yet) a place of the organization. */
public record ExternalPlace(String name, @Nullable String code, @Nullable FullAddress address) {
}
