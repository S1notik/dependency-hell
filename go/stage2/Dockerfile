FROM golang:1.23-alpine AS build
WORKDIR /service
COPY go.mod main.go stage3.go main_test.go stage3_test.go ./
RUN go test ./... && CGO_ENABLED=0 go build -o /registry .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /registry /registry
EXPOSE 8081
ENTRYPOINT ["/registry"]
