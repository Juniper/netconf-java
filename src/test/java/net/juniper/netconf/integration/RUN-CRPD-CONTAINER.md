# Running Juniper cRPD for Integration Tests

This project's integration tests need a live NETCONF endpoint. One
easy local option is Juniper cRPD running in a container.

The examples below use:

- `localhost:2222` for normal SSH and Junos CLI access
- `localhost:8830` for NETCONF-over-SSH

Using host port `8830` instead of `830` avoids binding a privileged
port on macOS/Linux and keeps the workflow rootless.

## 1. Prerequisites

- A cRPD image tarball from Juniper
- One container runtime:
  - Docker Desktop or Docker + Colima
  - Podman on macOS/Linux
- Java 17+ if you plan to run this repository's integration tests

On macOS with Docker + Colima:

```bash
brew install colima docker docker-compose
colima start
```

On macOS with Podman:

```bash
brew install podman
podman machine init
podman machine start
```

Place the cRPD image archive under `src/test/resources/`. If you
follow this workflow, the archive and extracted image artifacts are
treated as local test assets and are ignored by git.

## 2. Load the image into your runtime

Docker:

```bash
docker load < src/test/resources/junos-routing-crpd-docker-amd64-23.2R1.13.tgz
docker images | grep -i crpd
```

Podman:

```bash
podman load -i src/test/resources/junos-routing-crpd-docker-amd64-23.2R1.13.tgz
podman images | grep -i crpd
```

If you are on Apple Silicon and your cRPD image is the `amd64` build,
keep using that filename and add `--platform linux/amd64` when you
start the container.

## 3. Start cRPD with SSH and NETCONF exposed

Docker:

```bash
docker rm -f crpd1
docker run -d --name crpd1 --privileged \
  --hostname crpd1 \
  -p 2222:22 \
  -p 8830:830 \
  crpd:23.2R1.13
```

Podman:

```bash
podman rm -f crpd1
podman run -d --name crpd1 \
  --platform linux/amd64 \
  --privileged \
  --hostname crpd1 \
  -p 2222:22 \
  -p 8830:830 \
  localhost/crpd:23.2R1.13
```

If you are not running an `amd64` image, remove `--platform
linux/amd64` from the Podman command.

## 4. Configure the minimum Junos settings

Wait until `docker exec -it crpd1 cli` or `podman exec -it crpd1 cli`
works, then configure a test user and enable NETCONF:

```bash
podman exec -it crpd1 cli
# or: docker exec -it crpd1 cli

# inside the Junos CLI
configure
set system login user test uid 2000 class super-user
set system login user test authentication plain-text-password
# password: test1234

set system services ssh
set system services netconf ssh
set system services netconf rfc-compliant
commit and-quit
```

`set system root-authentication plain-text-password` is optional. It
is only needed if you want to log in as `root` over SSH. It is not
required for this repository's NETCONF integration tests.

The relevant committed config should look roughly like this:

```text
system {
    login {
        user test {
            uid 2000;
            class super-user;
            authentication {
                encrypted-password "$<redacted>";
            }
        }
    }
    services {
        ssh;
        netconf {
            ssh {
                port 830;
            }
            rfc-compliant;
        }
    }
}
```

What `rfc-compliant` changes in practice on cRPD 23.2R1.13:

- Server replies switch to RFC-shaped NETCONF XML such as
  `nc:hello`, `nc:rpc-reply`, `nc:ok`, and `nc:rpc-error`
- `<get-config>` replies use the newer Junos config namespace
  `http://yang.juniper.net/junos/conf/root`
- The server may still advertise only NETCONF `base:1.0` in `<hello>`, even with
  `rfc-compliant` enabled

That last point matters: in this environment, `rfc-compliant`
improves XML conformance, but it does not by itself prove NETCONF 1.1
chunked framing support. If the server does not advertise
`urn:ietf:params:netconf:base:1.1`, delimiter framing with `]]>]]>` is
still the expected behavior.

## 5. Verify cRPD is actually ready

Container is running:

```bash
podman ps --filter name=crpd1
# or: docker ps --filter name=crpd1
```

Port mapping looks right:

```bash
podman port crpd1
# or: docker port crpd1
# expect 22/tcp -> 2222 and 830/tcp -> 8830
```

Junos CLI responds:

```bash
podman exec crpd1 cli -c "show version"
podman exec crpd1 cli -c "show configuration system services | display set"
podman exec crpd1 cli -c \
  "show configuration system login user test | display set"
# or use the same commands with `docker exec`
```

End-to-end NETCONF handshake works from the host:

```bash
ssh -s -p 8830 test@localhost netconf
```

After you authenticate, you should see a NETCONF `<hello>` from the
server. That is a much better readiness check than only confirming
that the container exists.

## 6. Run the integration tests

From the repository root:

```bash
./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh \
  --username test \
  --password test1234 \
  --host localhost \
  --port 8830
```

The wrapper builds the library and runs the JUnit integration suite
against the NETCONF endpoint you just started.

If you prefer Gradle:

```bash
NETCONF_HOST=localhost \
NETCONF_USERNAME=test \
NETCONF_PASSWORD=test1234 \
NETCONF_PORT=8830 \
./gradlew integrationTest
```

## 7. Cleanup

```bash
docker rm -f crpd1
# or: podman rm -f crpd1
```

That gives you a repeatable local Junos target for NETCONF integration testing.
