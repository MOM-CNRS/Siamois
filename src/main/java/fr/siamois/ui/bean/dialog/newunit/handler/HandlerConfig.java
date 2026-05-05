package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class HandlerConfig {

    private final List<INewUnitHandler<? extends AbstractEntityDTO>> handlers;

    public HandlerConfig(List<INewUnitHandler<? extends AbstractEntityDTO>> handlers) {
        this.handlers = handlers;
    }

    @Bean
    public Map<UnitKind, INewUnitHandler<? extends AbstractEntityDTO>> unitHandlerMap() {
        return handlers.stream()
                .collect(Collectors.toMap(
                        INewUnitHandler::kind, // Use the kind() method to get the UnitKind
                        handler -> handler
                ));
    }
}

