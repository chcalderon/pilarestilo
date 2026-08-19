package com.pilarestilo.privacy.application.usecases;

import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.privacy.application.dto.PersonalDataExportDto;
import com.pilarestilo.privacy.domain.ports.DataConsentRepository;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the shop holds about one customer, in one answer.
 *
 * <p>The Ley 21.719 gives her the right to ask, and the answer has to be intelligible rather than a
 * database dump — so this is composed from the same models the rest of the code uses, not from a
 * query written for the occasion that will drift the moment a table changes.
 *
 * <p>Deliberately not exhaustive in one direction: a boleta is a tax document the shop is obliged
 * to keep for six years, so the export names the documents her orders carry rather than pretending
 * she can have them withdrawn.
 */
@Service
public class ExportMyDataUseCase {

    /** Enough to cover any customer this shop will have; a page is required, not a limit. */
    private static final int EVERYTHING = 500;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CustomerAddressRepository addressRepository;
    private final ReviewRepository reviewRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final DataConsentRepository consentRepository;

    public ExportMyDataUseCase(UserRepository userRepository,
                               OrderRepository orderRepository,
                               CustomerAddressRepository addressRepository,
                               ReviewRepository reviewRepository,
                               ReturnRequestRepository returnRequestRepository,
                               DataConsentRepository consentRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.reviewRepository = reviewRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.consentRepository = consentRepository;
    }

    @Transactional(readOnly = true)
    public PersonalDataExportDto execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found: " + userId));

        List<Order> orders = orderRepository
                .findByCustomerId(userId, PageRequest.of(0, EVERYTHING))
                .getContent();

        List<Map<String, Object>> orderSummaries = orders.stream()
                .map(order -> Map.<String, Object>of(
                        "reference", order.getPublicReference(),
                        "placedAt", order.getCreatedAt(),
                        "status", order.getStatus().name(),
                        "total", order.getTotalAmount().amount(),
                        "currency", order.getTotalAmount().currency(),
                        "items", order.getItems().stream()
                                .map(item -> (Object) Map.of(
                                        "product", item.getProductName(),
                                        "quantity", item.getQuantity()))
                                .toList()))
                .toList();

        List<Map<String, Object>> addresses = addressRepository
                .findByCustomerIdOrderByUpdatedAtDesc(userId).stream()
                .map(address -> Map.<String, Object>of(
                        "recipient", String.valueOf(address.getRecipientName()),
                        "line", String.valueOf(address.getLine1()),
                        "commune", String.valueOf(address.getComuna()),
                        "city", String.valueOf(address.getCity()),
                        "region", String.valueOf(address.getRegion()),
                        "phone", String.valueOf(address.getPhone())))
                .toList();

        List<Map<String, Object>> reviews = reviewRepository.findByUserId(userId).stream()
                .map(review -> Map.<String, Object>of(
                        "rating", review.getRating(),
                        "title", String.valueOf(review.getTitle()),
                        "comment", String.valueOf(review.getComment()),
                        "writtenAt", String.valueOf(review.getCreatedAt())))
                .toList();

        List<Map<String, Object>> returns = returnRequestRepository.findByRequestedBy(userId).stream()
                .map(request -> Map.<String, Object>of(
                        "kind", request.getKind().name(),
                        "status", request.getStatus().name(),
                        "reason", String.valueOf(request.getReason()),
                        "requestedAt", request.getRequestedAt()))
                .toList();

        List<Map<String, Object>> consents = consentRepository.findByUserId(userId).stream()
                .map(consent -> Map.<String, Object>of(
                        "type", consent.getType().name(),
                        "policyVersion", consent.getPolicyVersion(),
                        "acceptedAt", consent.getAcceptedAt(),
                        "revokedAt", String.valueOf(consent.getRevokedAt())))
                .toList();

        return new PersonalDataExportDto(
                Map.of(
                        "email", String.valueOf(user.getEmail()),
                        "fullName", String.valueOf(user.getFullName()),
                        "phone", String.valueOf(user.getPhone()),
                        "registeredAt", String.valueOf(user.getCreatedAt())),
                orderSummaries,
                addresses,
                reviews,
                returns,
                consents);
    }
}
