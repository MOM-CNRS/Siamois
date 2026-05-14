package fr.siamois.ui.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final AuthService authService;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredTokens() {
        log.info("Purging expired refresh tokens");
        authService.purgeExpiredRefreshTokens();
    }
}
