package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The single dispatch path for every publication: immediate (APPROVED), scheduled (SCHEDULED),
 * and automatic retries (RETRY_SCHEDULED) all become due rows here. Not @Transactional: each
 * dispatch is its own @Transactional call on PublicationService, so one failure does not roll back
 * the others. A row left in PUBLISHING past {@code stuckPublishingMinutes} (server crashed
 * mid-dispatch) is failed as DISPATCH_INTERRUPTED and NOT re-dispatched.
 */
@Component
public class DispatchDuePublicationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchDuePublicationsUseCase.class);

    private final PublicationJpaRepository publicationRepository;
    private final PublicationService publicationService;
    private final Clock clock;
    private final long maxLatenessMinutes;
    private final int batchSize;
    private final int stuckPublishingMinutes;

    @Autowired
    public DispatchDuePublicationsUseCase(
            PublicationJpaRepository publicationRepository,
            PublicationService publicationService,
            @Value("${app.social-publishing.dispatch.max-lateness-minutes:360}") long maxLatenessMinutes,
            @Value("${app.social-publishing.dispatch.batch-size:25}") int batchSize,
            @Value("${app.social-publishing.dispatch.stuck-publishing-minutes:15}") int stuckPublishingMinutes) {
        this(publicationRepository, publicationService, Clock.systemUTC(),
                maxLatenessMinutes, batchSize, stuckPublishingMinutes);
    }

    DispatchDuePublicationsUseCase(PublicationJpaRepository publicationRepository,
                                  PublicationService publicationService,
                                  Clock clock,
                                  long maxLatenessMinutes,
                                  int batchSize,
                                  int stuckPublishingMinutes) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
        this.clock = clock;
        this.maxLatenessMinutes = maxLatenessMinutes;
        this.batchSize = batchSize;
        this.stuckPublishingMinutes = stuckPublishingMinutes;
    }

    public int execute() {
        Instant now = Instant.now(clock);
        int handled = 0;

        Instant stuckBefore = now.minus(Duration.ofMinutes(stuckPublishingMinutes));
        for (PublicationEntity p : publicationRepository.findByStatusAndUpdatedAtLessThan(
                PublicationStatus.PUBLISHING, stuckBefore)) {
            try {
                publicationService.markDispatchInterrupted(p.getId());
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Could not recover stuck publication {}: {}", p.getId(), ex.getMessage());
            }
        }

        Instant staleBefore = now.minus(Duration.ofMinutes(maxLatenessMinutes));
        for (PublicationEntity p : publicationRepository.findDueForDispatch(now, PageRequest.of(0, batchSize))) {
            try {
                if (p.getStatus() == PublicationStatus.SCHEDULED
                        && p.getScheduledAt() != null && p.getScheduledAt().isBefore(staleBefore)) {
                    publicationService.markScheduleWindowMissed(p.getId());
                } else {
                    publicationService.dispatchFromWorker(p.getId());
                }
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Publication {} could not be dispatched: {}", p.getId(), ex.getMessage());
            }
        }
        return handled;
    }
}
