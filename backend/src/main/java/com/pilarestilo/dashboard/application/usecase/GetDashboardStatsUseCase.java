package com.pilarestilo.dashboard.application.usecase;

import com.pilarestilo.dashboard.application.port.out.DashboardStatsRepository;
import com.pilarestilo.dashboard.domain.model.DashboardStats;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class GetDashboardStatsUseCase {

    private final DashboardStatsRepository repo;

    public GetDashboardStatsUseCase(DashboardStatsRepository repo) {
        this.repo = repo;
    }

    public DashboardStats execute(AuthenticatedUser caller) {
        return switch (caller.role()) {
            case ADMIN, SUPERVISOR -> repo.getAdminStats();
            case SELLER -> repo.getSellerStats(caller.id());
            case DESPACHADOR -> repo.getDespachadorStats(caller.id());
            case ADMINISTRACION -> repo.getAdministracionStats();
            case CUSTOMER -> throw new IllegalStateException("CUSTOMER has no dashboard");
        };
    }
}
