package fr.siamois.ui.service.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.RefreshToken;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.TokenHasher;
import fr.siamois.domain.services.person.PersonDetailsService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.auth.RefreshTokenRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.OrganizationSummaryResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.TokenRefreshResponse;
import fr.siamois.ui.config.security.jwt.JwtProperties;
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

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonDetailsService personDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final InstitutionService institutionService;
    private final PersonMapper personMapper;

    @Transactional
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
        String refreshPlain = generateOpaqueRefreshToken();
        persistRefreshToken(person, refreshPlain);

        AuthUserResponse user = buildAuthUserResponse(person);
        return new LoginResponse(
                access,
                refreshPlain,
                jwtService.accessTokenExpiresInSeconds(),
                AuthConstants.TOKEN_TYPE_BEARER,
                user);
    }

    @Transactional
    public TokenRefreshResponse refresh(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }
        String hash = TokenHasher.sha256Hex(refreshTokenPlain.trim());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new BadCredentialsException("Expired refresh token");
        }
        Person person = stored.getPerson();
        person.getAuthorities();
        String access = jwtService.createAccessToken(person);
        return new TokenRefreshResponse(access, jwtService.accessTokenExpiresInSeconds(), AuthConstants.TOKEN_TYPE_BEARER);
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256Hex(refreshTokenPlain.trim()));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser() {
        Person person = AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return buildAuthUserResponse(person);
    }

    private void persistRefreshToken(Person person, String refreshPlain) {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken rt = new RefreshToken();
        rt.setPerson(person);
        rt.setTokenHash(TokenHasher.sha256Hex(refreshPlain));
        rt.setExpiresAt(now.plus(jwtProperties.getRefreshTokenValidity()));
        rt.setCreatedAt(now);
        refreshTokenRepository.save(rt);
    }

    private static String generateOpaqueRefreshToken() {
        byte[] buf = new byte[48];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
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
