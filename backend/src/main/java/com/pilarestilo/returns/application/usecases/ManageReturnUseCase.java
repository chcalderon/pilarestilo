package com.pilarestilo.returns.application.usecases;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.mappers.ReturnRequestMapper;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The three transitions that move a return along without touching money or stock: approving,
 * refusing, and recording that the garment arrived.
 *
 * <p>They live together because none of them has anything to decide — the rules are in the
 * aggregate, including the one that matters most: a retracto cannot be refused. Splitting three
 * one-line delegations into three classes would only spread that out.
 */
@Service
public class ManageReturnUseCase {

    private final ReturnRequestRepository returnRequestRepository;

    public ManageReturnUseCase(ReturnRequestRepository returnRequestRepository) {
        this.returnRequestRepository = returnRequestRepository;
    }

    @Transactional
    public ReturnRequestDto approve(UUID returnId) {
        ReturnRequest request = load(returnId);
        request.approve();
        return ReturnRequestMapper.toDto(returnRequestRepository.save(request));
    }

    @Transactional
    public ReturnRequestDto reject(UUID returnId, String note) {
        ReturnRequest request = load(returnId);
        request.reject(note);
        return ReturnRequestMapper.toDto(returnRequestRepository.save(request));
    }

    /** The garment arrived. It goes into reconditioning; nothing returns to stock here. */
    @Transactional
    public ReturnRequestDto receive(UUID returnId) {
        ReturnRequest request = load(returnId);
        request.receive();
        return ReturnRequestMapper.toDto(returnRequestRepository.save(request));
    }

    private ReturnRequest load(UUID returnId) {
        return returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new DomainException("Return not found: " + returnId));
    }
}
