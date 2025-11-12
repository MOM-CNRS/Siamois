package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.FieldCode;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service to handle the fields in the application.
 *
 * @author Julien Linget
 */
@Slf4j
@Service
@Transactional
public class FieldService {

    private static final List<String> FIELD_CODES;

    static {
        FIELD_CODES = new ArrayList<>();
        loadFieldCodes();
    }

    private static void loadFieldCodes() {
        Reflections reflections = new Reflections("fr.siamois.domain.models", Scanners.FieldsAnnotated);
        Set<Field> fieldsWithFieldCode = reflections.getFieldsAnnotatedWith(FieldCode.class);

        for (Field field : fieldsWithFieldCode) {
            if (isValidFieldCode(field)) {
                try {
                    String fieldCode = (String) field.get(null);
                    FIELD_CODES.add(fieldCode.toUpperCase());
                } catch (IllegalAccessException e) {
                    log.error("Error while searching for field code {}", field.getName());
                }
            }
        }
    }

    /**
     * Returns all field codes in the application.
     *
     * @return a list of field codes
     */
    @NonNull
    public List<String> searchAllFieldCodes() {
        return new ArrayList<>(FIELD_CODES);
    }

    @NonNull
    public <T> List<String> findFieldCodesOf(@NonNull Class<T> entityClass) {
        List<String> fieldCodes = new ArrayList<>();
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(FieldCode.class) && isValidFieldCode(field)) {
                try {
                    String fieldCode = (String) field.get(null);
                    fieldCodes.add(fieldCode.toUpperCase());
                } catch (IllegalAccessException e) {
                    log.error("Error while searching for field code {} in class {}", field.getName(), entityClass.getSimpleName());
                }
            }
        }
        return fieldCodes;
    }

    private static boolean isValidFieldCode(Field field) {
        return field.getType().equals(String.class) &&
                Modifier.isStatic(field.getModifiers()) &&
                Modifier.isFinal(field.getModifiers());
    }

}
