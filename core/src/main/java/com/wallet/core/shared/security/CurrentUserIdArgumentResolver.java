package com.wallet.core.shared.security;

import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

import static java.util.Objects.isNull;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw unauthenticated();
        }
        String subject = jwtAuthentication.getToken().getSubject();
        if (isNull(subject)) {
            throw unauthenticated();
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw unauthenticated();
        }
    }

    private static DomainException unauthenticated() {
        return new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "No authenticated user");
    }
}
