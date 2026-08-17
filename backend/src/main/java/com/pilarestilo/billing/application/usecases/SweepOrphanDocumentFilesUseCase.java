package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.billing.infrastructure.storage.SalesDocumentFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Removes uploaded boleta files that no document ever claimed.
 *
 * <p>Uploading is a separate call from registering the boleta, because the folio is typed the
 * moment the document is emitted while the PDF often arrives later. The cost of that split is that
 * abandoning the drawer after choosing a file leaves the file behind with nothing pointing at it.
 *
 * <p>Read-only against the database: it only asks which names are still claimed.
 */
@Service
public class SweepOrphanDocumentFilesUseCase {

    private final SalesDocumentRepository salesDocumentRepository;
    private final SalesDocumentFileStorage fileStorage;
    private final Duration minAge;

    public SweepOrphanDocumentFilesUseCase(
            SalesDocumentRepository salesDocumentRepository,
            SalesDocumentFileStorage fileStorage,
            @Value("${app.documents.orphan-sweep.min-age-hours:24}") long minAgeHours) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.fileStorage = fileStorage;
        this.minAge = Duration.ofHours(minAgeHours);
    }

    @Transactional(readOnly = true)
    public int execute() {
        return fileStorage.deleteOrphans(salesDocumentRepository.findAllStoredFileNames(), minAge);
    }
}
