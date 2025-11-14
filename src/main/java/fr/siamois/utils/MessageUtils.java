package fr.siamois.utils;

import fr.siamois.ui.bean.LangBean;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.PrimeFaces;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for displaying messages in the UI.
 * This class provides methods to display messages with different severities (info, error, warn) using a LangBean for localization.
 * It also handles the display of messages in a PrimeFaces growl component.
 */
public class MessageUtils {

    private static final Map<FacesMessage.Severity, String> titlesCodes = new HashMap<>();

    private MessageUtils() {
        throw new UnsupportedOperationException("MessageUtils is a utility class and cannot be instantiated");
    }

    static {
        titlesCodes.put(FacesMessage.SEVERITY_INFO, "common.message.state.info");
        titlesCodes.put(FacesMessage.SEVERITY_ERROR, "common.message.state.error");
        titlesCodes.put(FacesMessage.SEVERITY_WARN, "common.message.state.warn");
    }

    /**
     * Display a message with the specified severity and message code with optional arguments.
     *
     * @param langBean    the LangBean used for localization
     * @param severity    the severity of the message (info, error, warn)
     * @param messageCode the code of the message to display
     * @param args        optional arguments to format the message
     */
    public static void displayMessage(LangBean langBean, FacesMessage.Severity severity, String messageCode, Object... args) {
        String title = messageCode;
        if (titlesCodes.containsKey(severity))
            title = langBean.msg(titlesCodes.get(severity));

        displayMessage(severity, title, langBean.msg(messageCode, args));

        if (!titlesCodes.containsKey(severity))
            throw new IllegalArgumentException("Unknown severity: " + severity + ". Replaced by messageCode");
    }

    /**
     * Display a message with the specified severity, title, and content.
     *
     * @param severity   the severity of the message (info, error, warn)
     * @param title      the title of the message
     * @param msgContent the content of the message
     */
    public static void displayMessage(FacesMessage.Severity severity, String title, String msgContent) {
        FacesMessage facesMessage = new FacesMessage(severity, title, msgContent);
        FacesContext.getCurrentInstance().addMessage("templateFormCC:templateForm:templateGrowl", facesMessage);
        PrimeFaces.current().ajax().update("templateFormCC:templateForm:templateGrowl");
    }

    /**
     * Display an informational message using the LangBean for localization.
     *
     * @param langBean the LangBean used for localization
     * @param msgCode  the code of the message to display
     * @param args     optional arguments to format the message
     */
    public static void displayInfoMessage(LangBean langBean, String msgCode, Object... args) {
        displayMessage(langBean, FacesMessage.SEVERITY_INFO, msgCode, args);
    }

    /**
     * Display an error message using the LangBean for localization.
     *
     * @param langBean the LangBean used for localization
     * @param msgCode  the code of the message to display
     * @param args     optional arguments to format the message
     */
    public static void displayErrorMessage(LangBean langBean, String msgCode, Object... args) {
        displayMessage(langBean, FacesMessage.SEVERITY_ERROR, msgCode, args);
    }

    /**
     * Display a warning message using the LangBean for localization.
     *
     * @param langBean the LangBean used for localization
     * @param msgCode  the code of the message to display
     * @param args     optional arguments to format the message
     */
    public static void displayWarnMessage(LangBean langBean, String msgCode, Object... args) {
        displayMessage(langBean, FacesMessage.SEVERITY_WARN, msgCode, args);
    }

    /**
     * Display a message indicating that no thesaurus is configured.
     *
     * @param langBean the LangBean used for localization
     */
    public static void displayNoThesaurusConfiguredMessage(LangBean langBean) {
        displayErrorMessage(langBean, "common.error.thesaurusConfig.notfound");
    }

    /**
     * Display a message indicating that an internal error has occurred.
     *
     * @param langBean the LangBean used for localization
     */
    public static void displayInternalError(LangBean langBean) {
        displayErrorMessage(langBean, "common.error.internal");
    }
}
