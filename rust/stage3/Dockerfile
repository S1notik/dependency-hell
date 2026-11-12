FROM rust:1.90-bookworm AS build
WORKDIR /build
COPY Cargo.toml main.rs ./
RUN cargo test --release && cargo build --release

FROM debian:bookworm-slim
COPY --from=build /build/target/release/dependency-hell-graph /graph
EXPOSE 8082
ENTRYPOINT ["/graph"]
