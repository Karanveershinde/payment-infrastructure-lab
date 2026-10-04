package com.payflow.payment.repository;

import com.payflow.payment.domain.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, String> {
    List<RefundEntity> findByPaymentIdOrderByCreatedAtDesc(String paymentId);
}
