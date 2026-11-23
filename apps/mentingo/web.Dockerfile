FROM node:22.15.0-alpine AS base
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
ENV TURBO_TELEMETRY_DISABLED=1
RUN npm install -g pnpm@10.22.0 turbo@2.3.3
WORKDIR /app

FROM base AS pruner
COPY . .
RUN turbo prune web --docker

FROM base AS builder
COPY --from=pruner /app/out/json/ .
RUN pnpm install --frozen-lockfile
COPY --from=pruner /app/out/full/ .

ARG VITE_API_URL
ARG VITE_SENTRY_DSN
ARG VITE_POSTHOG_KEY
ARG VITE_POSTHOG_HOST
ARG VITE_TEST

ENV NODE_OPTIONS=--max-old-space-size=6144
RUN sed -i 's/remix({/remix({ basename: "\/mentingo",/' apps/web/vite.config.ts
RUN pnpm build --filter=web

FROM nginx:1.27.1
COPY ./apps/web/nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /app/apps/web/build/client /usr/share/nginx/html
