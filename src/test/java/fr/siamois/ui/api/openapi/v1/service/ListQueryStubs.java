package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.FieldQuery;

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
}
