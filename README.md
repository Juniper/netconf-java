netconf-java
============

Java library for NETCONF

SUPPORT
=======

This software is not officially supported by Juniper Networks, but by a team dedicated to helping customers,
partners, and the development community.  To report bug-fixes, issues, suggestions, please contact netconf-automation-hackers@juniper.net

REQUIREMENTS
============

[OpenJDK 8](http://openjdk.java.net/projects/jdk8/) or Java 8

[Maven](https://maven.apache.org/download.cgi) if you want to build using `mvn` [Supported from v2.1.1].

[lombok](https://mvnrepository.com/artifact/org.projectlombok/lombok) needs to be provided by the runtime (`mvn dependency scope` is set as `provided`)

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
  * Use the jar file from (source to netconf-java)/netconf-java/target
  * Use `mvn versions:display-dependency-updates` to identify possible target versions for dependencies
  
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
    .hostKeysFileName("hostKeysFileName")
    .build();
```

SYNOPSIS
========

```Java
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import net.juniper.netconf.NetconfException;
import org.xml.sax.SAXException;

import net.juniper.netconf.XML;
import net.juniper.netconf.Device;

public class ShowInterfaces {
    public static void main(String args[]) throws NetconfException,
                ParserConfigurationException, SAXException, IOException {

        //Create device
        Device device = Device.builder()
                            .hostName("hostname")
                            .userName("username")
                            .password("password")
                            .hostKeysFileName("hostKeysFileName")
                            .build(); 
        device.connect();

        //Send RPC and receive RPC Reply as XML
        XML rpc_reply = device.executeRPC("get-interface-information");
        /* OR
            * device.executeRPC("<get-interface-information/>");
            * OR
            * device.executeRPC("<rpc><get-interface-information/></rpc>");
            */

        //Print the RPC-Reply and close the device.
        System.out.println(rpc_reply);
        device.close();
    }
}
```

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
