package fr.siamois.infrastructure.database.initializer.seeder;

import java.util.List;

public class CustomFieldSeeder {

    public record CustomFieldSeederSpec(
            String answerType,
            Boolean isSystemField,
            String label,
            String valueBinding,
            String iconClass,
            String styleClass
    ){};

    public void seed(List<CustomFieldSeederSpec> customFieldSeederSpec){

    }

}
