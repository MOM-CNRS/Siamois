package fr.siamois.utils;

import fr.siamois.domain.models.auth.Person;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility class to retrieve the authenticated user from the SecurityContextHolder
 * @author Julien Linget
 */
public class AuthenticatedUserUtils {

    private AuthenticatedUserUtils() {}

    /**
     * Retrieve the authenticated Person from the {@link SecurityContextHolder}
     * @return Optional contains Person if user is authenticated and empty if no user is authenticated
     */
    public static Optional<Person> getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if ( authentication == null || authentication instanceof AnonymousAuthenticationToken) return Optional.empty();
        Person person = (Person) authentication.getPrincipal();
        return Optional.of(person);
    }

}
