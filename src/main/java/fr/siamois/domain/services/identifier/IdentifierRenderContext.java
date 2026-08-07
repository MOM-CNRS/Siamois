package fr.siamois.domain.services.identifier;

import org.springframework.lang.Nullable;

/** Supplies display values to identifier resolvers without coupling them to one entity type. */
@FunctionalInterface
public interface IdentifierRenderContext {
    @Nullable
    Object value(String tokenCode);
}
