FROM chainmakerofficial/chainmaker:v2.3.10 AS chainmaker
FROM golang:1.25-bookworm AS contract
WORKDIR /src
ARG GO_MODULE_PROXY=https://goproxy.cn|https://proxy.golang.org
ENV GOPROXY=${GO_MODULE_PROXY} GOTOOLCHAIN=local
COPY contracts/trace_anchor/go.mod contracts/trace_anchor/go.sum ./
RUN go mod download
COPY contracts/trace_anchor/*.go ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o /out/trace_anchor . \
    && apt-get update && apt-get install -y --no-install-recommends p7zip-full \
    && cd /out && 7z a -t7z trace_anchor.7z trace_anchor

FROM eclipse-temurin:11-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-cryptography python3-yaml openssl ca-certificates uuid-runtime \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /root/.fisco && ln -s /usr/bin/openssl /root/.fisco/tassl-1.1.1b
COPY --from=chainmaker /chainmaker-go/config/wx-org1/chainmaker.yml /opt/templates/chainmaker.yml
COPY --from=chainmaker /chainmaker-go/config/wx-org1/chainconfig/bc1.yml /opt/templates/genesis.yml
COPY --from=chainmaker /chainmaker-go/config/wx-org1/log.yml /opt/templates/log.yml
COPY --from=contract /out/trace_anchor.7z /opt/contracts/trace_anchor.7z
COPY deploy/heterogeneous/assets/parts-trace-backend.jar /opt/app/parts-trace-backend.jar
COPY deploy/heterogeneous/vendor/fisco-build-chain.sh /opt/tools/fisco-build-chain.sh
COPY deploy/heterogeneous/scripts/*.py /opt/tools/
COPY deploy/heterogeneous/scripts/bootstrap-logback.xml /opt/tools/bootstrap-logback.xml
WORKDIR /opt/tools
ENTRYPOINT ["python3", "/opt/tools/bootstrap.py"]
