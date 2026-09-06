package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.MetricsRefreshScope;
import com.pilarestilo.publication.application.usecases.RefreshMetricsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshRecentMetricsSchedulerTest {

    @Mock RefreshMetricsUseCase useCase;

    @Test
    void runs_the_use_case_with_a_recent_days_scope() {
        when(useCase.execute(any()))
                .thenReturn(new RefreshMetricsUseCase.MetricsRefreshResult(2, 1));

        new RefreshRecentMetricsScheduler(useCase, 30).run();

        ArgumentCaptor<MetricsRefreshScope> captor = ArgumentCaptor.forClass(MetricsRefreshScope.class);
        verify(useCase).execute(captor.capture());
        MetricsRefreshScope.RecentDays scope = (MetricsRefreshScope.RecentDays) captor.getValue();
        assertEquals(30, scope.days());
    }
}
