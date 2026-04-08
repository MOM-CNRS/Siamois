package fr.siamois.ui.bean.panel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.SearchResultDTO;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
@Setter
public class SearchBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final SearchRepository searchRepository;

    @Nullable
    private SearchResultDTO selected;

    private UserInfo userInfo;

    @PostConstruct
    public void init() {
        userInfo = sessionSettingsBean.getUserInfo();
    }

    public List<SearchResultDTO> completeText(String input) {
        return searchRepository.findResultsFor(input, userInfo.getInstitution(), userInfo.getUser());
    }

}
