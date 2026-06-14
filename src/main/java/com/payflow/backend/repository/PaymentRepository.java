package com.payflow.backend.repository;


import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ==================== PAYMENT REPOSITORY ====================
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByUserId(Long userId);

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    @Query("""
       SELECT p
       FROM Payment p
       WHERE p.paymentStatus = :status
       AND p.createdAt < :threshold
       AND p.retryCount < :maxRetries
       """)
    List<Payment> findPaymentsForRetry(
            @Param("status") PaymentStatus status,
            @Param("threshold") LocalDateTime threshold,
            @Param("maxRetries") Integer maxRetries);



    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = :status " +
            "AND p.processedAt >= :startDate AND p.processedAt <= :endDate")
    Optional<java.math.BigDecimal> getTotalSuccessfulPayments(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}