package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes SCHEDULED publications when their scheduled_at is reached. Not @Transactional: each
 * dispatch is its own @Transactional call on PublicationService — same reasoning as
 * PublishProductsBatchUseCase. A row more than {@code maxLatenessMinutes} overdue (e.g. the server
 * was down over its window) is failed rather than posted at a surprising time.
 */
@Component
public class PublishDueScheduledPublicationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishDueScheduledPublicationsUseCase.class);

    private final PublicationJpaRepository publicationRepository;
    private final PublicationService publicationService;
    private final Clock clock;
    private final long maxLatenessMinutes;

    @Autowired
    public PublishDueScheduledPublicationsUseCase(PublicationJpaRepository publicationRepository,
                                                 PublicationService publicationService,
                                                 @Value("${app.social-publishing.schedule.max-lateness-minutes:360}") long maxLatenessMinutes) {
        this(publicationRepository, publicationService, Clock.systemUTC(), maxLatenessMinutes);
    }

    PublishDueScheduledPublicationsUseCase(PublicationJpaRepository publicationRepository,
                                          PublicationService publicationService,
                                          Clock clock,
                                          long maxLatenessMinutes) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
        this.clock = clock;
        this.maxLatenessMinutes = maxLatenessMinutes;
    }

    public int execute() {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minus(Duration.ofMinutes(maxLatenessMinutes));
        List<PublicationEntity> due = publicationRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(PublicationStatus.SCHEDULED, now);
        int handled = 0;
        for (PublicationEntity p : due) {
            try {
                if (p.getScheduledAt() != null && p.getScheduledAt().isBefore(staleBefore)) {
                    publicationService.markScheduleWindowMissed(p.getId());
                } else {
                    publicationService.dispatch(p.getId(), null);
                }
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Scheduled publication {} could not be handled: {}", p.getId(), ex.getMessage());
            }
        }
        return handled;
    }
}
