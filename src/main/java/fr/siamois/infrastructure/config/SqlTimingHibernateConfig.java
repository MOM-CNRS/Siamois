package fr.siamois.infrastructure.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Points Hibernate's own connection acquisition at a {@link TimingDataSourceProxy}-wrapped datasource,
 * without touching the Spring {@code dataSource} bean itself — several classes (e.g.
 * {@code StratiTriggerInitializer}, {@code SearchRepository}) inject the concrete
 * {@code HikariDataSource} type directly, which breaks if that bean is replaced by a JDK dynamic proxy
 * (interface-only). Overriding {@code hibernate.connection.datasource} in the JPA properties instead
 * only affects how the {@code EntityManagerFactory} opens connections — which is all this diagnostic
 * (see {@link TimingDataSourceProxy}) needs.
 */
@Configuration
public class SqlTimingHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer sqlTimingHibernatePropertiesCustomizer(DataSource dataSource) {
        DataSource timed = TimingDataSourceProxy.wrap(dataSource);
        return properties -> properties.put(AvailableSettings.DATASOURCE, timed);
    }
}
