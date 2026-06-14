package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ==================== ORDER REPOSITORY ====================
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserId(Long userId);

    @Query("""
SELECT DISTINCT o
FROM Order o
LEFT JOIN FETCH o.items
WHERE o.user.id = :userId
ORDER BY o.createdAt DESC
""")
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    @Query("""
       SELECT DISTINCT o
       FROM Order o
       LEFT JOIN FETCH o.items
       WHERE o.id = :orderId
       """)
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    List<Order> findByOrderStatus(OrderStatus orderStatus);

    List<Order> findByPaymentStatus(PaymentStatus paymentStatus);

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate")
    List<Order> findOrdersByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("""
       SELECT COUNT(o)
       FROM Order o
       WHERE o.user.id = :userId
       AND o.orderStatus = :status
       """)
    Long countOrdersByUserAndStatus(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status);

    @Query("""
       SELECT o
       FROM Order o
       WHERE o.orderStatus = :status
       AND o.createdAt < :threshold
       """)
    List<Order> findStaleOrders(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold);
}