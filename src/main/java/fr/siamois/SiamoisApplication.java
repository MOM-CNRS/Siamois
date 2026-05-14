package fr.siamois;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
public class SiamoisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiamoisApplication.class, args);
    }

}
