package fr.siamois.ui.config;

import com.sun.faces.config.ConfigureListener;
import jakarta.faces.annotation.FacesConfig;
import jakarta.faces.webapp.FacesServlet;
import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.weld.environment.servlet.Listener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.context.ServletContextAware;

/**
 * JSF / PrimeFaces servlet init. Defaults to Production Facelets caching (critical for RESTORE_VIEW cost).
 * Opt into Development via {@code siamois.jsf.development=true} (e.g. when editing XHTML frequently).
 */
@Slf4j
@FacesConfig
@Configuration
@RequiredArgsConstructor
public class JsfConfig implements ServletContextAware {

    private final Environment environment;

    @Override
    public void setServletContext(ServletContext servletContext) {
        servletContext.setInitParameter("com.sun.faces.forceLoadConfiguration", Boolean.TRUE.toString());
        servletContext.setInitParameter("javax.faces.FACELETS_SKIP_COMMENTS", Boolean.TRUE.toString());
        servletContext.setInitParameter("javax.faces.DEFAULT_SUFFIX", ".xhtml");

        boolean development = environment.getProperty("siamois.jsf.development", Boolean.class, false);
        if (development) {
            servletContext.setInitParameter("facelets.DEVELOPMENT", Boolean.TRUE.toString());
            servletContext.setInitParameter("javax.faces.PROJECT_STAGE", "Development");
            servletContext.setInitParameter("javax.faces.FACELETS_REFRESH_PERIOD", "1");
            log.warn("JSF Facelets running in Development mode (siamois.jsf.development=true) — slower RESTORE_VIEW");
        } else {
            servletContext.setInitParameter("facelets.DEVELOPMENT", Boolean.FALSE.toString());
            servletContext.setInitParameter("javax.faces.PROJECT_STAGE", "Production");
            // -1 = never check Facelets for changes (cached compiled views)
            servletContext.setInitParameter("javax.faces.FACELETS_REFRESH_PERIOD", "-1");
            log.info("JSF Facelets Production mode (FACELETS_REFRESH_PERIOD=-1)");
        }

        // Explicit PSS (Mojarra default is already true; keep it pinned)
        servletContext.setInitParameter("javax.faces.PARTIAL_STATE_SAVING", Boolean.TRUE.toString());
        servletContext.setInitParameter("jakarta.faces.PARTIAL_STATE_SAVING", Boolean.TRUE.toString());

        servletContext.setInitParameter("primefaces.CLIENT_SIDE_VALIDATION", Boolean.TRUE.toString());
        servletContext.setInitParameter("primefaces.THEME", "siamois-theme");
    }

    @Bean
    public ServletRegistrationBean<jakarta.faces.webapp.FacesServlet> facesServletRegistration() {
        ServletRegistrationBean<jakarta.faces.webapp.FacesServlet> registrationBean =
                new ServletRegistrationBean<>(new FacesServlet(), "*.xhtml");
        registrationBean.setLoadOnStartup(1);
        return registrationBean;
    }

    /**
     * Weld doit initialiser le BeanManager avant {@link ConfigureListener} (Mojarra exige CDI en Jakarta Faces 4).
     */
    @Bean
    public ServletListenerRegistrationBean<Listener> weldServletListener() {
        ServletListenerRegistrationBean<Listener> bean = new ServletListenerRegistrationBean<>(new Listener());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public ServletListenerRegistrationBean<ConfigureListener> jsfConfigureListener() {
        ServletListenerRegistrationBean<ConfigureListener> bean =
                new ServletListenerRegistrationBean<>(new ConfigureListener());
        bean.setOrder(Ordered.LOWEST_PRECEDENCE);
        return bean;
    }
}
