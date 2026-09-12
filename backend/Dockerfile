# Build simple de dos etapas. Se descartó cargo-chef (capa de caché de
# dependencias) porque su propia cadena de dependencias en crates.io exige,
# en el momento de escribir esto, un rustc más nuevo que el que fijamos aquí
# — un problema de las herramientas de build, no de nuestro código — y no
# vale la pena acoplar el Dockerfile a esa carrera de versiones por una
# optimización de caché. El costo es recompilar dependencias en cada cambio
# de código; aceptable para el tamaño de este servicio.
#
# Se usa el tag flotante `1-bookworm` (en vez de fijar un patch exacto)
# porque, en la práctica reciente, varias dependencias de crates.io empezaron
# a exigir la edición 2024 de Cargo, y pinear una versión vieja fuerza a
# perseguir manualmente cada nuevo mínimo. El trade-off es reproducibilidad
# de build a cambio de no romperse con el ecosistema; si se necesita fijar
# una versión exacta más adelante, este es el lugar.
FROM rust:1-bookworm AS builder
WORKDIR /app
COPY . .
RUN cargo build --release --bin backend

FROM debian:bookworm-slim AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/backend /usr/local/bin/backend
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/backend"]
