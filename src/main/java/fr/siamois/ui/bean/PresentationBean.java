package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.utils.AuthenticatedUserUtils;
import jakarta.faces.context.FacesContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class PresentationBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final ConversionService conversionService;


    public void continueToDashboardIfLogged() {
        Optional<PersonDTO> opt = AuthenticatedUserUtils.getAuthenticatedUser()
                .map(person -> conversionService.convert(person, PersonDTO.class));

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        try {
            if (opt.isEmpty() || userInfo == null || userInfo.getInstitution() == null) {
                FacesContext.getCurrentInstance().getExternalContext().redirect("login");
            } else {
                FacesContext.getCurrentInstance().getExternalContext().redirect("dashboard");
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void continueIfLogged() {
        Optional<PersonDTO> opt = AuthenticatedUserUtils.getAuthenticatedUser()
                .map(person -> conversionService.convert(person, PersonDTO.class));

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        try {
            if (opt.isEmpty() || userInfo == null || userInfo.getInstitution() == null) {
                FacesContext.getCurrentInstance().getExternalContext().redirect("login");
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

}
