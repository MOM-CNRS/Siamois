package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkResultResource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Runs a bulk action element by element and collects what became of each: the elements it changed
 * and, for the others, their failure. One builder per bulk request.
 */
final class BulkResultBuilder {

    private final List<Long> succeeded = new ArrayList<>();
    private final List<BulkFailureResource> failed = new ArrayList<>();
    private final BulkFailureTranslator failureTranslator;

    /**
     * A builder reporting failures through this translator.
     *
     * @param failureTranslator how an element's failure is reported
     */
    BulkResultBuilder(BulkFailureTranslator failureTranslator) {
        this.failureTranslator = failureTranslator;
    }

    /**
     * Runs the action on one element and records its success, or its failure.
     *
     * @param id     the element's id
     * @param action the element's unit operation
     */
    void attempt(long id, LongConsumer action) {
        try {
            action.accept(id);
            succeeded.add(id);
        } catch (RuntimeException e) {
            failed.add(failureTranslator.translate(id, e));
        }
    }

    /**
     * What the action did, element by element.
     *
     * @return the bulk result
     */
    BulkResultResource build() {
        return new BulkResultResource(List.copyOf(succeeded), List.copyOf(failed));
    }
}
