package com.pilarestilo.dashboard.infrastructure.web;

import com.pilarestilo.dashboard.application.usecase.GetDashboardStatsUseCase;
import com.pilarestilo.dashboard.infrastructure.web.dto.DashboardStatsResponse;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final GetDashboardStatsUseCase useCase;

    public DashboardController(GetDashboardStatsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','SELLER','DESPACHADOR','ADMINISTRACION')")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        var stats = useCase.execute(caller);
        return ResponseEntity.ok(DashboardStatsResponse.from(stats));
    }
}
