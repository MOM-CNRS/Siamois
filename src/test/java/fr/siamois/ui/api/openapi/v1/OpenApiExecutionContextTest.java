package fr.siamois.ui.api.openapi.v1;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiExecutionContextTest {

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    void runWithUserInfo_setsAndClearsContext() {
        UserInfo userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr");

        OpenApiExecutionContext.runWithUserInfo(userInfo, () ->
                assertThat(ExecutionContextHolder.get()).isSameAs(userInfo));

        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    void runWithUserInfo_nullUserInfo_runsWithoutContext() {
        OpenApiExecutionContext.runWithUserInfo(null, () ->
                assertThat(ExecutionContextHolder.get()).isNull());
    }

    @Test
    void callWithUserInfo_returnsValueAndClearsContext() {
        UserInfo userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "en");

        String result = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            assertThat(ExecutionContextHolder.get()).isSameAs(userInfo);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(ExecutionContextHolder.get()).isNull();
    }
}
