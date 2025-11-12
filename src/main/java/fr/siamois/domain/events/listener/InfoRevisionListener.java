package fr.siamois.domain.events.listener;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;

@SessionScoped
@Component
public class InfoRevisionListener {

    private final ApplicationContext applicationContext;

    private SessionSettingsBean sessionSettingsBean;
    private InstitutionRepository institutionRepository;
    private PersonRepository personRepository;

    public InfoRevisionListener(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Method called before persisting an InfoRevisionEntity.
     * Is it used to set the updatedBy and updatedFrom fields in the revision information.
     * @param entity the InfoRevisionEntity being persisted
     */
    @PrePersist
    private void onPersist(@NotNull InfoRevisionEntity entity) {
        if (sessionSettingsBean == null) {
            sessionSettingsBean = applicationContext.getBean(SessionSettingsBean.class);
        }

        if (personRepository == null) {
            personRepository = applicationContext.getBean(PersonRepository.class);
        }

        if (institutionRepository == null) {
            institutionRepository = applicationContext.getBean(InstitutionRepository.class);
        }

        UserInfo info = sessionSettingsBean.getUserInfo();
        if (info == null) {
            Person admin = personRepository.findByUsernameIgnoreCase("system").orElseThrow(() -> new IllegalStateException("System user should exists"));
            Institution defaultInsti = institutionRepository.findInstitutionByIdentifier("siamois").orElseThrow(() -> new IllegalStateException("Default institution should exist"));
            info = new UserInfo(defaultInsti, admin, "en");
        }

        entity.setUpdatedBy(info.getUser());
        entity.setUpdatedFrom(info.getInstitution());
    }

}
