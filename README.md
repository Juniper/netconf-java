netconf-java
============

**A modernized Java library for NETCONF (now Java 17‑compatible)**

Java library for NETCONF

SUPPORT
=======

This software is not officially supported by Juniper Networks, but by a team dedicated to helping customers,
partners, and the development community.  To report bug-fixes, issues, suggestions, please raise issues
or even better submit pull requests on GitHub.

REQUIREMENTS
============

* [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or later
* [Maven](https://maven.apache.org/download.cgi) if you want to build using `mvn` [Supported from v2.1.1].
* [Gradle 8+](https://gradle.org/releases/) if you prefer a Gradle build (`./gradlew build`)

Building
========
You can build the project using **Maven** or **Gradle**.

### Maven
```bash
mvn clean package
```

### Gradle
```bash
./gradlew clean build
```
(The wrapper script downloads the correct Gradle version automatically.)

To run the live NETCONF integration suite with Gradle:

```bash
NETCONF_HOST=192.168.1.1 \
NETCONF_USERNAME=admin \
NETCONF_PASSWORD=secret \
NETCONF_PORT=830 \
./gradlew integrationTest
```

Releases
========
Releases contain source code only. Due to changing JDK licensing, jar files are not released.
User may download the source code and compile it with desired JDK version.

* Instructions to build
  * Download Source Code for the required release
  * Compile the code and build the jar using your chosen JDK version
  * Use the jar file

* Instructions to build using `mvn`
  * Download Source Code for the required release
  * Compile the code and build the jar using `mvn package`
  * Use the jar file from `./target/`
  * Use `mvn versions:display-dependency-updates` to identify possible target versions for dependencies
  
=======

v2.2.1
------
* Hardened NETCONF XML parsing against XXE and DTD-based attacks
* Fixed NETCONF RPC framing and `message-id` reply correlation for sequential session reuse
* Enforced shared NETCONF base capability negotiation and derive session framing from the negotiated base version
* Capability-gated candidate, validate, and confirmed-commit operations before sending RPCs
* Added negotiated capability inspection via `Device.getNegotiatedCapabilities()` and `NetconfSession.getNegotiatedCapabilities()`
* Typed NETCONF `<rpc-error>` replies as structured exceptions so callers can inspect server-reported error details
* Added `ValidateException` and clarified `validate()` semantics: server `rpc-error` replies throw, while warning-only or other non-`<ok/>` non-error replies still return `false`
* Improved SSH/NETCONF session cleanup on failed connection or session initialization
* Fixed shell exec helpers so commands are set, channels are connected, and timeout/cleanup behavior is more predictable
* Fixed nested XML path construction in the XML helper
* Documented `NetconfSession` as a sequential request/response channel rather than a concurrent in-flight RPC transport
* Added [`docs/compatibility.md`](docs/compatibility.md) with current RFC, capability, NMDA, and extension support details
* Added a dedicated Gradle `integrationTest` task that forwards NETCONF connection settings for live-server testing
* Upgraded `assertj-core` to `3.27.7` to address `CVE-2026-24400`

v2.2.0
------
* Java 17 baseline; compiled with `--release 17`
* Gradle build added alongside Maven
* SpotBugs upgraded to 6.x
* Added **:confirmed-commit:1.1** support (`commitConfirm(timeout, persist)` and `cancelCommit(persistId)`)
* Added **killSession(String)** helper for RFC 6241 §7.9
* Auto‑inject base 1.1 capability in &lt;hello&gt; exchange
* Gradle wrapper committed; GitHub Actions now builds Maven *and* Gradle
* Expanded Javadoc and SpotBugs clean‑up

v2.1.1
------

* Fixed `mvn` build issues

v2.0.0
------

* Replaced the ssh library with [JSch](http://www.jcraft.com/jsch/)
  * Adds support for new ssh crypto algorithms
  * More modern ssh implementation
* Added support for importing and building the library with maven
* Added FindBugs code testing to maven build

This is a breaking change to the API. New Device objects are now created using a builder.
Example:

```Java
Device device = Device.builder().hostName("hostname")
    .userName("username")
    .password("password")
    .connectionTimeout(2000)
    .hostKeysFileName("hostKeysFileName")
    .build();
```

SYNOPSIS
========

```Java
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.ValidateException;
import org.xml.sax.SAXException;

import net.juniper.netconf.XML;
import net.juniper.netconf.Device;

public class ShowInterfaces {
    public static void main(String args[]) throws NetconfException,
                ParserConfigurationException, SAXException, IOException {

        try (Device device = Device.builder()
                .hostName("hostname")
                .userName("username")
                .password("password")
                .hostKeysFileName("hostKeysFileName")
                .connectionTimeout(2000)
                .commandTimeout(5000)
                .build()) {

            // Establish the SSH transport and the default NETCONF session.
            device.connect();

            // Send RPC and receive RPC reply as XML.
            XML rpcReply = device.executeRPC("get-interface-information");
            /* OR
             * device.executeRPC("<get-interface-information/>");
             * OR
             * device.executeRPC("<rpc><get-interface-information/></rpc>");
             */

            System.out.println(rpcReply);
        }
    }
}
```

Candidate validate example:

```Java
try {
    boolean clean = device.validate();
    if (!clean) {
        // Warning-only or other non-error, non-<ok/> reply.
        System.out.println("Validate completed without rpc-error, but did not return <ok/>");
    }
} catch (ValidateException e) {
    // Server returned one or more <rpc-error> elements.
    System.err.println("Validate failed: " + e.getMessage());
    e.getRpcErrors().forEach(System.err::println);
}
```

Recommended usage:

* Build one `Device` per target connection and use `try-with-resources` so SSH resources are released predictably.
* Call `connect()` before issuing RPCs. If `connect()` throws, no usable NETCONF session was established.
* Inspect `getNegotiatedCapabilities()` after `connect()` if your application needs to branch on server support for candidate, validate, or confirmed-commit behavior.
* Set `connectionTimeout` and `commandTimeout` explicitly for production use rather than relying on defaults.
* Prefer NETCONF RPC helpers (`executeRPC`, `getConfig`, `loadXMLConfiguration`, `commit`, and friends) for device operations; use shell helpers only for device-specific workflows that are not available over NETCONF.
* Treat `ValidateException` as the server-side `rpc-error` path for `validate()`. A `false` return now means the reply was non-error but not a clean `<ok/>`, typically warnings.
* Shell helper reads are bounded by `commandTimeout`. If you use `runShellCommandRunning(...)`, always close the returned reader so the underlying exec channel is released.

LICENSE
=======

(BSD 2)

Copyright © 2013, Juniper Networks

All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

(1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

(2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those of the authors and should not be interpreted as representing official policies, either expressed or implied, of Juniper Networks.

AUTHOR
======

[Ankit Jain](http://www.linkedin.com/in/ankitj093), Juniper Networks
[Peter J Hill](https://github.com/peterjhill), Oracle
[Community Contributors](https://github.com/Juniper/netconf-java/graphs/contributors)
