package com.pilarestilo.payment.infrastructure.jobs;

import com.pilarestilo.payment.application.usecases.AutoCancelPendingBankTransferUseCase;
import com.pilarestilo.systemsettings.domain.events.BankTransferSettingsChangedEvent;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransferAutoCancelSchedulerTest {

    @Mock
    TaskScheduler taskScheduler;

    @Mock
    SystemSettingsRepository settingsRepo;

    @Mock
    AutoCancelPendingBankTransferUseCase useCase;

    @InjectMocks
    BankTransferAutoCancelScheduler scheduler;

    private SystemSettings enabledSettings() {
        return SystemSettings.createDefault();
    }

    @SuppressWarnings("unchecked")
    private ScheduledFuture<Object> mockFuture() {
        return mock(ScheduledFuture.class);
    }

    @Test
    void schedule_when_enabled_registers_task() {
        when(settingsRepo.get()).thenReturn(enabledSettings());
        doReturn(mockFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        scheduler.schedule();

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void schedule_when_disabled_does_not_register_task() {
        SystemSettings settings = mock(SystemSettings.class);
        when(settings.isBankTransferAutoCancelEnabled()).thenReturn(false);
        when(settingsRepo.get()).thenReturn(settings);

        scheduler.schedule();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void reschedule_cancels_existing_future_before_registering_new() {
        when(settingsRepo.get()).thenReturn(enabledSettings());
        ScheduledFuture<Object> firstFuture = mockFuture();
        ScheduledFuture<Object> secondFuture = mockFuture();
        doReturn(firstFuture, secondFuture).when(taskScheduler)
                .schedule(any(Runnable.class), any(Trigger.class));

        scheduler.schedule();
        scheduler.schedule();

        verify(firstFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void onSettingsChanged_reschedules_task() {
        when(settingsRepo.get()).thenReturn(enabledSettings());
        doReturn(mockFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        scheduler.onSettingsChanged(new BankTransferSettingsChangedEvent(true, "0 */15 * * * *", 30));

        verify(settingsRepo).get();
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void onSettingsChanged_disabled_cancels_existing_task() {
        when(settingsRepo.get()).thenReturn(enabledSettings());
        ScheduledFuture<Object> existingFuture = mockFuture();
        doReturn(existingFuture).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        scheduler.schedule();

        SystemSettings disabledSettings = mock(SystemSettings.class);
        when(disabledSettings.isBankTransferAutoCancelEnabled()).thenReturn(false);
        when(settingsRepo.get()).thenReturn(disabledSettings);

        scheduler.onSettingsChanged(new BankTransferSettingsChangedEvent(false, "0 */15 * * * *", 30));

        verify(existingFuture).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }
}
