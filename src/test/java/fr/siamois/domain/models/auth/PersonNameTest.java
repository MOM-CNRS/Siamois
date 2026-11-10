package fr.siamois.domain.models.auth;

import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonNameTest {

    @Test
    void constructor_trimsAndCollapsesWhitespace() {
        PersonName name = new PersonName("  Jean   Paul ", " Sartre ");

        assertEquals("Jean Paul", name.firstName());
        assertEquals("Sartre", name.lastName());
        assertEquals("Jean Paul Sartre", name.displayName());
    }

    @Test
    void constructor_keepsNullAsEmpty() {
        PersonName name = new PersonName(null, null);

        assertEquals("", name.firstName());
        assertEquals("", name.lastName());
    }

    @Test
    void of_acceptsFilledNames() throws InvalidNameException {
        PersonName name = PersonName.of("Marie", "Curie");

        assertEquals("Marie", name.firstName());
        assertEquals("Curie", name.lastName());
    }

    @Test
    void of_rejectsBlankFirstName() {
        assertThrows(InvalidNameException.class, () -> PersonName.of("  ", "Curie"));
    }

    @Test
    void of_rejectsBlankLastName() {
        assertThrows(InvalidNameException.class, () -> PersonName.of("Marie", null));
    }

    @Test
    void of_rejectsTooLongName() {
        String tooLong = "a".repeat(Person.NAME_MAX_LENGTH + 1);

        assertThrows(InvalidNameException.class, () -> PersonName.of(tooLong, "Curie"));
    }

    @Test
    void matches_ignoresCaseAndAccents() {
        PersonName name = new PersonName("Hélène", "Lefèvre");

        assertTrue(name.matches("helene", "LEFEVRE"));
        assertTrue(name.matches(" Hélène ", "Lefèvre"));
        assertFalse(name.matches("Hélène", "Lefebvre"));
    }

    @Test
    void matchKey_isLowerCaseWithoutAccents() {
        assertEquals("helene lefevre", new PersonName("Hélène", "LEFÈVRE").matchKey());
    }
}
