package fr.siamois.ui.email;

import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Builds the localised strings shown in an invitation e-mail (scope phrase, subject and profile list),
 * shared by the "add member" dialogs and the members-list pages so both wording sources stay in sync.
 */
public final class InvitationMessages {

    private InvitationMessages() {
        throw new UnsupportedOperationException("InvitationMessages should not be instantiated");
    }

    /**
     * @param langBean        the language bean resolving the localised message
     * @param institutionName the institution the invitee is joining
     * @return the scope phrase for an institution invitation (e.g. "the institution X")
     */
    public static String institutionScope(LangBean langBean, String institutionName) {
        return langBean.msg("mail.invitation.scope.institution", institutionName);
    }

    /**
     * @param langBean         the language bean resolving the localised message
     * @param projectName      the project the invitee is joining
     * @param organisationName the organisation owning the project, or {@code null}/blank when unknown
     * @return the scope phrase for a project invitation, qualified with the organisation when available
     */
    public static String projectScope(LangBean langBean, String projectName, String organisationName) {
        if (StringUtils.isBlank(organisationName)) {
            return langBean.msg("mail.invitation.scope.project", projectName);
        }
        return langBean.msg("mail.invitation.scope.project.withOrganisation", projectName, organisationName);
    }

    /**
     * @param langBean the language bean resolving the localised message
     * @return the scope phrase for an application-wide invitation (e.g. "SIAMOIS")
     */
    public static String applicationScope(LangBean langBean) {
        return langBean.msg("mail.invitation.application.scopeName");
    }

    /**
     * @param langBean        the language bean resolving the localised message
     * @param institutionName the institution the invitee is joining
     * @return the subject of an institution invitation e-mail
     */
    public static String institutionSubject(LangBean langBean, String institutionName) {
        return langBean.msg("mail.invitation.subject", institutionName);
    }

    /**
     * @param langBean    the language bean resolving the localised message
     * @param projectName the project the invitee is joining
     * @return the subject of a project invitation e-mail
     */
    public static String projectSubject(LangBean langBean, String projectName) {
        return langBean.msg("mail.invitation.project.subject", projectName);
    }

    /**
     * @param langBean the language bean resolving the localised message
     * @return the subject of an application-wide invitation e-mail
     */
    public static String applicationSubject(LangBean langBean) {
        return langBean.msg("mail.invitation.application.subject");
    }

    /**
     * Builds a human-readable, comma-separated list of the given profile names.
     *
     * @param langBean the language bean resolving the fallback message when no profile is present
     * @param profiles the profiles granted to the invitee
     * @return the joined profile names, or a localised "no specific profile" fallback when empty
     */
    public static String profilesLabel(LangBean langBean, Collection<ProfileDTO> profiles) {
        String label = profiles.stream()
                .map(ProfileDTO::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        return StringUtils.isBlank(label) ? langBean.msg("mail.invitation.profiles.none") : label;
    }
}
