package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.ui.bean.ActionFromBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@SessionScoped
@Getter
@Setter
@RequiredArgsConstructor
public class InstitutionDialogBean implements Serializable {

    private final transient VocabularyService vocabularyService;
    private String title;
    private String buttonLabel;

    private final transient InstitutionService institutionService;
    private final SessionSettingsBean sessionSettingsBean;
    private transient ActionFromBean actionFromBean;
    private String institutionName;
    private String identifier;
    private String description;
    private Boolean thesaurusError;

    private String thesaurusServerUrl = "https://thesaurus.mom.fr";
    private ThesaurusInfo selected = new ThesaurusInfo("", "", "", "");
    private transient List<ThesaurusInfo> publicThesaurus = new ArrayList<>();

    @EventListener(LoginEvent.class)
    public void reset() {
        log.trace("Reset called");
        actionFromBean = null;
        thesaurusError = false;
        institutionName = "";
        identifier = "";
        description = "";
        title = "";
        buttonLabel = "";
        publicThesaurus = new ArrayList<>();
        thesaurusServerUrl = "https://thesaurus.mom.fr";
        selected = new ThesaurusInfo("", "", "", "");
        reloadThesaurusInfoList();
    }

    public Institution createInstitution() throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException, NotSiamoisThesaurusException {
        Institution institution = new Institution();
        institution.setName(institutionName);
        institution.setIdentifier(identifier);
        institution.setId(-1L);
        institution.getManagers().add(sessionSettingsBean.getAuthenticatedUser());
        institution.setDescription(description);

        return institutionService.createInstitution(institution, selected.completeThesaurusUrl());
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

    public void reloadThesaurusInfoList() {
        log.trace("Reload thesaurus info list : {} {}", thesaurusServerUrl, sessionSettingsBean.getLanguageCode());
        try {
            publicThesaurus = vocabularyService.findAllPublicThesaurusOf(thesaurusServerUrl, sessionSettingsBean.getLanguageCode());
            if (!publicThesaurus.isEmpty()) {
                selected = publicThesaurus.get(0);
            }
        } catch (InvalidEndpointException e) {
            log.error(e.getMessage(), e);
            publicThesaurus = new ArrayList<>();
        }
    }

    public List<ThesaurusInfo> completeFromInput(String input) {
        List<ThesaurusInfo> filtered = new ArrayList<>();
        if (input == null || input.length() < 2)
            return publicThesaurus;

        input = input.toLowerCase();

        for (ThesaurusInfo info : publicThesaurus) {
            if (info.label().toLowerCase().contains(input) || info.idTheso().toLowerCase().contains(input)) {
                filtered.add(info);
            }
        }

        return filtered;
    }

}
