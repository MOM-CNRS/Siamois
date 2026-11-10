package fr.siamois.domain.models.auth;

import fr.siamois.domain.models.exceptions.auth.InvalidNameException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The first and last name of a person, trimmed and with inner whitespace collapsed.
 * <p>
 * It is what identifies a person created on the fly from a form field (a person awaiting the creation of
 * their account, see {@code PendingAccountPersonService}): two names are the same person's when their
 * {@link #matchKey() match keys} are equal, i.e. ignoring case and accents.
 *
 * @param firstName the first name ({@link Person#getName()})
 * @param lastName  the last name ({@link Person#getLastname()})
 */
public record PersonName(String firstName, String lastName) {

    /**
     * Normalizes both parts: trimmed, inner whitespace collapsed to one space, {@code null} kept as an empty text.
     */
    public PersonName {
        firstName = normalized(firstName);
        lastName = normalized(lastName);
    }

    /**
     * Builds a name both parts of which are filled and fit in {@link Person#NAME_MAX_LENGTH} characters.
     *
     * @param firstName the first name
     * @param lastName  the last name
     * @return the normalized name
     * @throws InvalidNameException when a part is blank or too long
     */
    public static PersonName of(String firstName, String lastName) throws InvalidNameException {
        PersonName name = new PersonName(firstName, lastName);
        requireValid(name.firstName(), "First name");
        requireValid(name.lastName(), "Last name");
        return name;
    }

    /**
     * The name as compared with another one: lower case, without accents, first name then last name.
     *
     * @return the comparison key of this name
     */
    public String matchKey() {
        return withoutAccents(firstName + " " + lastName).toLowerCase(Locale.ROOT);
    }

    /**
     * Tells whether a person carries this name, ignoring case and accents.
     *
     * @param name     the person's first name
     * @param lastname the person's last name
     * @return {@code true} when both parts match
     */
    public boolean matches(String name, String lastname) {
        return matchKey().equals(new PersonName(name, lastname).matchKey());
    }

    /**
     * The name as displayed: first name then last name.
     *
     * @return the display name
     */
    public String displayName() {
        return firstName + " " + lastName;
    }

    private static void requireValid(String part, String label) throws InvalidNameException {
        if (part.isEmpty()) {
            throw new InvalidNameException(label + " cannot be empty.");
        }
        if (part.length() > Person.NAME_MAX_LENGTH) {
            throw new InvalidNameException(label + " should be at most " + Person.NAME_MAX_LENGTH + " characters.");
        }
    }

    private static String normalized(String part) {
        return part == null ? "" : part.trim().replaceAll("\\s+", " ");
    }

    private static String withoutAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
