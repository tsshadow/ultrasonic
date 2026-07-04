# Build stage
FROM golang:1.22-alpine AS builder

WORKDIR /app
COPY apk-hoster/main.go .
RUN go build -o apk-hoster main.go

# Final stage
FROM alpine:latest

WORKDIR /app
COPY --from=builder /app/apk-hoster .
COPY dist ./dist

EXPOSE 8275
CMD ["./apk-hoster"]
