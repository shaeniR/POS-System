package com.pos.inventory.repository;

import com.pos.inventory.model.Payment;
import com.pos.inventory.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    boolean existsByOrderIdAndStatusAndCreatedAtAfter(Long orderId, PaymentStatus status, LocalDateTime createdAfter);
}
