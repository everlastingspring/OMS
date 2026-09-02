package com.oms.audit.service;

import com.oms.audit.document.ActivityHistory;
import com.oms.audit.document.OrderAuditLog;
import com.oms.audit.repository.ActivityHistoryRepository;
import com.oms.audit.repository.ApplicationLogRepository;
import com.oms.audit.repository.OrderAuditLogRepository;
import com.oms.audit.service.impl.AuditServiceImpl;
import com.oms.common.event.EventType;
import com.oms.common.event.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditServiceImpl")
class AuditServiceImplTest {

    @Mock
    private OrderAuditLogRepository auditLogRepository;

    @Mock
    private ActivityHistoryRepository activityHistoryRepository;

    @Mock
    private ApplicationLogRepository applicationLogRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    private OrderEvent sampleEvent() {
        OrderEvent event = new OrderEvent();
        event.setEventId("evt-001");
        event.setEventType(EventType.ORDER_CREATED);
        event.setOrderId(42L);
        event.setOrderNumber("ORD-001");
        event.setUserId(7L);
        event.setTotalAmount(new BigDecimal("999.00"));
        event.setPreviousStatus(null);
        event.setCurrentStatus("PENDING");
        event.setOccurredAt(Instant.parse("2026-01-01T10:00:00Z"));
        return event;
    }

    @Test
    @DisplayName("saves audit log, activity history, and application log for a new event")
    void recordOrderEvent_newEvent_savesAllThreeDocuments() {
        OrderEvent event = sampleEvent();
        when(auditLogRepository.save(any(OrderAuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.recordOrderEvent(event);

        verify(auditLogRepository).save(any(OrderAuditLog.class));
        verify(activityHistoryRepository).save(any(ActivityHistory.class));
        verify(applicationLogRepository).save(any());
    }

    @Test
    @DisplayName("maps all event fields correctly into the audit log document")
    void recordOrderEvent_mapsFieldsCorrectly() {
        OrderEvent event = sampleEvent();
        when(auditLogRepository.save(any(OrderAuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.recordOrderEvent(event);

        ArgumentCaptor<OrderAuditLog> logCaptor = ArgumentCaptor.forClass(OrderAuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        OrderAuditLog saved = logCaptor.getValue();

        assertThat(saved.getEventId()).isEqualTo("evt-001");
        assertThat(saved.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(saved.getOrderId()).isEqualTo(42L);
        assertThat(saved.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("999.00");
        assertThat(saved.getCurrentStatus()).isEqualTo("PENDING");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    @DisplayName("maps event fields correctly into the activity history document")
    void recordOrderEvent_mapsActivityHistoryCorrectly() {
        OrderEvent event = sampleEvent();
        when(auditLogRepository.save(any(OrderAuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.recordOrderEvent(event);

        ArgumentCaptor<ActivityHistory> captor = ArgumentCaptor.forClass(ActivityHistory.class);
        verify(activityHistoryRepository).save(captor.capture());
        ActivityHistory saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getEntityType()).isEqualTo("ORDER");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(saved.getDetail()).contains("ORD-001").contains("PENDING");
    }

    @Test
    @DisplayName("swallows DuplicateKeyException on redelivery without propagating or double-saving")
    void recordOrderEvent_duplicateEvent_skipsWithoutError() {
        OrderEvent event = sampleEvent();
        when(auditLogRepository.save(any(OrderAuditLog.class)))
                .thenThrow(new DuplicateKeyException("duplicate eventId"));

        assertThatNoException().isThrownBy(() -> auditService.recordOrderEvent(event));

        verify(activityHistoryRepository, never()).save(any());
        verify(applicationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAuditTrailForOrder delegates to the repository and returns its result")
    void getAuditTrailForOrder_delegatesToRepository() {
        OrderAuditLog log1 = new OrderAuditLog();
        log1.setOrderId(42L);
        when(auditLogRepository.findByOrderIdOrderByOccurredAtDesc(42L))
                .thenReturn(Collections.singletonList(log1));

        List<OrderAuditLog> result = auditService.getAuditTrailForOrder(42L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getAuditTrailForOrder returns empty list when no events exist")
    void getAuditTrailForOrder_noEvents_returnsEmpty() {
        when(auditLogRepository.findByOrderIdOrderByOccurredAtDesc(999L))
                .thenReturn(Collections.emptyList());

        assertThat(auditService.getAuditTrailForOrder(999L)).isEmpty();
    }

    @Test
    @DisplayName("getActivityForUser delegates to the repository and returns its result")
    void getActivityForUser_delegatesToRepository() {
        ActivityHistory a1 = new ActivityHistory();
        a1.setUserId(7L);
        ActivityHistory a2 = new ActivityHistory();
        a2.setUserId(7L);
        when(activityHistoryRepository.findByUserIdOrderByTimestampDesc(7L))
                .thenReturn(Arrays.asList(a1, a2));

        List<ActivityHistory> result = auditService.getActivityForUser(7L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getActivityForUser returns empty list when no activity exists")
    void getActivityForUser_noActivity_returnsEmpty() {
        when(activityHistoryRepository.findByUserIdOrderByTimestampDesc(999L))
                .thenReturn(Collections.emptyList());

        assertThat(auditService.getActivityForUser(999L)).isEmpty();
    }
}
