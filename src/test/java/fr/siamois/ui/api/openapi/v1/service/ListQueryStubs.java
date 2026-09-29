package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.FieldQuery;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

/** List-query collaborators for tests that do not exercise them. */
public final class ListQueryStubs {

    private ListQueryStubs() {
    }

    /** A {@link FieldQueryService} for requests with no field-keyed sort/filter. */
    public static FieldQueryService none() {
        return mock(FieldQueryService.class,
                invocation -> invocation.getMethod().getReturnType() == FieldQuery.class ? FieldQuery.NONE : null);
    }

    /** An {@link AdditionalAnswersListProjector} that adds nothing: rows keep their projector's answers. */
    public static AdditionalAnswersListProjector noAdditionalAnswers() {
        return mock(AdditionalAnswersListProjector.class, invocation ->
                "merge".equals(invocation.getMethod().getName()) ? invocation.getArgument(0) : null);
    }

    /**
     * A real {@link MultiValueAnswers} — every multi-valued answer shaped as the API serves it —
     * over relation fields that have no value.
     */
    public static MultiValueAnswers multiValueAnswers() {
        return new MultiValueAnswers(noRelationValues());
    }

    /** A {@link RelationFieldService} whose relations are all empty. */
    @SuppressWarnings("unchecked")
    public static RelationFieldService noRelationValues() {
        return mock(RelationFieldService.class, invocation -> {
            if ("previews".equals(invocation.getMethod().getName())) {
                Collection<Long> owners = invocation.getArgument(1);
                return owners.stream().collect(Collectors.toMap(Function.identity(), id -> MultiValue.complete(List.of())));
            }
            if ("page".equals(invocation.getMethod().getName())) {
                return new RelationFieldService.ValuesPage(List.of(), 0);
            }
            return null;
        });
    }
}
