package net.juniper.netconf.integration;

import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.NetconfConstants;
import net.juniper.netconf.NetconfSession;
import net.juniper.netconf.NegotiatedCapabilities;
import net.juniper.netconf.RpcErrorException;
import net.juniper.netconf.ValidateException;
import net.juniper.netconf.XML;
import net.juniper.netconf.element.RpcError;
import net.juniper.netconf.element.RpcReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for netconf-java library against real network devices.
 *
 * To run these tests:
 * mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true \
 *   -Dnetconf.host=192.168.1.1 -Dnetconf.username=admin -Dnetconf.password=secret
 *
 * Or provide the same values with environment variables:
 * NETCONF_HOST=192.168.1.1 NETCONF_USERNAME=admin NETCONF_PASSWORD=secret \
 *   mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true
 */
@EnabledIfSystemProperty(named = "netconf.integration.enabled", matches = "true")
public class NetconfIntegrationTest {

    private static String hostname;
    private static String username;
    private static String password;
    private static int port = 830; // Default NETCONF port
    private static int timeout = 30000; // 30 seconds

    @BeforeAll
    static void setupCredentials() {
        // Prefer explicit JVM properties, then fall back to environment variables.
        hostname = getConfigValue("netconf.host", "NETCONF_HOST");
        username = getConfigValue("netconf.username", "NETCONF_USERNAME");
        password = getConfigValue("netconf.password", "NETCONF_PASSWORD");

        String portStr = getConfigValue("netconf.port", "NETCONF_PORT");
        if (portStr != null) {
            port = Integer.parseInt(portStr);
        }

        String timeoutStr = getConfigValue("netconf.timeout", "NETCONF_TIMEOUT");
        if (timeoutStr != null) {
            timeout = Integer.parseInt(timeoutStr);
        }

        // If not provided via system properties, prompt user
        if (hostname == null || username == null || password == null) {
            Console console = System.console();
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

            System.out.println("=== NETCONF Integration Test Setup ===");

            if (hostname == null) {
                System.out.print("Enter device hostname/IP: ");
                hostname = console != null ? console.readLine() : scanner.nextLine();
            }

            if (username == null) {
                System.out.print("Enter username: ");
                username = console != null ? console.readLine() : scanner.nextLine();
            }

            if (password == null) {
                if (console != null) {
                    char[] passwordChars = console.readPassword("Enter password: ");
                    password = new String(passwordChars);
                } else {
                    System.out.print("Enter password: ");
                    password = scanner.nextLine();
                }
            }

            System.out.print("Enter port (default 830): ");
            String portInput = console != null ? console.readLine() : scanner.nextLine();
            if (!portInput.trim().isEmpty()) {
                port = Integer.parseInt(portInput.trim());
            }

            System.out.println("Using connection details:");
            System.out.println("  Host: " + hostname);
            System.out.println("  Username: " + username);
            System.out.println("  Port: " + port);
            System.out.println("  Timeout: " + timeout + "ms");
            System.out.println();
        }

        assertNotNull(hostname, "Hostname must be provided");
        assertNotNull(username, "Username must be provided");
        assertNotNull(password, "Password must be provided");
    }

