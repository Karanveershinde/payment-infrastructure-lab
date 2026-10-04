package com.payflow.webhook.repository;

import com.payflow.webhook.domain.entity.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, String> {
    Optional<WebhookEventEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
