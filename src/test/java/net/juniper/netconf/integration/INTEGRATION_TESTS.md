# Netconf Java Integration Tests

This directory contains optional integration tests that exercise the
library against a live NETCONF-over-SSH endpoint. The suite is
disabled by default and only runs when
`netconf.integration.enabled=true` is set.

## What the suite covers

The current integration suite verifies:

- Basic SSH and NETCONF session establishment
- Server capability retrieval after `<hello>`
- Negotiated NETCONF base-version selection from client/server
  capability overlap
- Basic `<get-config>` behavior against the running datastore
- Same-session sequential RPC reuse across multiple operations
- Streamed reply draining when callers close a `BufferedReader` early
- Multiple sequential connect/close cycles
- Structured `rpc-error` parsing for malformed RPCs, plus
  post-error session recovery
- Capability-guarded candidate lock/validate/unlock workflow
- Connection timeout behavior against an unreachable address
- A Junos-specific `get-interface-information` smoke test

Notes:

- The suite does not perform configuration changes.
- The `get-interface-information` test is best-effort. It is useful
  for Junos targets, but it may legitimately be unsupported on
  non-Juniper servers.
- The suite inspects negotiated server capabilities via
  `getNegotiatedCapabilities()` rather than the client's configured
  hello list.

## Prerequisites

- Java 17
- Maven available on `PATH` if you want to use the wrapper script
- Gradle 8+ or the checked-in `./gradlew` wrapper if you want to use
  the Gradle task directly
- A reachable NETCONF server and credentials

For a local Junos-based test target, see
[`RUN-CRPD-CONTAINER.md`](./RUN-CRPD-CONTAINER.md). That runbook
covers Docker and Podman on macOS, the minimum cRPD NETCONF config
including `rfc-compliant`, and a quick readiness check before you run
the suite.

## Recommended: wrapper script

The wrapper script is the easiest way to run the tests interactively.
It resolves the repository root internally, so you can invoke it from
the repo root or by absolute path from another working directory. The
wrapper currently uses Maven under the hood.

From the repository root:

```bash
chmod +x ./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh
./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh
```

With explicit connection details:

```bash
./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh \
  --host 192.168.1.1 \
  --username admin \
  --password secret \
  --port 830 \
  --timeout 30000
```

The same values can also be provided with environment variables:

```bash
NETCONF_HOST=192.168.1.1 \
NETCONF_USERNAME=admin \
NETCONF_PASSWORD=secret \
NETCONF_PORT=830 \
NETCONF_TIMEOUT=30000 \
./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh
```

The wrapper exports those variables to Maven instead of echoing
secrets on the command line.

## Direct Maven invocation

For CI or other non-interactive environments, prefer passing all
connection details explicitly:

```bash
mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true \
  -Dnetconf.host=192.168.1.1 \
  -Dnetconf.username=admin \
  -Dnetconf.password=secret \
  -Dnetconf.port=830 \
  -Dnetconf.timeout=30000
```

If you omit required properties, the test class may prompt on stdin.
That works best from a normal terminal session; the wrapper script is
the more reliable interactive path.

The test class also reads `NETCONF_HOST`, `NETCONF_USERNAME`,
`NETCONF_PASSWORD`, `NETCONF_PORT`, and `NETCONF_TIMEOUT` directly
from the environment. That is the better option when you do not want
credentials to appear in the shell history or Maven system-property
reports.

```bash
NETCONF_HOST=192.168.1.1 \
NETCONF_USERNAME=admin \
NETCONF_PASSWORD=secret \
NETCONF_PORT=830 \
NETCONF_TIMEOUT=30000 \
mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true
```

## Direct Gradle invocation

For Gradle users, use the dedicated `integrationTest` task:

```bash
./gradlew integrationTest \
  -Dnetconf.host=192.168.1.1 \
  -Dnetconf.username=admin \
  -Dnetconf.password=secret \
  -Dnetconf.port=830 \
  -Dnetconf.timeout=30000
```

The same task also accepts `NETCONF_HOST`, `NETCONF_USERNAME`,
`NETCONF_PASSWORD`, `NETCONF_PORT`, and `NETCONF_TIMEOUT` from the
environment:

```bash
NETCONF_HOST=192.168.1.1 \
NETCONF_USERNAME=admin \
NETCONF_PASSWORD=secret \
NETCONF_PORT=830 \
NETCONF_TIMEOUT=30000 \
./gradlew integrationTest
```

Unlike the Maven wrapper flow, the Gradle task is intentionally
non-interactive. If required connection details are missing, it fails
fast with a clear error instead of trying to prompt inside the Gradle
test worker.

## Connection properties

The integration test class reads the following JVM system properties:

- `netconf.integration.enabled=true`
- `netconf.host`
- `netconf.username`
- `netconf.password`
- `netconf.port` (default `830`)
- `netconf.timeout` (default `30000`)

## Operational notes

- The tests build `Device` instances with
  `strictHostKeyChecking(false)` for convenience. That is acceptable
  for disposable local test targets, but it should not be copied into
  production usage.
- The suite assumes the target speaks NETCONF over SSH and that the
  supplied account has enough privilege for basic `<get-config>` and
  vendor RPCs.
- For local containerized targets such as cRPD, pass the published
  host-side NETCONF port, for example `8830`, rather than the
  container's internal port `830`.
- If you use the cRPD container workflow, downloaded image archives
  and extracted Docker image artifacts under `src/test/resources/` are
  treated as local test assets and are ignored by git.
