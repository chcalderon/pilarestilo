package com.pilarestilo.returns.application.usecases;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.mappers.ReturnRequestMapper;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListReturnsUseCase {

    private final ReturnRequestRepository returnRequestRepository;

    public ListReturnsUseCase(ReturnRequestRepository returnRequestRepository) {
        this.returnRequestRepository = returnRequestRepository;
    }

    /**
     * @param openOnly ordered by deadline when true: what has to be paid soonest comes first, which
     *                 is the only ordering a legal countdown makes sense in
     */
    @Transactional(readOnly = true)
    public Page<ReturnRequestDto> execute(boolean openOnly, Pageable pageable) {
        Page<com.pilarestilo.returns.domain.model.ReturnRequest> page = openOnly
                ? returnRequestRepository.findOpenByDeadline(pageable)
                : returnRequestRepository.findAll(pageable);
        return page.map(ReturnRequestMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestDto> byOrder(UUID orderId) {
        return returnRequestRepository.findAllByOrderId(orderId).stream()
                .map(ReturnRequestMapper::toDto)
                .toList();
    }

    /** What Mi Cuenta shows: everything this customer has asked for. */
    @Transactional(readOnly = true)
    public List<ReturnRequestDto> mine(UUID userId) {
        return returnRequestRepository.findByRequestedBy(userId).stream()
                .map(ReturnRequestMapper::toDto)
                .toList();
    }
}
