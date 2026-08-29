package com.pilarestilo.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiGatewayRateLimitFilterForgotPasswordTest {

    private final ApiGatewayRateLimitFilter filter =
            new ApiGatewayRateLimitFilter(true, 60, 12, 6, 5, 180);

    private MockHttpServletRequest forgotPasswordFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
        request.addHeader("X-Forwarded-For", ip);
        return request;
    }

    @Test
    void the_sixth_request_from_one_ip_in_the_window_is_throttled() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(forgotPasswordFrom("203.0.113.7"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse sixth = new MockHttpServletResponse();
        filter.doFilterInternal(forgotPasswordFrom("203.0.113.7"), sixth, chain);

        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getHeader("Retry-After")).isEqualTo("60");
        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void a_different_ip_has_its_own_budget() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 6; i++) {
            filter.doFilterInternal(forgotPasswordFrom("198.51.100.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilterInternal(forgotPasswordFrom("198.51.100.2"), other, chain);

        assertThat(other.getStatus()).isEqualTo(200);
    }
}
