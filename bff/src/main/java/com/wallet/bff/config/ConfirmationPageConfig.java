package com.wallet.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The page the e-mailed links open: {@code <WALLET_PUBLIC_URL>/email/confirm#token=...}, a static
 * page in {@code static/email}. A page of the BFF and not a route of the web app, so the link works
 * wherever the e-mail is read, on a phone with only the Android app included, and whether or not the
 * web app is deployed.
 *
 * <p>The token comes after the {@code #}, which browsers never send: it stays out of the access logs
 * here and in any proxy ahead. The page only confirms when the button is pressed (a POST to
 * {@code /api/auth/email/confirm}), so mail scanners that open every link confirm nothing.
 */
@Configuration(proxyBeanMethods = false)
public class ConfirmationPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/email/confirm").setViewName("forward:/email/confirm.html");
    }
}
