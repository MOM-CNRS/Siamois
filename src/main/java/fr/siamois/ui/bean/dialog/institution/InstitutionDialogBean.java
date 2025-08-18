package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.ui.bean.ActionFromBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;

@Slf4j
@Component
@SessionScoped
@Getter
@Setter
public class InstitutionDialogBean implements Serializable {

    private String title;
    private String buttonLabel;

    private final transient InstitutionService institutionService;
    private final SessionSettingsBean sessionSettingsBean;
    private transient ActionFromBean actionFromBean;
    private String institutionName;
    private String identifier;
    private String description;
    private String thesaurusUrl;
    private Boolean thesaurusError;

    public InstitutionDialogBean(InstitutionService institutionService,
                                 SessionSettingsBean sessionSettingsBean) {
        this.institutionService = institutionService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        log.trace("Reset called");
        actionFromBean = null;
        thesaurusUrl = null;
        thesaurusError = false;
        institutionName = "";
        identifier = "";
        description = "";
        title = "";
        buttonLabel = "";
    }

    public Institution createInstitution() throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException {
        Institution institution = new Institution();
        institution.setName(institutionName);
        institution.setIdentifier(identifier);
        institution.setId(-1L);
        institution.getManagers().add(sessionSettingsBean.getAuthenticatedUser());
        institution.setDescription(description);

        return institutionService.createInstitution(institution, thesaurusUrl);
    }

    public void save() {
        assert actionFromBean != null;
        actionFromBean.apply();
    }

    public void exit() {
        log.trace("Exit organization dialog");
        reset();
        PrimeFaces.current().executeScript("PF('newInstitutionDialog').hide();");
    }

}
