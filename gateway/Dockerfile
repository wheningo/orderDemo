FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -o gateway .

FROM alpine:3.19
WORKDIR /app
COPY --from=build /app/gateway .
EXPOSE 8081
ENTRYPOINT ["./gateway"]