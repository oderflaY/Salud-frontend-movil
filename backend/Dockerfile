# syntax=docker/dockerfile:1
#
# Dos etapas. Las dependencias y `target/` viven en cachés de BuildKit
# (`--mount=type=cache`): un cambio en el código ya no recompila todos los
# crates, sin depender de herramientas externas como cargo-chef (cuya cadena
# de dependencias exigía un rustc más nuevo que el de la imagen).
#
# Se usa el tag flotante `1-bookworm` porque varias dependencias de crates.io
# ya exigen la edición 2024; fijar un patch viejo obliga a perseguir cada nuevo
# mínimo. Si se necesita reproducibilidad exacta, este es el lugar.
FROM rust:1-bookworm AS builder
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/usr/local/cargo/git \
    --mount=type=cache,target=/app/target \
    cargo build --release --locked --bin backend \
    && cp target/release/backend /usr/local/bin/backend

FROM debian:bookworm-slim AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home salud
COPY --from=builder /usr/local/bin/backend /usr/local/bin/backend
# Sin privilegios de root: una falla en el servicio no da control del contenedor.
USER salud
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/backend"]
