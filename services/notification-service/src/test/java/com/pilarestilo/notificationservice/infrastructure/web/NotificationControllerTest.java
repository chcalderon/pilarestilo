package com.pilarestilo.notificationservice.infrastructure.web;

import com.pilarestilo.notificationservice.application.dto.InAppNotificationDto;
import com.pilarestilo.notificationservice.application.usecases.GetNotificationsUseCase;
import com.pilarestilo.notificationservice.application.usecases.GetUnreadCountUseCase;
import com.pilarestilo.notificationservice.application.usecases.MarkAllNotificationsReadUseCase;
import com.pilarestilo.notificationservice.application.usecases.MarkNotificationReadUseCase;
import com.pilarestilo.notificationservice.auth.AuthenticatedUser;
import com.pilarestilo.notificationservice.auth.JwtAuthenticationFilter;
import com.pilarestilo.notificationservice.auth.JwtTokenProvider;
import com.pilarestilo.notificationservice.auth.UserRole;
import com.pilarestilo.notificationservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class })
@TestPropertySource(properties = "app.jwt.secret=U2VjcmV0U2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean GetNotificationsUseCase getNotificationsUseCase;
    @MockitoBean GetUnreadCountUseCase getUnreadCountUseCase;
    @MockitoBean MarkNotificationReadUseCase markReadUseCase;
    @MockitoBean MarkAllNotificationsReadUseCase markAllReadUseCase;

    final UUID userId = UUID.randomUUID();

    private Authentication customer() {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "cliente@example.com",
                UserRole.CUSTOMER, false);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    @Test
    void list_is_scoped_to_the_authenticated_user() throws Exception {
        InAppNotificationDto dto = new InAppNotificationDto(UUID.randomUUID(), "ORDER_CONFIRMED",
                "Pedido confirmado", "Cuerpo", Map.of(), false, Instant.now());
        when(getNotificationsUseCase.execute(eq(userId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/notifications").with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Pedido confirmado"));

        verify(getNotificationsUseCase).execute(eq(userId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unread_count_wraps_the_use_case_result() throws Exception {
        when(getUnreadCountUseCase.execute(userId)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").with(authentication(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void mark_read_is_a_put_and_returns_204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/notifications/{id}/read", id).with(authentication(customer())))
                .andExpect(status().isNoContent());

        verify(markReadUseCase).execute(id, userId);
    }

    @Test
    void mark_all_read_is_a_put_and_returns_204() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all").with(authentication(customer())))
                .andExpect(status().isNoContent());

        verify(markAllReadUseCase).execute(userId);
    }

    @Test
    void reads_without_a_token_are_rejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is4xxClientError());
    }
}
