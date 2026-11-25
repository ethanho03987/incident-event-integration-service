package com.ethanho.incidents.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA writes the implementation of this interface at runtime.
 * Methods like {@code findByEventId} are turned into SQL from their names.
 */
public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    Optional<IncidentEvent> findByEventId(String eventId);

    Page<IncidentEvent> findByStatus(EventStatus status, Pageable pageable);

    /** Events saved but never confirmed on RabbitMQ, oldest first. Used by the republisher. */
    List<IncidentEvent> findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(EventStatus status, Instant before, Pageable pageable);

    /**
     * Conditional status change: only moves {@code from -> to} if the row is still in {@code from}.
     * Returns the number of rows changed (0 or 1).
     *
     * <p>This matters because the consumer can finish processing (PROCESSED) before the publisher
     * gets round to marking the row QUEUED. A plain update would overwrite PROCESSED with QUEUED.
     *
     * <p>It deliberately does <i>not</i> bump {@code version}: QUEUED is only informational, and bumping
     * it would make a consumer that is mid-way through processing fail its optimistic-lock check and
     * dispatch the event a second time on retry.
     */
    @Modifying
    @Transactional
    @Query("update IncidentEvent e set e.status = :to where e.id = :id and e.status = :from")
    int transitionStatus(@Param("id") Long id, @Param("from") EventStatus from, @Param("to") EventStatus to);

    @Query("select e.status as status, count(e) as count from IncidentEvent e group by e.status")
    List<StatusCount> countByStatus();

    /** Interface-based projection for the group-by query above. */
    interface StatusCount {
        EventStatus getStatus();
        long getCount();
    }
}
