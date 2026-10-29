package fr.siamois.ui.config.api;

import fr.siamois.ui.api.openapi.v1.request.list.SearchMode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents the {@code searchMode} query parameter on every endpoint marked
 * {@link SearchMode.Param} — read from the request where the search becomes a filter rather than
 * declared on each controller method, so springdoc can't see it on its own.
 */
@Component
public class SearchModeOperationCustomizer implements OperationCustomizer {

    /**
     * Adds the {@code searchMode} parameter to the operation of a marked endpoint.
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!handlerMethod.hasMethodAnnotation(SearchMode.Param.class)) return operation;
        StringSchema schema = new StringSchema();
        schema.addEnumItem("contains");
        schema.addEnumItem("prefix");
        schema.setDefault("contains");
        operation.addParametersItem(new Parameter()
                .in("query")
                .name(SearchMode.PARAM)
                .required(false)
                .schema(schema)
                .description("Comment search correspond : contains (l'identifiant contient le texte) ou "
                        + "prefix (il commence par le texte, ce que font les sélecteurs des champs)."));
        return operation;
    }
}
