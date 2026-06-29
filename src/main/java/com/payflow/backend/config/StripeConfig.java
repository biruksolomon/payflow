package com.payflow.backend.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * StripeConfig — reads Stripe credentials from application.yml and
 * initialises the Stripe SDK global API key on startup.
 *
 * Values are injected from:
 *   app.stripe.api-key        → STRIPE_API_KEY env var (sk_test_... / sk_live_...)
 *   app.stripe.public-key     → STRIPE_PUBLIC_KEY env var (pk_test_... / pk_live_...)
 *   app.stripe.webhook-secret → STRIPE_WEBHOOK_SECRET env var (whsec_...)
 */
@Getter
@Configuration
public class StripeConfig {

    @Value("${app.stripe.api-key}")
    private String apiKey;

    @Value("${app.stripe.public-key}")
    private String publicKey;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.stripe.success-url}")
    private String successUrl;

    @Value("${app.stripe.cancel-url}")
    private String cancelUrl;

    /**
     * Runs once on bean initialisation.  Sets Stripe.apiKey globally so
     * every SDK call in the application picks it up automatically.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }
}
