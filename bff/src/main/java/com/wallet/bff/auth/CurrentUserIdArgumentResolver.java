package com.wallet.bff.auth;

import com.wallet.bff.exception.BffException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)
                || isNull(token.getToken().getSubject())) {
            throw unauthenticated();
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            throw unauthenticated();
        }
    }

    private static BffException unauthenticated() {
        return new BffException(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "No authenticated user");
    }
}
