#!/usr/bin/env bash
# Sourced by the Docker harness scripts. Adapts `docker run` to the Claude Code
# cloud sandbox, whose egress goes through an agent proxy at 127.0.0.1:<port>
# that MITM's TLS with a CA the container doesn't trust.
#
# A container on the default bridge can't reach that loopback proxy, and its
# transparently-intercepted traffic gets 403s (e.g. papermc's download API) and
# PKIX errors. The fix (per the agent proxy docs) is to run the container on the
# host network and hand it the proxy + CA the same way the host has them:
#   --network host                     reach 127.0.0.1:<proxy port>
#   HTTP(S)_PROXY env                  curl / env-aware tools
#   JAVA_TOOL_OPTIONS proxy props+CA   the itzg image's Java downloader
#                                      (Paper, Mojang libraries, Via* on Modrinth)
#   SSL_CERT_FILE / NODE_EXTRA_CA_CERTS other tooling
#
# No-op everywhere else (e.g. GitHub CI), where the agent CA file is absent — so
# committing this is safe.
SANDBOX_DOCKER_ARGS=()
_ccr_ca="/root/.ccr/ca-bundle.crt"
_ccr_ts="/root/.ccr/java-truststore.p12"
if [ -f "$_ccr_ca" ] && [ -n "${HTTPS_PROXY:-}" ]; then
  _hp="${HTTPS_PROXY#*://}"; _phost="${_hp%%:*}"; _pport="${_hp##*:}"
  _jopts="-Dhttps.proxyHost=$_phost -Dhttps.proxyPort=$_pport"
  _jopts="$_jopts -Dhttp.proxyHost=$_phost -Dhttp.proxyPort=$_pport -Dhttp.nonProxyHosts=localhost|127.0.0.1"
  SANDBOX_DOCKER_ARGS+=(
    --network host
    -e "HTTPS_PROXY=$HTTPS_PROXY" -e "HTTP_PROXY=$HTTPS_PROXY"
    -e "https_proxy=$HTTPS_PROXY" -e "http_proxy=$HTTPS_PROXY"
    -e "NO_PROXY=localhost,127.0.0.1" -e "no_proxy=localhost,127.0.0.1"
    -v "$_ccr_ca":/ccr/ca-bundle.crt:ro
    -e SSL_CERT_FILE=/ccr/ca-bundle.crt
    -e CURL_CA_BUNDLE=/ccr/ca-bundle.crt
    -e NODE_EXTRA_CA_CERTS=/ccr/ca-bundle.crt
  )
  if [ -f "$_ccr_ts" ]; then
    _jopts="-Djavax.net.ssl.trustStore=/ccr/java-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit $_jopts"
    SANDBOX_DOCKER_ARGS+=( -v "$_ccr_ts":/ccr/java-truststore.p12:ro )
  fi
  SANDBOX_DOCKER_ARGS+=( -e "JAVA_TOOL_OPTIONS=$_jopts" )
  echo "==> Claude sandbox detected: host network + agent proxy/CA into the container"
fi
