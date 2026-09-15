package com.pos.inventory.repository;

import com.pos.inventory.model.Order;
import com.pos.inventory.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o.id FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Long> findIdByOrderNumber(@Param("orderNumber") String orderNumber);

    /** Row-locks the order (SELECT ... FOR UPDATE) so concurrent status transitions are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.expiresAt <= :now ORDER BY o.expiresAt")
    List<Long> findIdsByStatusExpiredBy(@Param("status") OrderStatus status, @Param("now") LocalDateTime now);

    boolean existsByIdAndStatusAndExpiresAtLessThanEqual(Long id, OrderStatus status, LocalDateTime now);
}
