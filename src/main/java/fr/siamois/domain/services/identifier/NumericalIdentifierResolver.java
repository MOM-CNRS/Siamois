package fr.siamois.domain.services.identifier;

import org.springframework.lang.Nullable;

/** Standard numerical token with width-aware zero padding and missing-value rendering. */
public record NumericalIdentifierResolver<C extends IdentifierRenderContext>(
        String code,
        String titleCode,
        @Nullable String descriptionCode,
        @Nullable String partitionDimensionCode) implements IdentifierResolver<C> {
    @Override
    public IdentifierValueKind valueKind() {
        return IdentifierValueKind.NUMERICAL;
    }
}
