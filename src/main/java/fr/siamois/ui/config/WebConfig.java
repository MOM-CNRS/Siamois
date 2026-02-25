package fr.siamois.ui.config;


import fr.siamois.ui.mapper.FormPanelMapperImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Specifies the page redirection without parameters. The page redirection with parameters are placed inside
     * controllers in {@link fr.siamois.ui.redirection}
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Entry point
        registry.addViewController("/").setViewName("forward:/index.xhtml");

        // Login
        registry.addViewController("/login").setViewName("forward:/pages/login/login.xhtml");

        // Errors
        registry.addViewController("/error/404").setViewName("forward:/pages/error/error-404.xhtml");
        registry.addViewController("/error/403").setViewName("forward:/pages/error/error-403.xhtml");
        registry.addViewController("/error/500").setViewName("forward:/pages/error/error-500.xhtml");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        AntPathMatcher matcher = new AntPathMatcher();
        matcher.setCaseSensitive(false);
        configurer.setPathMatcher(matcher);
    }


}
