package com.wallet.bff.controller;

import com.wallet.bff.client.CoreApi;
import com.wallet.bff.config.WebhookProperties;
import com.wallet.bff.config.WebhookSecret;
import com.wallet.bff.exception.BffException;
import com.wallet.bff.ratelimit.ClientAddress;
import com.wallet.bff.ratelimit.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pluggy's webhook: public, with no session, and NOT signed, so what arrives is a hint and nothing
 * more. It gets in only with the secret in {@code X-Wallet-Webhook-Secret} and, when a list is set,
 * from an allowed address; the rate limit counts it per address before this runs. The body goes to
 * the core exactly as it came (the core reads three fields of it and asks Pluggy for the rest) and
 * the answer is 202 at once.
 *
 * <p>The bean only exists with {@code wallet.webhooks.enabled=true}: otherwise the route is a 404.
 */
@RestController
@RequestMapping("/api/webhooks")
@ConditionalOnProperty(prefix = "wallet.webhooks", name = "enabled", havingValue = "true")
public class WebhookController {

    private final CoreApi coreApi;
    private final WebhookSecret secret;
    private final ClientAddress clientAddress;
    private final ClientAddress.Allowlist allowedAddresses;

    public WebhookController(CoreApi coreApi, WebhookSecret secret, WebhookProperties properties, RateLimitProperties rateLimits) {
        this.coreApi = coreApi;
        this.secret = secret;
        this.clientAddress = new ClientAddress(rateLimits.trustedProxies());
        this.allowedAddresses = new ClientAddress.Allowlist(properties.allowedIps());
    }

    @PostMapping("/pluggy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pluggy(HttpServletRequest request,
                       @RequestHeader(name = WebhookSecret.HEADER, required = false) String presentedSecret,
                       @RequestBody byte[] body) {
        if (!allowedAddresses.allows(clientAddress.of(request))) {
            throw new BffException(HttpStatus.FORBIDDEN, "webhook.forbidden", "This address may not call the webhook");
        }
        if (!secret.matches(presentedSecret)) {
            throw new BffException(HttpStatus.UNAUTHORIZED, "webhook.unauthorized", "Missing or wrong webhook secret");
        }
        coreApi.pluggyWebhook(body);
    }
}
