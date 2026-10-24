package com.wallet.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The page the "Esqueci minha senha" link opens: {@code <WALLET_PUBLIC_URL>/password/reset#token=...},
 * a static page in {@code static/password}, built like {@link ConfirmationPageConfig}'s. The token
 * comes after the {@code #}, which browsers never send, and leaves the address bar as soon as it is
 * read. The page only acts when the person has typed the new password twice and pressed the button
 * (a POST to {@code /api/auth/password/reset}), so mail scanners that open every link change nothing.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordResetPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/password/reset").setViewName("forward:/password/reset.html");
    }
}
