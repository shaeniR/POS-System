package com.pos.inventory.repository;

import com.pos.inventory.model.OrderItem;
import com.pos.inventory.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Rows of [productId (Long), total quantity (Long)] across all orders in the given status. */
    @Query("SELECT oi.product.id, SUM(oi.quantity) FROM OrderItem oi WHERE oi.order.status = :status GROUP BY oi.product.id")
    List<Object[]> sumQuantityByProductForStatus(@Param("status") OrderStatus status);
}
