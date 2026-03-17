package fr.siamois.ui.bean;

import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.io.IOException;
import java.io.Serializable;

@Slf4j
@Component
@RequestScope
public class RedirectBean implements Serializable {

    private final transient ServletContext servletContext;

    public RedirectBean(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public String redirectUrl(String resource) {
        if (!resource.startsWith("/"))
            resource = "/" + resource;
        return servletContext.getContextPath() + resource;
    }

    public void redirectTo(String resource) {
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect(redirectUrl(resource));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }



    public void redirectTo(HttpStatus errorStatus) {
        String errorCode = switch (errorStatus) {
            case NOT_FOUND -> "404";
            case FORBIDDEN -> "403";
            default -> "500";
        };

        redirectTo(String.format("/error/%s", errorCode));
    }

    public String redirectUrlToResource(String name, String library) {
        return redirectUrl(String.format("/jakarta.faces.resource/%s.xhtml?ln=%s", name, library));
    }

}
