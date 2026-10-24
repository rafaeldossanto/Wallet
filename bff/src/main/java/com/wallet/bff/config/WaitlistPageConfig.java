package com.wallet.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The pages the waitlist's e-mailed links open, static pages in {@code static/waitlist}, built like
 * {@link ConfirmationPageConfig}'s: {@code <WALLET_PUBLIC_URL>/waitlist/confirm#token=...} and
 * {@code <WALLET_PUBLIC_URL>/waitlist/unsubscribe?token=...}.
 *
 * <p>Neither page acts on the GET: mail scanners open every link, and the person is on the list
 * (or off it) only after pressing the button, which posts to {@code /api/waitlist/confirm} or
 * {@code /api/waitlist/unsubscribe}. The unsubscribe token is in the query and not after the
 * {@code #} because the mail provider's one-click button posts to that exact URL (RFC 8058).
 */
@Configuration(proxyBeanMethods = false)
public class WaitlistPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/waitlist/confirm").setViewName("forward:/waitlist/confirm.html");
        registry.addViewController("/waitlist/unsubscribe").setViewName("forward:/waitlist/unsubscribe.html");
    }
}
