package com.wallet.bff.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;

/**
 * Runs inside the security chain, right after the token is read, so it can count per user.
 * Login and the other {@code /api/auth} routes count per IP with a lower limit: that is where
 * brute force goes, and every attempt costs a BCrypt in the core.
 *
 * <p>Linking a bank and asking for a Pluggy Connect token also count per user against limits of
 * their own, low and over a longer window ({@link RateLimitProperties}), on top of the general one.
 * So does the statement's text search, over the general window: the core decrypts the period to
 * answer each one. So do the routes that ask for the password or a second-factor code again
 * (changing the e-mail or the password, the second factor and its recovery codes, deleting the account): per user, with the sign-in's
 * limit, so an open session is no way around it to guess a password. The export of the user's data
 * ({@code GET /api/me/export}) counts per user against a few a day: each one reads everything they
 * have. The waitlist's public routes
 * ({@code /api/waitlist}) count per IP too, all together, against a low limit over a long window:
 * each sign-up sends an e-mail to an address the caller picked. The health checks never get
 * here: they have a chain of their own.
 *
 * <p>The address comes from {@link ClientAddress}: the connection's own, unless it is one of the
 * configured trusted proxies. A client cannot pick a fresh address per request by writing
 * {@code X-Forwarded-For} itself. Paths are compared decoded, as Spring MVC routes them, so
 * {@code /api/%63onnections} counts as {@code /api/connections}.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String GENERAL = "{\"code\":\"rate_limit.exceeded\",\"message\":\"Too many requests; try again shortly\"}";
    private static final String CONNECTIONS =
            "{\"code\":\"rate_limit.connections\",\"message\":\"Too many attempts to connect a bank; try again later\"}";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ClientAddress clientAddress;
    private final IpPseudonyms pseudonyms;

    /** @throws IllegalArgumentException when a trusted proxy is not an IP or a CIDR range: the BFF does not start */
    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, IpPseudonyms pseudonyms) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.pseudonyms = pseudonyms;
        this.clientAddress = new ClientAddress(properties.trustedProxies());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        if (isWaitlistRoute(path)) {
            if (!acquire("waitlist", address(request), properties.waitlistLimit(), properties.waitlistWindow())) {
                reject(response, GENERAL, properties.waitlistWindow());
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        boolean authRoute = path.startsWith("/api/auth/");
        int limit = authRoute ? properties.authLimit() : properties.defaultLimit();
        if (!acquire(authRoute ? "auth" : "api", authRoute ? address(request) : caller(request), limit, properties.window())) {
            reject(response, GENERAL, properties.window());
            return;
        }
        if (isCredentialRoute(request.getMethod(), path)
                && !acquire("credentials", caller(request), properties.authLimit(), properties.window())) {
            reject(response, GENERAL, properties.window());
            return;
        }
        Optional<RouteLimit> route = routeLimitOf(request, path);
        if (route.isPresent() && !acquire(route.get().name(), caller(request), route.get().limit(), route.get().window())) {
            reject(response, route.get().body(), route.get().window());
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<RouteLimit> routeLimitOf(HttpServletRequest request, String path) {
        if (HttpMethod.GET.matches(request.getMethod()) && "/api/transactions".equals(path)
                && hasText(request.getParameter("q"))) {
            return Optional.of(new RouteLimit("search", properties.searchLimit(), properties.window(), GENERAL));
        }
        if (HttpMethod.GET.matches(request.getMethod()) && "/api/me/export".equals(path)) {
            return Optional.of(new RouteLimit("export", properties.exportLimit(), properties.exportWindow(), GENERAL));
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return Optional.empty();
        }
        return switch (path) {
            case "/api/connections" ->
                    Optional.of(new RouteLimit("link", properties.linkLimit(), properties.linkWindow(), CONNECTIONS));
            case "/api/connections/connect-token" -> Optional.of(new RouteLimit("connect-token",
                    properties.connectTokenLimit(), properties.connectTokenWindow(), CONNECTIONS));
            default -> Optional.empty();
        };
    }

    /** The landing page's waitlist, a counter of its own per address: it is public and each sign-up sends an e-mail. */
    private static boolean isWaitlistRoute(String path) {
        return "/api/waitlist".equals(path) || path.startsWith("/api/waitlist/");
    }

    private static boolean isCredentialRoute(String method, String path) {
        return path.startsWith("/api/me/two-factor")
                || "/api/me/email".equals(path) && HttpMethod.POST.matches(method)
                || "/api/me/password".equals(path) && HttpMethod.PUT.matches(method)
                || "/api/me".equals(path) && HttpMethod.DELETE.matches(method);
    }

    private boolean acquire(String scope, Caller caller, int limit, Duration window) {
        boolean acquired = rateLimiter.tryAcquire(scope + ":" + caller.key(), limit, window);
        if (!acquired) {
            log.warn("[RATE-LIMIT] {}:{} went over {} requests per {}", scope, caller.label(), limit, window);
        }
        return acquired;
    }

    private Caller caller(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            String user = "user:" + token.getToken().getSubject();
            return new Caller(user, user);
        }
        return address(request);
    }

    private Caller address(HttpServletRequest request) {
        String address = clientAddress.of(request);
        return new Caller("ip:" + address, "ip:" + pseudonyms.of(address));
    }

    /** Fixed bodies, nothing from the request in them: nothing to escape. */
    private static void reject(HttpServletResponse response, String body, Duration window) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }

    /**
     * Who is being counted. The key is what the limiter counts under; the label is what the log may
     * show: a user's id, or the pseudonym of an address (L12), never the address.
     */
    private record Caller(String key, String label) {
    }

    private record RouteLimit(String name, int limit, Duration window, String body) {
    }
}
