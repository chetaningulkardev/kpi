package com.example.kpi.repository;

import com.example.kpi.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientIdOrderByCreatedDateDesc(Long recipientId);

    /**
     * The casts are load-bearing on PostgreSQL. Every filter here is optional,
     * and a bare {@code :param is null} gives the database no way to infer the
     * bind's type, so it rejects the statement outright ("could not determine
     * data type of parameter", or "function upper(bytea) does not exist" once it
     * guesses). That made an unfiltered listing — the default the UI asks for —
     * fail instead of returning everything.
     */
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId
              and (cast(:read as boolean) is null or n.read = :read)
              and (cast(:type as string) is null or upper(n.type) = upper(cast(:type as string)))
              and (cast(:fromAt as timestamp) is null or n.createdDate >= :fromAt)
              and (cast(:toAt as timestamp) is null or n.createdDate <= :toAt)
            """)
    Page<Notification> searchByRecipient(
            @Param("recipientId") Long recipientId,
            @Param("read") Boolean read,
            @Param("type") String type,
            @Param("fromAt") java.time.LocalDateTime fromAt,
            @Param("toAt") java.time.LocalDateTime toAt,
            Pageable pageable
    );

    @Query("""
            select distinct n.type from Notification n
            where n.recipient.id = :recipientId
              and n.type is not null
              and n.type <> ''
            order by n.type asc
            """)
    List<String> findDistinctTypesByRecipientId(@Param("recipientId") Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);
}

