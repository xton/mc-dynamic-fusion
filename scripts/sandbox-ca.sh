#!/usr/bin/env bash
# Sourced by the Docker harness scripts. In the Claude Code cloud sandbox all
# container egress is transparently MITM'd by an agent proxy whose CA the
# container's trust store doesn't include — so the itzg image's Java downloads
# (Paper, Mojang libraries, Via* from Modrinth) fail with PKIX "unable to find
# valid certification path" errors.
#
# When that CA is present on the host, populate SANDBOX_CA_ARGS with docker
# flags that mount the agent CA + JVM truststore into the container and point
# its tooling at them. No-op everywhere else (e.g. GitHub CI), where the file is
# absent, so committing this is safe.
SANDBOX_CA_ARGS=()
_ccr_ca="/root/.ccr/ca-bundle.crt"
_ccr_ts="/root/.ccr/java-truststore.p12"
if [ -f "$_ccr_ca" ]; then
  SANDBOX_CA_ARGS+=(
    -v "$_ccr_ca":/ccr/ca-bundle.crt:ro
    -e SSL_CERT_FILE=/ccr/ca-bundle.crt
    -e CURL_CA_BUNDLE=/ccr/ca-bundle.crt
    -e NODE_EXTRA_CA_CERTS=/ccr/ca-bundle.crt
  )
  if [ -f "$_ccr_ts" ]; then
    SANDBOX_CA_ARGS+=(
      -v "$_ccr_ts":/ccr/java-truststore.p12:ro
      -e "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/ccr/java-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
    )
  fi
  echo "==> Claude sandbox detected: mounting the agent CA into the container"
fi
