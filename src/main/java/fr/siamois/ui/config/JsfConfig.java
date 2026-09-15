package fr.siamois.ui.config;

import com.sun.faces.config.ConfigureListener;
import jakarta.faces.annotation.FacesConfig;
import jakarta.faces.webapp.FacesServlet;
import jakarta.servlet.ServletContext;
import org.jboss.weld.environment.servlet.Listener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.context.ServletContextAware;

@FacesConfig
@Configuration
public class JsfConfig implements ServletContextAware {

    private final Environment environment;

    public JsfConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        boolean devMode = environment.matchesProfiles("dev", "dev-debug", "dev2", "test");

        servletContext.setInitParameter("com.sun.faces.forceLoadConfiguration", Boolean.TRUE.toString());
        servletContext.setInitParameter("javax.faces.FACELETS_SKIP_COMMENTS", Boolean.TRUE.toString());
        servletContext.setInitParameter("javax.faces.PARTIAL_STATE_SAVING", Boolean.TRUE.toString());
        servletContext.setInitParameter("primefaces.MOVE_SCRIPTS_TO_BOTTOM", Boolean.TRUE.toString());

        // Facelet re-parsing on every request and JSF's own dev-mode checks are useful locally but cost a
        // render-time penalty on every request — only worth paying outside production.
        servletContext.setInitParameter("facelets.DEVELOPMENT", Boolean.toString(devMode));
        servletContext.setInitParameter("javax.faces.PROJECT_STAGE", devMode ? "Development" : "Production");
        servletContext.setInitParameter("javax.faces.FACELETS_REFRESH_PERIOD", devMode ? "1" : "-1");

        servletContext.setInitParameter("javax.faces.DEFAULT_SUFFIX", ".xhtml");

        servletContext.setInitParameter("primefaces.CLIENT_SIDE_VALIDATION", Boolean.TRUE.toString());
        servletContext.setInitParameter("primefaces.THEME", "siamois-theme");
    }

    @Bean
    public ServletRegistrationBean<jakarta.faces.webapp.FacesServlet> facesServletRegistration() {
        ServletRegistrationBean<jakarta.faces.webapp.FacesServlet> registrationBean = new ServletRegistrationBean<>(new FacesServlet(), "*.xhtml");
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
        ServletListenerRegistrationBean<ConfigureListener> bean = new ServletListenerRegistrationBean<>(new ConfigureListener());
        bean.setOrder(Ordered.LOWEST_PRECEDENCE);
        return bean;
    }
}
