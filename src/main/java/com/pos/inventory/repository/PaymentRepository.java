package com.pos.inventory.repository;

import com.pos.inventory.model.Payment;
import com.pos.inventory.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatusAndCreatedAtAfter(Long orderId, PaymentStatus status, LocalDateTime createdAfter);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :to, p.message = :message, p.completedAt = :now " +
            "WHERE p.order.id = :orderId AND p.status = :from")
    int updateStatusForOrder(@Param("orderId") Long orderId,
                             @Param("from") PaymentStatus from,
                             @Param("to") PaymentStatus to,
                             @Param("message") String message,
                             @Param("now") LocalDateTime now);
}
