package com.pilarestilo.privacy.application.usecases;

import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Carries out a deletion request, or refuses it with a reason.
 *
 * <p>Anonymising, never deleting. Two obligations hold at the same time: the Ley 21.719 gives the
 * customer the right to be removed, and the tax law makes the shop keep the boleta for six years.
 * They reconcile because a boleta copies the buyer's name and email when it is issued — that
 * snapshot exists for this moment.
 *
 * <p>What goes: the account's identifying fields and the delivery addresses. What stays: orders,
 * payments, documents and reviews, all of which now point at somebody nobody can identify.
 */
@Service
public class ResolveDeletionRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResolveDeletionRequestUseCase.class);

    private final DataDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;

    public ResolveDeletionRequestUseCase(DataDeletionRequestRepository deletionRepository,
                                         UserRepository userRepository,
                                         CustomerAddressRepository addressRepository) {
        this.deletionRepository = deletionRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public DataDeletionRequest anonymise(UUID requestId, UUID resolvedBy) {
        DataDeletionRequest request = load(requestId);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new DomainException("User not found: " + request.getUserId()));

        /*
         * The addresses go entirely: unlike an order, a saved address has no legal reason to
         * survive the person, and it is the most directly identifying thing the shop holds.
         */
        addressRepository.findByCustomerIdOrderByUpdatedAtDesc(user.getId())
                .forEach(address -> addressRepository.deleteByIdAndCustomerId(address.getId(), user.getId()));

        /*
         * Reviews are left alone on purpose. A product's rating is built from them, so removing one
         * would silently rewrite what other customers see - and they carry no name of their own:
         * the author is read from the user row, which is the row being anonymised here.
         */

        user.anonymise();
        userRepository.save(user);

        request.markAnonymised(resolvedBy);
        log.info("Anonymised user {} on deletion request {}", user.getId(), requestId);
        return deletionRepository.save(request);
    }

    @Transactional
    public DataDeletionRequest refuse(UUID requestId, String reason, UUID resolvedBy) {
        DataDeletionRequest request = load(requestId);
        request.refuse(reason, resolvedBy);
        return deletionRepository.save(request);
    }

    private DataDeletionRequest load(UUID requestId) {
        return deletionRepository.findById(requestId)
                .orElseThrow(() -> new DomainException("Deletion request not found: " + requestId));
    }
}
