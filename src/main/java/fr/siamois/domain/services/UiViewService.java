package fr.siamois.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.infrastructure.database.UiViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads UI view state from persistence.
 */
@Service
@RequiredArgsConstructor
public class UiViewService {

    private final UiViewRepository uiViewRepository;

    public TableViewState getState(Long viewId) {

        UiTableView entity = uiViewRepository.findById(viewId)
                .orElseThrow(() ->
                        new IllegalArgumentException("UI View not found: " + viewId)
                );

        return entity.getState();
    }
}
