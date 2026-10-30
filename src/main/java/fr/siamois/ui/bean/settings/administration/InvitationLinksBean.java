package fr.siamois.ui.bean.settings.administration;

import fr.siamois.domain.models.auth.invitation.InvitationLink;
import fr.siamois.domain.models.auth.invitation.InvitationLinkStatus;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.auth.InvitationLinkService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;
import static fr.siamois.utils.MessageUtils.displayWarnMessage;

/**
 * The "invitation links" tab of the administration page: a super admin creates links that let several people
 * sign up, follows how many accounts each one created, and revokes them.
 */
@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
public class InvitationLinksBean {

    private final transient InvitationLinkService invitationLinkService;
    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    /** One of the days offered by the dropdown: 1, 3, 7 or 30. */
    private int validityDays = (int) InvitationLinkService.DEFAULT_VALIDITY.toDays();
    private int maxAccounts = InvitationLink.DEFAULT_MAX_ACCOUNTS;
    private transient List<InvitationLinkService.Summary> links = new ArrayList<>();
    /** The link just created: shown once, as only its hash is stored. */
    private String createdLink;

    public InvitationLinksBean(InvitationLinkService invitationLinkService,
                               ProfilePermissionService profilePermissionService,
                               SessionSettingsBean sessionSettingsBean,
                               LangBean langBean) {
        this.invitationLinkService = invitationLinkService;
        this.profilePermissionService = profilePermissionService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
    }

    private boolean isNotSuperAdmin() {
        return !profilePermissionService.hasInstancePermission(
                sessionSettingsBean.getAuthenticatedUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS);
    }

    /** Initialises the tab when the page is first displayed, not on every ajax round trip. */
    public void onPreRenderView() {
        if (!FacesContext.getCurrentInstance().isPostback()) {
            init();
        }
    }

    /** Loads the links and resets the creation form. */
    public void init() {
        validityDays = (int) InvitationLinkService.DEFAULT_VALIDITY.toDays();
        maxAccounts = InvitationLink.DEFAULT_MAX_ACCOUNTS;
        createdLink = null;
        refresh();
    }

    private void refresh() {
        links = isNotSuperAdmin() ? new ArrayList<>() : invitationLinkService.findAll();
    }

    public void create() {
        if (isNotSuperAdmin()) {
            displayWarnMessage(langBean, "administrationSettings.error.notAdmin");
            return;
        }
        try {
            InvitationLinkService.Created created = invitationLinkService.create(
                    Duration.ofDays(validityDays), maxAccounts, sessionSettingsBean.getAuthenticatedUser().getId());
            createdLink = invitationLinkService.link(created.rawToken());
                refresh();
        } catch (IllegalArgumentException e) {
            displayErrorMessage(langBean, "administrationSettings.invitationLinks.error.invalid");
        }
    }

    public void closeCreatedLink() {
        createdLink = null;
    }

    public void revoke(InvitationLinkService.Summary summary) {
        if (isNotSuperAdmin()) {
            displayWarnMessage(langBean, "administrationSettings.error.notAdmin");
            return;
        }
        invitationLinkService.revoke(summary.link().getId());
        refresh();
    }

    public boolean canRevoke(InvitationLinkService.Summary summary) {
        return summary.status() == InvitationLinkStatus.ACTIVE;
    }

    public String statusLabel(InvitationLinkStatus status) {
        return langBean.msg("administrationSettings.invitationLinks.status." + status.name());
    }

    public String statusChipClass(InvitationLinkStatus status) {
        return switch (status) {
            case ACTIVE -> "chip-status-active";
            case EXPIRED, FULL -> "chip-status-expired";
            case REVOKED -> "chip-status-inactive";
        };
    }
}
