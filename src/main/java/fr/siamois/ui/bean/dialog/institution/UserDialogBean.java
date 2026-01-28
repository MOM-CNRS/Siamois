package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.email.EmailManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;
import org.primefaces.event.TabChangeEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
@RequiredArgsConstructor
public class UserDialogBean implements Serializable {

    // Injections
    private final transient EmailManager emailManager;
    private final transient PersonService personService;
    private final transient InstitutionService institutionService;
    private final LangBean langBean;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LabelBean labelBean;
    private final transient ConceptService conceptService;

    // Data storage
    private Institution institution;
    private transient ProcessPerson processPerson;
    private String title;
    private String buttonLabel;
    private List<Person> alreadyExistingPersons = new ArrayList<>();
    private String conceptCompleteUrl;
    private Concept parentConcept;

    private boolean shouldRenderRoleField = false;

    private TabState tabState = TabState.SEARCH;

    // Search TAB
    private Person selectedExistingPerson;
    private Concept currentSelectedRole;
    private transient List<PersonRole> personSelectedList = new ArrayList<>();

    // Create TAB
    private String firstname;
    private String lastname;
    private String username;
    private String email;
    private String password;
    private String confirmPassword;

    public void init(String title, String buttonLabel, Institution institution, ProcessPerson processPerson) {
        reset();
        this.title = title;
        this.buttonLabel = buttonLabel;
        this.institution = institution;
        this.shouldRenderRoleField = false;
        this.processPerson = processPerson;
        this.tabState = TabState.SEARCH;
        PrimeFaces.current().ajax().update("newMemberDialog");
    }

    public void init(String title, String buttonLabel, Institution institution, boolean shouldRenderRole, ProcessPerson processPerson) {
        reset();
        this.title = title;
        this.buttonLabel = buttonLabel;
        this.institution = institution;
        this.shouldRenderRoleField = shouldRenderRole;
        this.processPerson = processPerson;
        if (shouldRenderRole) {
            conceptCompleteUrl = fieldConfigurationService.getUrlForFieldCode(sessionSettingsBean.getUserInfo(), Person.USER_ROLE_FIELD_CODE);
        }
        PrimeFaces.current().ajax().update("newMemberDialog");

    }

    @EventListener(LoginEvent.class)
    public void reset() {
        this.institution = null;
        this.title = null;
        this.buttonLabel = null;
        this.shouldRenderRoleField = false;
        this.processPerson = null;
        this.tabState = TabState.SEARCH;
        this.selectedExistingPerson = null;
        this.personSelectedList.clear();
        this.alreadyExistingPersons.clear();
        this.firstname = null;
        this.lastname = null;
        this.username = null;
        this.email = null;
        this.password = null;
        this.confirmPassword = null;
    }

    public void applyToAllPerson() {
        int affected = 0;
        for (PersonRole personRole : createOrSearchPersons()) {
            if (personRole != null) {
                Boolean added = processPerson.process(personRole);
                if(Boolean.TRUE.equals(added)) {
                    affected++;
                }
            }
        }
        if (affected > 0) {
            exit();
        }
    }

    private List<PersonRole> createOrSearchPersons() {
        if (tabState == TabState.SEARCH) {
            return searchPerson();
        } else if (tabState == TabState.CREATE) {
            PersonRole result = createPerson();
            if (result != null)
                return List.of(result);
            return List.of();
        } else if (tabState == TabState.BULK) {
            throw new UnsupportedOperationException("Bulk creation is not implemented yet.");
        }

        throw new IllegalStateException("Unexpected tab state: " + tabState);
    }

    public List<PersonRole> searchPerson() {
        return personSelectedList;
    }

    public PersonRole createPerson() {
        if (oneFieldIsEmpty()) {
            displayErrorMessage(langBean, "userDialog.error.fields");
            return null;
        }

        if (!password.equals(confirmPassword)) {
            displayErrorMessage(langBean, "userDialog.error.password.match");
            return null;
        }

        Person person = new Person();
        person.setName(firstname);
        person.setLastname(lastname);
        person.setUsername(username);
        person.setEmail(email);
        person.setPassword(password);
        person.setPassToModify(true);

        try {
            return new PersonRole(personService.createPerson(person), null);
        } catch (InvalidUsernameException e) {
            displayErrorMessage(langBean, "userDialog.error.username");
        } catch (EmailAlreadyExistException e) {
            displayErrorMessage(langBean, "userDialog.error.email.alreadyexists", email);
        } catch (InvalidEmailException e) {
            displayErrorMessage(langBean, "userDialog.error.email");
        } catch (UserAlreadyExistException e) {
            displayErrorMessage(langBean, "userDialog.error.username.alreadyexists", username);
        } catch (InvalidPasswordException e) {
            displayErrorMessage(langBean, "userDialog.error.password");
        } catch (InvalidNameException e) {
            displayErrorMessage(langBean, "userDialog.error.name");
        }
        return null;
    }

    private boolean oneFieldIsEmpty() {
        List<String> fields = List.of(firstname, lastname, username, email, password, confirmPassword);
        for (String field : fields) {
            if (StringUtils.isBlank(field)) {
                return true;
            }
        }
        return false;
    }

    public void exit() {
        reset();
        PrimeFaces.current().executeScript("PF('newMemberDialog').hide();");
    }

    public void tabChanged(TabChangeEvent<Void> event) {
        TabState oldState = tabState;
        String tabId = event.getTab().getId();
        switch (tabId) {
            case "createNewUser" -> tabState = TabState.CREATE;
            case "importUserGroup" -> tabState = TabState.BULK;
            default -> tabState = TabState.SEARCH;
        }
        log.trace("Tab changed from {} to: {}", oldState, tabState);
    }


    public enum TabState {
        SEARCH,
        CREATE,
        BULK
    }

    public List<Person> searchUser(String usernameOrMailInput) {
        List<Person> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(usernameOrMailInput));
        for (Person person : alreadyExistingPersons) {
            result.remove(person);
        }

        for (PersonRole person : personSelectedList) {
            result.remove(person.person());
        }

        return result;
    }

    public void addToList() {
        if (personSelectedIsValid() && roleFieldIsValid()) {
            personSelectedList.add(new PersonRole(selectedExistingPerson, currentSelectedRole));
            selectedExistingPerson = null;
            currentSelectedRole = null;
        } else {
            displayErrorMessage(langBean, "userDialog.error.empty");
        }
    }

    private boolean personSelectedIsValid() {
        return selectedExistingPerson != null;
    }

    private boolean roleFieldIsValid() {
        return !shouldRenderRoleField || currentSelectedRole != null;
    }

    public void removeFromList(PersonRole personRole) {
        personSelectedList.remove(personRole);
    }

    public List<Concept> completeRole(String input) {
        log.trace("Autocomplete role input: {}", input);
        return List.of();
    }

}
