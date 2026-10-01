package fr.siamois.ui.bean.panel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.SearchResultDTO;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.AjaxBehaviorEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
@Setter
public class SearchBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient SearchRepository searchRepository;
    private final FlowBean flowBean;

    @Nullable
    private SearchResultDTO selected;

    private UserInfo userInfo;

    @PostConstruct
    public void init() {
        userInfo = sessionSettingsBean.getUserInfo();
    }

    private static final String SNAKE_EASTER_EGG_KEYWORD = "motherlode";

    public List<SearchResultDTO> completeText(String input) {
        if (input != null && SNAKE_EASTER_EGG_KEYWORD.equalsIgnoreCase(input.trim())) {
            PrimeFaces.current().ajax().update("snakeGameForm");
            PrimeFaces.current().executeScript("PF('snakeGameDiag').show()");
            return List.of();
        }
        return searchRepository.findResultsFor(input,
                sessionSettingsBean.getSelectedInstitution(),
                userInfo.getUser());
    }

    /**
     * Opens the selected result's fiche — a full navigation to its focus URL, the same one an
     * identifier chip leads to. The main panel is React: updating the JSF overview would not reach it.
     */
    public void onResultSelect(AjaxBehaviorEvent event) throws IOException {
        String resourceUri = resourceUriOf(selected);
        if (resourceUri == null) return;
        selected = null;
        flowBean.redirectToFocus(resourceUri);
    }

    @Nullable
    static String resourceUriOf(@Nullable SearchResultDTO result) {
        if (result == null) return null;
        if (result.getRecordingUnitId() != null) return "/recording-unit/" + result.getRecordingUnitId();
        if (result.getSpatialUnitId() != null) return "/spatial-unit/" + result.getSpatialUnitId();
        if (result.getActionUnitId() != null) return "/action-unit/" + result.getActionUnitId();
        if (result.getSpecimenId() != null) return "/specimen/" + result.getSpecimenId();
        return null;
    }

}
