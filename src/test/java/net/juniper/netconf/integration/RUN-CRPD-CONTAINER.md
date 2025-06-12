# Running Juniper cRPD for integration tests

This project’s integration tests need a live NETCONF endpoint.  
You can spin up Juniper’s containerised Routing Protocol Daemon (**cRPD**) locally in < 1 minute.

---

## 1 . Prerequisites

* **Docker + Colima** (or Docker Desktop) on macOS  
  ```bash
  brew install colima docker docker-compose
  colima start                      # arm64 by default; add --arch x86_64 if you need an amd64 VM
  ```

* **cRPD image** (free evaluation tarball from Juniper)  
  Place it under `src/test/resources/` as shown below.

---

## 2 . Load the image into Docker

```bash
docker load < src/test/resources/junos-routing-crpd-docker-23.2R1.13-arm64.tgz
#            or …-amd64… if you’re running a colima --arch x86_64 VM
```

Verify:

```bash
docker images | grep crpd
# crpd  23.2R1.13  0cf5ad…   498MB
```

---

## 3 . Start cRPD with NETCONF and SSH exposed

```bash
docker run -d --name crpd1 --privileged \
           -p 2222:22 \        # SSH
           -p 1830:830 \       # NETCONF
           crpd:23.2R1.13
```

Wait ~40 s, then configure a test user and enable NETCONF:

```bash
docker exec -ti crpd1 cli

# inside Junos CLI
configure
set system root-authentication plain-text-password
#  (enter a password, e.g. Junos123)
set system login user test uid 2000 class super-user
set system login user test authentication plain-text-password
#  (password: test1234)

set system services ssh
set system services netconf ssh
commit and-quit
```

---

## 4 . Run the integration test wrapper

```bash
./src/test/java/net/juniper/netconf/integration/run-integration-tests.sh \
    --username test \
    --password test1234 \
    --host     localhost \
    --port     1830
```

The script builds the library, spins up JUnit tests, and targets the NETCONF service you just started.

---

### Cleaning up

```bash
docker rm -f crpd1      # stop & remove the container
```

That’s it! You now have a repeatable way to launch a Junos device for automated NETCONF testing.