package fr.siamois.ui.service.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonDetailsService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.OrganizationSummaryResponse;
import fr.siamois.ui.config.security.jwt.JwtService;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonDetailsService personDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final InstitutionService institutionService;
    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        if (email == null || email.isBlank() || password == null) {
            throw new BadCredentialsException("Missing credentials");
        }
        Person person;
        try {
            person = personDetailsService.findPersonByEmail(email.trim());
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, person.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!person.isEnabled() || !person.isAccountNonLocked() || !person.isAccountNonExpired()) {
            throw new BadCredentialsException("Account disabled");
        }

        String access = jwtService.createAccessToken(person);
        AuthUserResponse user = buildAuthUserResponse(person);
        return new LoginResponse(access, jwtService.accessTokenExpiresInSeconds(), AuthConstants.TOKEN_TYPE_BEARER, user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser() {
        Person person = AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return buildAuthUserResponse(person);
    }

    private AuthUserResponse buildAuthUserResponse(Person person) {
        PersonDTO dto = personMapper.convert(person);
        Set<InstitutionDTO> institutions = institutionService.findInstitutionsOfPerson(dto);
        List<OrganizationSummaryResponse> orgs = institutions.stream()
                .filter(i -> i.getId() != null)
                .sorted(Comparator.comparing(InstitutionDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(i -> new OrganizationSummaryResponse(i.getId(), i.getName()))
                .toList();

        return new AuthUserResponse(person.getId(), person.getEmail(), person.getUsername(), person.getName(), person.getLastname(), orgs);
    }
}
