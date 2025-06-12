# NetConf Java Integration Tests

This directory contains integration tests for the netconf-java library that test against real network devices.

## Overview

The integration tests verify:
- Basic device connection and authentication
- Server capabilities retrieval
- Configuration retrieval via get-config
- Multiple sequential connections
- Error handling and timeouts
- Device-specific RPC operations

## Running the Tests

### Method 1: Using JUnit (Recommended)

```bash
# Run with interactive prompts
mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true

# Run with predefined credentials
mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true \
  -Dnetconf.host=192.168.1.1 \
  -Dnetconf.username=admin \
  -Dnetconf.password=secret \
  -Dnetconf.port=830
```

### Method 2: Using the Shell Script

```bash
# Make the script executable
chmod +x run-integration-tests.sh

# Run with interactive prompts
./run-integration-tests.sh

# Run with command line arguments
./run-integration-tests.sh --host 192.168.1.1 --username admin --password secret
```

### Method 3: Manual Test Runner

For environments where JUnit is not available:

```bash
# Compile the manual runner
javac -cp "target/classes:target/dependency/*" src/test/java/net/juniper/netconf/integration/ManualTestRunner.java

# Run the manual tests
java -cp "target/classes:target/dependency/*:src/test/java" net.juniper.netconf.integration.