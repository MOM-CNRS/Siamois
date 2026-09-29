package fr.siamois.ui.config.api;

import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.math.BigDecimal;

/**
 * Documents the {@code valuesLimit} query parameter on every endpoint marked
 * {@link ValuesLimit.Param} — read from the request deep in the answers projection rather than
 * declared on each controller method, so springdoc can't see it on its own.
 */
@Component
public class ValuesLimitOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ValuesLimit.Param param = handlerMethod.getMethodAnnotation(ValuesLimit.Param.class);
        if (param == null) return operation;
        IntegerSchema schema = new IntegerSchema();
        schema.setMinimum(BigDecimal.ZERO);
        schema.setMaximum(BigDecimal.valueOf(ValuesLimit.MAX));
        schema.setDefault(param.defaultValue());
        operation.addParametersItem(new Parameter()
                .in("query")
                .name(ValuesLimit.PARAM)
                .required(false)
                .schema(schema)
                .description("Nombre de valeurs renvoyées pour chaque champ multivalué de answers. Chaque "
                        + "valeur multivaluée donne aussi total et complete ; si complete=false, la liste "
                        + "entière est à _links.values (GET /api/v1/{collection}/{id}/fields/{fieldId}/values). "
                        + "0 = le total seulement."));
        return operation;
    }
}
