FROM golang:1.23-alpine AS build
WORKDIR /service
COPY go.mod main.go ./
RUN CGO_ENABLED=0 go build -o /registry .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /registry /registry
EXPOSE 8081
ENTRYPOINT ["/registry"]
