package fr.siamois.ui.bean;

import lombok.Getter;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AppVersionBean {

    private final BuildProperties buildProperties;

    public AppVersionBean(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public String getVersion() {
        return buildProperties.getVersion();
    }

}