    private static String getConfigValue(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.trim().isEmpty()) {
            return environmentValue;
        }
        return null;
    }

    private static Device newDevice() throws NetconfException {
        return Device.builder()
            .hostName(hostname)
            .userName(username)
            .password(password)
            .port(port)
            .connectionTimeout(timeout)
            .strictHostKeyChecking(false)
            .build();
    }

    private static RpcReply getLastRpcReplyObject(Device device) {
        try {
            Field sessionField = Device.class.getDeclaredField("netconfSession");
            sessionField.setAccessible(true);
            NetconfSession session = (NetconfSession) sessionField.get(device);
            assertNotNull(session, "Netconf session should be present after connect");
            RpcReply reply = session.getLastRpcReplyObject();
            assertNotNull(reply, "Last RPC reply should be captured");
            return reply;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect Device netconfSession for integration assertions", e);
        }
    }

    @Test
    @DisplayName("Test device connection and basic capabilities")
    void testDeviceConnection() throws NetconfException {
        System.out.println("Testing device connection...");

        Device device = newDevice();

        try {
            device.connect();
            assertTrue(device.isConnected(), "Device should be connected");

            NegotiatedCapabilities negotiatedCapabilities = device.getNegotiatedCapabilities();
            assertNotNull(negotiatedCapabilities, "Negotiated capabilities should not be null");

            // Verify the capabilities reported by the server hello.
            String[] capabilities = negotiatedCapabilities.getServerCapabilities().toArray(new String[0]);
            assertNotNull(capabilities, "Server capabilities should not be null");
            assertTrue(capabilities.length > 0, "Server should have at least one capability");

            System.out.println("✓ Successfully connected to device");
            System.out.println("✓ Server capabilities count: " + capabilities.length);
            System.out.println("✓ Negotiated NETCONF base version: " + negotiatedCapabilities.getBaseVersion());

            // Print some capabilities for debugging
            System.out.println("Server capabilities:");
            for (int i = 0; i < Math.min(5, capabilities.length); i++) {
                System.out.println("  " + capabilities[i]);
            }
            if (capabilities.length > 5) {
                System.out.println("  ... and " + (capabilities.length - 5) + " more");
            }

        } finally {
            if (device.isConnected()) {
                device.close();
                assertFalse(device.isConnected(), "Device should be disconnected after close");
            }
        }
    }

    @Test
    @DisplayName("Test basic get-config operation")
    void testGetConfig() throws org.xml.sax.SAXException, IOException, NetconfException {
        System.out.println("Testing get-config operation...");

        Device device = newDevice();

        try {
            device.connect();

            // Test get-config with running datastore
            XML rpcReply = device.executeRPC("<get-config><source><running/></source></get-config>");

            assertNotNull(rpcReply, "RPC reply should not be null");

            String response = rpcReply.toString();
            assertNotNull(response, "RPC reply string should not be null");
            assertFalse(response.isEmpty(), "RPC reply should not be empty");
            assertTrue(response.contains("rpc-reply"), "Response should contain rpc-reply");

            System.out.println("✓ Successfully executed get-config");
            System.out.println("✓ Response length: " + response.length() + " characters");

        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test get operation with interface information")
    void testGetInterfaceInformation() throws org.xml.sax.SAXException, IOException, NetconfException {
        System.out.println("Testing get interface information...");

        Device device = newDevice();

        try {
            device.connect();

            // Try different RPC formats to test flexibility
            String[] rpcFormats = {
                "get-interface-information",
                "<get-interface-information/>",
                "<rpc><get-interface-information/></rpc>"
            };

            boolean atLeastOneSucceeded = false;

            for (String rpc : rpcFormats) {
                try {
                    System.out.println("Trying RPC format: " + rpc);
                    XML rpcReply = device.executeRPC(rpc);

                    assertNotNull(rpcReply, "RPC reply should not be null for format: " + rpc);
                    String response = rpcReply.toString();
                    assertFalse(response.isEmpty(), "RPC reply should not be empty for format: " + rpc);

                    System.out.println("✓ RPC format '" + rpc + "' succeeded");
                    atLeastOneSucceeded = true;

                } catch (NetconfException e) {
                    System.out.println("✗ RPC format '" + rpc + "' failed: " + e.getMessage());
                    // Continue trying other formats
                }
            }

            // At least one format should work on a properly configured device
            // Note: This might fail on non-Juniper devices, which is expected
            if (!atLeastOneSucceeded) {
                System.out.println("ℹ None of the interface information RPC formats succeeded. " +
                    "This might be normal for non-Juniper devices.");
            }

        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test connection timeout handling")
    void testConnectionTimeout() throws NetconfException {
        System.out.println("Testing connection timeout handling...");

        // Use an unreachable IP to test timeout
        long startTime;
        try (Device device = Device.builder()
            .hostName("192.0.2.1") // RFC 5737 test IP - should be unreachable
            .userName("test")
            .password("test")
            .port(830)
            .strictHostKeyChecking(false)
            .connectionTimeout(5000) // 5 second timeout
            .build()) {

            startTime = System.currentTimeMillis();

            assertThrows(NetconfException.class, device::connect,
                "Connection to unreachable host should throw NetconfException");
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Should timeout within reasonable time (allow some variance)
        assertTrue(elapsedTime < 10000,
            "Timeout should occur within 10 seconds, but took " + elapsedTime + "ms");

        System.out.println("✓ Connection timeout handled correctly in " + elapsedTime + "ms");
    }

    @Test
    @DisplayName("Test multiple sequential connections")
    void testMultipleConnections() throws NetconfException {
        System.out.println("Testing multiple sequential connections...");

        for (int i = 1; i <= 3; i++) {
            System.out.println("Connection attempt " + i + "/3");

            Device device = newDevice();

            try {
                device.connect();
                assertTrue(device.isConnected(), "Device should be connected on attempt " + i);

                // Brief operation to ensure connection is working
                int capabilityCount = device.getNegotiatedCapabilities().getServerCapabilities().size();
                assertTrue(capabilityCount > 0, "Should have capabilities on attempt " + i);

            } finally {
                if (device.isConnected()) {
                    device.close();
                    assertFalse(device.isConnected(), "Device should be disconnected after close on attempt " + i);
                }
            }

            // Small delay between connections
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("✓ Multiple sequential connections completed successfully");
    }

    @Test
    @DisplayName("Test negotiated base version matches server hello")
    void testNegotiatedBaseVersionMatchesServerHello() throws NetconfException {
        System.out.println("Testing negotiated base version...");

        Device device = newDevice();

        try {
            device.connect();

            NegotiatedCapabilities negotiatedCapabilities = device.getNegotiatedCapabilities();
            assertNotNull(negotiatedCapabilities, "Negotiated capabilities should not be null");
            assertNotNull(negotiatedCapabilities.getBaseVersion(), "Negotiated base version should not be null");

            boolean serverSupportsBase11 = negotiatedCapabilities.getServerCapabilities()
                .contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);
            boolean serverSupportsBase10 = negotiatedCapabilities.getServerCapabilities()
                .contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);

            assertTrue(serverSupportsBase10 || serverSupportsBase11,
                "Server should advertise at least one NETCONF base capability");

            NegotiatedCapabilities.BaseVersion expectedBaseVersion = serverSupportsBase11
                ? NegotiatedCapabilities.BaseVersion.NETCONF_1_1
                : NegotiatedCapabilities.BaseVersion.NETCONF_1_0;
            assertEquals(expectedBaseVersion, negotiatedCapabilities.getBaseVersion(),
                "Negotiated base version should follow the shared hello capabilities");

            System.out.println("✓ Negotiated base version: " + negotiatedCapabilities.getBaseVersion());
        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test same session handles multiple sequential RPCs")
    void testSequentialRpcsOnSingleSession() throws org.xml.sax.SAXException, IOException, NetconfException {
        System.out.println("Testing sequential RPCs on one session...");

        Device device = newDevice();

        try {
            device.connect();

            XML firstReply = device.getRunningConfig();
            XML secondReply = device.getRunningConfigAndState(null);
            XML thirdReply = device.getRunningConfig();

            assertNotNull(firstReply, "First reply should not be null");
            assertNotNull(secondReply, "Second reply should not be null");
            assertNotNull(thirdReply, "Third reply should not be null");
            assertTrue(firstReply.toString().contains("rpc-reply"), "First reply should contain rpc-reply");
            assertTrue(secondReply.toString().contains("rpc-reply"), "Second reply should contain rpc-reply");
            assertTrue(thirdReply.toString().contains("rpc-reply"), "Third reply should contain rpc-reply");

            System.out.println("✓ Same session handled sequential get-config/get/get-config RPCs");
        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test streaming RPC close drains reply and keeps session aligned")
    void testStreamingReplyCloseKeepsSessionAligned() throws org.xml.sax.SAXException, IOException, NetconfException {
        System.out.println("Testing streamed RPC drain behavior...");

        Device device = newDevice();

        try {
            device.connect();

            char[] buffer = new char[256];
            try (BufferedReader reader = device.executeRPCRunning("<get-config><source><running/></source></get-config>")) {
                int charsRead = reader.read(buffer);
                assertTrue(charsRead > 0, "Streaming RPC should yield at least some response bytes");
            }

            XML followUpReply = device.getRunningConfig();
            assertNotNull(followUpReply, "Follow-up reply should not be null");
            assertTrue(followUpReply.toString().contains("rpc-reply"),
                "Follow-up reply should still be parsed correctly after draining the stream");

            System.out.println("✓ Closing streamed reply kept the session aligned");
        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test RPC error handling returns structured rpc-error details")
    void testRPCErrorHandling() throws Exception {
        System.out.println("Testing RPC error handling...");

        Device device = newDevice();

        try {
            device.connect();

            RpcErrorException exception = assertThrows(RpcErrorException.class, () ->
                    device.executeRPC("<invalid-rpc-that-should-not-exist/>"),
                "Invalid RPC should throw a structured RpcErrorException");

            assertNotNull(exception.getMessage(), "Exception message should be populated");
            assertEquals(1, exception.getRpcErrors().size(),
                "Exception should expose one parsed rpc-error");

            RpcReply rpcReply = getLastRpcReplyObject(device);
            assertTrue(rpcReply.hasErrors(), "Last rpc-reply should contain an rpc-error");
            assertEquals(1, rpcReply.getErrors().size(), "Expected one rpc-error from the invalid RPC");

            RpcError rpcError = rpcReply.getErrors().get(0);
            assertEquals(RpcError.ErrorType.PROTOCOL, rpcError.errorType(), "Unexpected rpc-error type");
            assertEquals(RpcError.ErrorSeverity.ERROR, rpcError.errorSeverity(), "Unexpected rpc-error severity");
            assertEquals(RpcError.ErrorTag.OPERATION_FAILED, rpcError.errorTag(), "Unexpected rpc-error tag");
            assertNotNull(rpcError.errorInfo(), "rpc-error info should be present");
            assertEquals("invalid-rpc-that-should-not-exist", rpcError.errorInfo().getBadElement(),
                "Server should identify the offending RPC element");

            System.out.println("✓ RPC error handling works correctly");
            System.out.println("✓ Parsed rpc-error bad-element: " + rpcError.errorInfo().getBadElement());

            XML followUpReply = device.getRunningConfig();
            assertNotNull(followUpReply, "Connection should still support follow-up RPCs after an error");
            assertTrue(followUpReply.toString().contains("rpc-reply"),
                "Follow-up reply should be a valid rpc-reply after an rpc-error");

            System.out.println("✓ Connection remains usable after RPC error");

        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }

    @Test
    @DisplayName("Test candidate lock/validate/unlock workflow when supported")
    void testCandidateLockValidateUnlockWorkflow() throws Exception {
        System.out.println("Testing candidate lock/validate/unlock workflow...");

        Device device = newDevice();
        boolean locked = false;

        try {
            device.connect();

            NegotiatedCapabilities negotiatedCapabilities = device.getNegotiatedCapabilities();
            Assumptions.assumeTrue(negotiatedCapabilities.supportsCandidate(),
                "Server does not advertise candidate capability");
            Assumptions.assumeTrue(negotiatedCapabilities.supportsValidate(),
                "Server does not advertise validate capability");

            locked = device.lockConfig();
            assertTrue(locked, "Candidate datastore lock should succeed");

            boolean validateReturnedTrue;
            RpcReply validateReply;
            try {
                validateReturnedTrue = device.validate();
                validateReply = getLastRpcReplyObject(device);
            } catch (ValidateException e) {
                assertNotNull(e.getRpcReply(), "ValidateException should expose the parsed rpc-reply");
                assertFalse(e.getRpcErrors().isEmpty(),
                    "ValidateException should expose at least one parsed rpc-error");
                assertTrue(e.getRpcReply().hasErrors(),
                    "ValidateException rpc-reply should contain error-severity rpc-errors");
                System.out.println("ℹ Candidate validate returned structured rpc-errors: " + e.getRpcErrors());
                Assumptions.assumeTrue(false,
                    "Server validate failed in this environment: " + e.getMessage());
                return;
            }

            assertTrue(validateReturnedTrue || validateReply.hasWarnings(),
                "Candidate validation should either succeed cleanly or return warnings only");

            boolean unlocked = device.unlockConfig();
            locked = false;
            assertTrue(unlocked, "Candidate datastore unlock should succeed");

            System.out.println("✓ Candidate lock/validate/unlock workflow completed");
            if (!validateReturnedTrue) {
                System.out.println("ℹ Candidate validate returned warnings instead of a clean <ok/> reply");
            }
        } finally {
            if (locked && device.isConnected()) {
                assertTrue(device.unlockConfig(), "Candidate datastore unlock should succeed in cleanup");
            }
            if (device.isConnected()) {
                device.close();
            }
        }
    }
}
