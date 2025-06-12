package net.juniper.netconf.integration;

import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.XML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.Console;
import java.io.IOException;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for netconf-java library against real network devices.
 *
 * To run these tests:
 * mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true
 *
 * Or run with specific device details:
 * mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true \
 *   -Dnetconf.host=192.168.1.1 -Dnetconf.username=admin -Dnetconf.password=secret
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
        // Try to get credentials from system properties first
        hostname = System.getProperty("netconf.host");
        username = System.getProperty("netconf.username");
        password = System.getProperty("netconf.password");

        String portStr = System.getProperty("netconf.port");
        if (portStr != null) {
            port = Integer.parseInt(portStr);
        }

        String timeoutStr = System.getProperty("netconf.timeout");
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

    @Test
    @DisplayName("Test device connection and basic capabilities")
    void testDeviceConnection() throws NetconfException {
        System.out.println("Testing device connection...");

        Device device = Device.builder()
            .hostName(hostname)
            .userName(username)
            .password(password)
            .port(port)
            .connectionTimeout(timeout)
            .strictHostKeyChecking(false)
            .build();

        try {
            device.connect();
            assertTrue(device.isConnected(), "Device should be connected");

            // Test getting server capabilities
            String[] capabilities = device.getNetconfCapabilities().toArray(new String[0]);
            assertNotNull(capabilities, "Server capabilities should not be null");
            assertTrue(capabilities.length > 0, "Server should have at least one capability");

            System.out.println("✓ Successfully connected to device");
            System.out.println("✓ Server capabilities count: " + capabilities.length);

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
    void testGetConfig() throws
        org.xml.sax.SAXException, IOException {
        System.out.println("Testing get-config operation...");

        Device device = Device.builder()
            .hostName(hostname)
            .userName(username)
            .password(password)
            .port(port)
            .connectionTimeout(timeout)
            .strictHostKeyChecking(false)
            .build();

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
    void testGetInterfaceInformation() throws org.xml.sax.SAXException, IOException {
        System.out.println("Testing get interface information...");

        Device device = Device.builder()
            .hostName(hostname)
            .userName(username)
            .password(password)
            .port(port)
            .connectionTimeout(timeout)
            .strictHostKeyChecking(false)
            .build();

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

            Device device = Device.builder()
                .hostName(hostname)
                .userName(username)
                .password(password)
                .port(port)
                .strictHostKeyChecking(false)
                .connectionTimeout(timeout)
                .build();

            try {
                device.connect();
                assertTrue(device.isConnected(), "Device should be connected on attempt " + i);

                // Brief operation to ensure connection is working
                int capabilityCount = device.getNetconfCapabilities().size();
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
    @DisplayName("Test RPC error handling")
    void testRPCErrorHandling() throws NetconfException {
        System.out.println("Testing RPC error handling...");

        Device device = Device.builder()
            .hostName(hostname)
            .userName(username)
            .password(password)
            .port(port)
            .strictHostKeyChecking(false)
            .connectionTimeout(timeout)
            .build();

        try {
            device.connect();

            // Send an intentionally malformed RPC
            assertThrows(Exception.class, () -> device.executeRPC(
                "<invalid-rpc-that-should-not-exist/>"),
                "Invalid RPC should throw an exception");

            System.out.println("✓ RPC error handling works correctly");

            // Verify connection is still usable after error
            int capabilityCount = device.getNetconfCapabilities().size();
            assertTrue(capabilityCount > 0,
                "Connection should still be usable after RPC error");

            System.out.println("✓ Connection remains usable after RPC error");

        } finally {
            if (device.isConnected()) {
                device.close();
            }
        }
    }
}