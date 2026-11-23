FROM oven/bun:1.4.0
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN git clone --depth 1 --branch release https://github.com/trycompai/crm.git .
ENV DATABASE_URL=postgres://postgres:postgres@crm-db:5432/crm
RUN bun install
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 3000 3001
ENTRYPOINT ["/entrypoint.sh"]
