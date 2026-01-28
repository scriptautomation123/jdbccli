FROM mcr.microsoft.com/devcontainers/base:bookworm

# Remove expired Yarn repository to prevent APT errors
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
