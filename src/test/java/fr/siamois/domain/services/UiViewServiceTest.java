package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.UITableViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UiViewServiceTest {

    @Mock UiViewRepository uiViewRepository;
    @Mock PersonMapper personMapper;
    @Mock UITableViewMapper uiTableViewMapper;

    @Captor ArgumentCaptor<UiTableView> entityCaptor;

    @InjectMocks
    UiViewService service;

    private UiTableView entity;
    private UITableViewDTO dto;
    private TableViewState state;
    private PersonDTO personDTO;
    private Person person;

    @BeforeEach
    void setUp() {
        state = new TableViewState();
        state.setVersion(1);

        person = new Person();
        person.setId(7L);

        personDTO = new PersonDTO();
        personDTO.setId(7L);

        entity = new UiTableView();
        entity.setId(10L);
        entity.setState(state);
        entity.setTitle("My View");
        entity.setOwner(person);

        dto = new UITableViewDTO();
        dto.setId(10L);
        dto.setTitle("My View");
        dto.setState(state);
        dto.setOwner(personDTO);
    }

    // ------------------------------------------------------------------
    // getState
    // ------------------------------------------------------------------

    @Test
    void getState_found_returnsEntityState() {
        when(uiViewRepository.findById(10L)).thenReturn(Optional.of(entity));

        TableViewState result = service.getState(10L);

        assertThat(result).isSameAs(state);
    }

    @Test
    void getState_notFound_throwsIllegalArgumentWithId() {
        when(uiViewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getState(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ------------------------------------------------------------------
    // findOne
    // ------------------------------------------------------------------

    @Test
    void findOne_found_returnsMappedDto() {
        when(uiViewRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(uiTableViewMapper.toDto(entity)).thenReturn(dto);

        UITableViewDTO result = service.findOne(10L);

        assertThat(result).isSameAs(dto);
        verify(uiTableViewMapper).toDto(entity);
    }

    @Test
    void findOne_notFound_throwsIllegalArgumentWithId() {
        when(uiViewRepository.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne(55L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("55");
    }

    // ------------------------------------------------------------------
    // save(TableViewState, PersonDTO, String)
    // ------------------------------------------------------------------

    @Test
    void save_statePersonTitle_persistsNewEntityAndReturnsMappedDto() {
        UiTableView saved = new UiTableView();
        saved.setId(20L);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(uiViewRepository.save(any(UiTableView.class))).thenReturn(saved);
        when(uiTableViewMapper.toDto(saved)).thenReturn(dto);

        UITableViewDTO result = service.save(state, personDTO, "My View");

        assertThat(result).isSameAs(dto);
    }

    @Test
    void save_statePersonTitle_entityHasCorrectStateAndTitle() {
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(uiViewRepository.save(any(UiTableView.class))).thenReturn(entity);
        when(uiTableViewMapper.toDto(entity)).thenReturn(dto);

        service.save(state, personDTO, "My View");

        verify(uiViewRepository).save(entityCaptor.capture());
        UiTableView captured = entityCaptor.getValue();
        assertThat(captured.getState()).isSameAs(state);
        assertThat(captured.getTitle()).isEqualTo("My View");
        assertThat(captured.getOwner()).isSameAs(person);
    }

    @Test
    void save_statePersonTitle_convertsPersonDtoToEntity() {
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(uiViewRepository.save(any())).thenReturn(entity);
        when(uiTableViewMapper.toDto(entity)).thenReturn(dto);

        service.save(state, personDTO, "Title");

        verify(personMapper).invertConvert(personDTO);
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    void update_setsOwnerAndIdOnDtoThenSavesAndMaps() {
        UITableViewDTO viewState = new UITableViewDTO();
        viewState.setTitle("Updated");

        UiTableView mappedEntity = new UiTableView();
        mappedEntity.setId(10L);

        when(uiTableViewMapper.toEntity(viewState)).thenReturn(mappedEntity);
        when(uiViewRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(uiTableViewMapper.toDto(mappedEntity)).thenReturn(dto);

        UITableViewDTO result = service.update(10L, viewState, personDTO);

        assertThat(result).isSameAs(dto);
        assertThat(viewState.getOwner()).isSameAs(personDTO);
        assertThat(viewState.getId()).isEqualTo(10L);
    }

    @Test
    void update_savesEntityReturnedByMapper() {
        UITableViewDTO viewState = new UITableViewDTO();
        UiTableView mappedEntity = new UiTableView();

        when(uiTableViewMapper.toEntity(viewState)).thenReturn(mappedEntity);
        when(uiViewRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(uiTableViewMapper.toDto(mappedEntity)).thenReturn(dto);

        service.update(10L, viewState, personDTO);

        verify(uiViewRepository).save(same(mappedEntity));
    }

    // ------------------------------------------------------------------
    // save(UITableViewDTO)
    // ------------------------------------------------------------------

    @Test
    void save_dto_convertsToEntitySavesAndMapsBack() {
        UITableViewDTO inputDto = new UITableViewDTO();
        inputDto.setTitle("Saved from DTO");
        UiTableView mappedEntity = new UiTableView();

        when(uiTableViewMapper.toEntity(inputDto)).thenReturn(mappedEntity);
        when(uiViewRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(uiTableViewMapper.toDto(mappedEntity)).thenReturn(dto);

        UITableViewDTO result = service.save(inputDto);

        assertThat(result).isSameAs(dto);
        verify(uiTableViewMapper).toEntity(inputDto);
        verify(uiViewRepository).save(mappedEntity);
        verify(uiTableViewMapper).toDto(mappedEntity);
    }

    @Test
    void save_dto_mapperCalledInCorrectOrder() {
        UITableViewDTO inputDto = new UITableViewDTO();
        UiTableView mappedEntity = new UiTableView();

        when(uiTableViewMapper.toEntity(inputDto)).thenReturn(mappedEntity);
        when(uiViewRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(uiTableViewMapper.toDto(mappedEntity)).thenReturn(dto);

        service.save(inputDto);

        var inOrder = inOrder(uiTableViewMapper, uiViewRepository);
        inOrder.verify(uiTableViewMapper).toEntity(inputDto);
        inOrder.verify(uiViewRepository).save(any());
        inOrder.verify(uiTableViewMapper).toDto(any());
    }
}
