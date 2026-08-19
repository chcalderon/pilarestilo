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

import java.util.LinkedHashMap;
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
                .map(address -> fields(
                        "recipient", address.getRecipientName(),
                        "line", address.getLine1(),
                        "commune", address.getComuna(),
                        "city", address.getCity(),
                        "region", address.getRegion(),
                        "phone", address.getPhone()))
                .toList();

        List<Map<String, Object>> reviews = reviewRepository.findByUserId(userId).stream()
                .map(review -> fields(
                        "rating", review.getRating(),
                        "title", review.getTitle(),
                        "comment", review.getComment(),
                        "writtenAt", review.getCreatedAt()))
                .toList();

        List<Map<String, Object>> returns = returnRequestRepository.findByRequestedBy(userId).stream()
                .map(request -> fields(
                        "kind", request.getKind().name(),
                        "status", request.getStatus().name(),
                        "reason", request.getReason(),
                        "requestedAt", request.getRequestedAt()))
                .toList();

        List<Map<String, Object>> consents = consentRepository.findByUserId(userId).stream()
                .map(consent -> fields(
                        "type", consent.getType().name(),
                        "policyVersion", consent.getPolicyVersion(),
                        "acceptedAt", consent.getAcceptedAt(),
                        "revokedAt", consent.getRevokedAt()))
                .toList();

        return new PersonalDataExportDto(
                fields(
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "phone", user.getPhone(),
                        "registeredAt", user.getCreatedAt()),
                orderSummaries,
                addresses,
                reviews,
                returns,
                consents);
    }

    /**
     * Builds one section of the copy, keeping the order the keys were written in and letting a
     * missing value stay missing. {@code Map.of} refuses nulls, and wrapping them in
     * {@code String.valueOf} was handing the customer the literal text "null" where she has no
     * phone and no revocation - a document about her that reads like a stack trace.
     */
    private static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> section = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            section.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return section;
    }
}
