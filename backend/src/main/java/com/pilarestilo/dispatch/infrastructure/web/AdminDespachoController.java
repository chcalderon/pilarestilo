package com.pilarestilo.dispatch.infrastructure.web;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.usecases.ListDispatchesUseCase;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/despachos")
public class AdminDespachoController {

    private final ListDispatchesUseCase listUseCase;
    private final DispatchRepository dispatchRepository;

    public AdminDespachoController(ListDispatchesUseCase listUseCase,
                                    DispatchRepository dispatchRepository) {
        this.listUseCase = listUseCase;
        this.dispatchRepository = dispatchRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<DispatchDto> list(Pageable pageable) {
        return listUseCase.executeForAdmin(pageable);
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DispatchDto seed(@RequestBody Map<String, String> body) {
        UUID orderId = UUID.fromString(body.get("orderId"));
        if (dispatchRepository.existsByOrderId(orderId)) {
            return dispatchRepository.findByOrderId(orderId).map(DispatchDto::from).orElseThrow();
        }
        return DispatchDto.from(dispatchRepository.save(Dispatch.create(orderId)));
    }
}
