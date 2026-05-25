package fr.siamois.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.UITableViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.env.EnvironmentEndpoint;
import org.springframework.stereotype.Service;

/**
 * Loads UI view state from persistence.
 */
@Service
@RequiredArgsConstructor
public class UiViewService {

    private final UiViewRepository uiViewRepository;
    private final PersonMapper personMapper;
    private final UITableViewMapper uiTableViewMapper;

    public TableViewState getState(Long viewId) {

        UiTableView entity = uiViewRepository.findById(viewId)
                .orElseThrow(() ->
                        new IllegalArgumentException("UI View not found: " + viewId)
                );

        return entity.getState();
    }

    public UITableViewDTO findOne(Long viewId) {

        UiTableView entity = uiViewRepository.findById(viewId)
                .orElseThrow(() ->
                        new IllegalArgumentException("UI View not found: " + viewId)
                );

        return uiTableViewMapper.toDto(entity);
    }

    public UITableViewDTO save(TableViewState tableViewState, PersonDTO personDTO, String title) {

        UiTableView entity = new UiTableView();
        entity.setState(tableViewState);
        entity.setTitle(title);
        entity.setOwner(personMapper.invertConvert(personDTO));
        entity = uiViewRepository.save(entity);

        return uiTableViewMapper.toDto(entity);
    }

    public UITableViewDTO update(Long viewId, UITableViewDTO viewState, PersonDTO personDTO) {

        viewState.setOwner(personDTO);
        viewState.setId(viewId);
        viewState.setId(viewId);
        UiTableView entity = uiViewRepository.save(uiTableViewMapper.toEntity(viewState));

        return uiTableViewMapper.toDto(entity);
    }

    public UITableViewDTO save(UITableViewDTO uiTableViewDTO) {
        return uiTableViewMapper.toDto(uiViewRepository.save(uiTableViewMapper.toEntity(uiTableViewDTO)));
    }
}
