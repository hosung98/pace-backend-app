package com.pace.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.auth.security.SecurityErrorHandler;
import com.pace.common.util.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC에서 발생한 보안·비즈니스·서버 예외의 공통 응답을 검증합니다. */
class GlobalExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new GlobalExceptionHandler(new SecurityErrorHandler(new JsonCodec(new ObjectMapper()))))
            .build();

    @Test
    void 인증과_인가_실패를_서버오류로_바꾸지_않는다() throws Exception {
        mvc.perform(get("/authentication").requestAttr("requestId", "test-request"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("test-request"));
        mvc.perform(get("/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN")).andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void 비즈니스_상태는_유지하고_내부오류는_노출하지_않는다() throws Exception {
        mvc.perform(get("/business")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    /** 실제 서비스 예외를 재현하는 테스트 전용 컨트롤러입니다. */
    @RestController
    static class FailureController {
        @GetMapping("/authentication")
        void authentication() { throw new BadCredentialsException("내부 인증 정보"); }
        @GetMapping("/denied")
        void denied() { throw new AccessDeniedException("내부 권한 정보"); }
        @GetMapping("/business")
        void business() { throw BusinessException.conflict("충돌"); }
        @GetMapping("/unexpected")
        void unexpected() { throw new IllegalStateException("노출되면 안 되는 내부 정보"); }
    }
}
