package fr.siamois.domain.services.identifier;

import org.springframework.lang.Nullable;

/** Standard textual/full-identifier token; a missing value is rendered as XXX. */
public record TextIdentifierResolver<C extends IdentifierRenderContext>(
        String code,
        String titleCode,
        @Nullable String descriptionCode,
        @Nullable String partitionDimensionCode) implements IdentifierResolver<C> {
    @Override
    public IdentifierValueKind valueKind() {
        return IdentifierValueKind.TEXT;
    }
}
