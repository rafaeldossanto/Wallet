# syntax=docker/dockerfile:1
# Caddy image: the landing page (site/) and the Flutter web build, both baked in, plus the Caddyfile.
# The build context is the repository root (it needs app/ and site/); compose passes WALLET_DOMAIN:
#   docker build -f deploy/caddy/Dockerfile --build-arg WALLET_DOMAIN=example.com .
#
# The domain is part of the build: the app is compiled with the API address and the landing's
# config.js is generated with it. A new domain means a new build.

# The Flutter build. The cirruslabs/flutter image has no 3.47.x tag, so Flutter is cloned at the
# exact version the app is built with.
FROM debian:bookworm-slim AS flutter
ARG FLUTTER_VERSION=3.47.6
ARG WALLET_DOMAIN
RUN test -n "$WALLET_DOMAIN" || (echo "WALLET_DOMAIN build arg is required" >&2; exit 1)
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl unzip xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch "$FLUTTER_VERSION" https://github.com/flutter/flutter.git /opt/flutter
ENV PATH=/opt/flutter/bin:$PATH
RUN git config --global --add safe.directory /opt/flutter \
    && flutter config --no-analytics --enable-web \
    && flutter precache --web
WORKDIR /src
COPY app/ ./
RUN --mount=type=cache,target=/root/.pub-cache \
    flutter pub get \
    && flutter build web --release --no-web-resources-cdn \
        --dart-define=WALLET_BFF_URL=https://app.${WALLET_DOMAIN}

FROM caddy:2
ARG WALLET_DOMAIN
RUN test -n "$WALLET_DOMAIN" || (echo "WALLET_DOMAIN build arg is required" >&2; exit 1)
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY site /srv/site
# The landing posts to the app's address: replaces the development config.js.
RUN printf 'window.WALLET_API_URL = "https://app.%s";\n' "$WALLET_DOMAIN" > /srv/site/config.js
COPY --from=flutter /src/build/web /srv/app
