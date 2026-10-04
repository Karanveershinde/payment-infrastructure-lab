package com.payflow.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private String webhookServiceUrl = "http://localhost:8082/api/v1/webhooks";
    private String webhookSecret = "whsec_test_secret_key_1234567890";
    private boolean asyncWebhooksEnabled = true;
    private long webhookDelayMs = 200;

    private Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        private double successRate = 0.95;
        private int minLatencyMs = 50;
        private int maxLatencyMs = 100;
        private boolean forceOutage = false;
        private boolean forceTimeout = false;
        private boolean force500Error = false;
        private String forcedErrorCode = null;
    }
}
