#!/bin/bash

# run-integration-tests.sh
# Script to run netconf-java integration tests with interactive credential prompts

set -e

echo "=== NetConf Java Integration Test Runner ==="
echo "This script will run integration tests against a real network device."
echo "You will be prompted for connection details if not provided via environment."
echo ""

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Parse command line arguments
INTERACTIVE=true
SKIP_COMPILE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            NETCONF_HOST="$2"
            shift 2
            ;;
        --username)
            NETCONF_USERNAME="$2"
            shift 2
            ;;
        --password)
            NETCONF_PASSWORD="$2"
            shift 2
            ;;
        --port)
            NETCONF_PORT="$2"
            shift 2
            ;;
        --timeout)
            NETCONF_TIMEOUT="$2"
            shift 2
            ;;
        --skip-compile)
            SKIP_COMPILE=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --host <hostname>      Device hostname or IP address"
            echo "  --username <username>  SSH username"
            echo "  --password <password>  SSH password"
            echo "  --port <port>         NETCONF port (default: 830)"
            echo "  --timeout <ms>        Connection timeout in milliseconds (default: 30000)"
            echo "  --skip-compile        Skip Maven compile phase"
            echo "  --help, -h            Show this help message"
            echo ""
            echo "Environment variables can also be used:"
            echo "  NETCONF_HOST, NETCONF_USERNAME, NETCONF_PASSWORD, NETCONF_PORT, NETCONF_TIMEOUT"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set defaults from environment if not provided via command line
NETCONF_HOST=${NETCONF_HOST:-$NETCONF_HOST}
NETCONF_USERNAME=${NETCONF_USERNAME:-$NETCONF_USERNAME}
NETCONF_PASSWORD=${NETCONF_PASSWORD:-$NETCONF_PASSWORD}
NETCONF_PORT=${NETCONF_PORT:-830}
NETCONF_TIMEOUT=${NETCONF_TIMEOUT:-30000}

# Compile the project first (unless skipped)
if [ "$SKIP_COMPILE" = false ]; then
    echo "Compiling project..."
    mvn compile test-compile -q
    if [ $? -ne 0 ]; then
        echo "Error: Failed to compile project"
        exit 1
    fi
    echo "✓ Project compiled successfully"
    echo ""
fi

# Build Maven command
MVN_CMD="mvn test -Dtest=NetconfIntegrationTest -Dnetconf.integration.enabled=true"

# Add system properties if provided
if [ -n "$NETCONF_HOST" ]; then
    MVN_CMD="$MVN_CMD -Dnetconf.host=$NETCONF_HOST"
fi

if [ -n "$NETCONF_USERNAME" ]; then
    MVN_CMD="$MVN_CMD -Dnetconf.username=$NETCONF_USERNAME"
fi

if [ -n "$NETCONF_PASSWORD" ]; then
    MVN_CMD="$MVN_CMD -Dnetconf.password=$NETCONF_PASSWORD"
fi

if [ -n "$NETCONF_PORT" ]; then
    MVN_CMD="$MVN_CMD -Dnetconf.port=$NETCONF_PORT"
fi

if [ -n "$NETCONF_TIMEOUT" ]; then
    MVN_CMD="$MVN_CMD -Dnetconf.timeout=$NETCONF_TIMEOUT"
fi

echo "Running integration tests..."
echo "Command: $MVN_CMD"
echo ""

# Execute the tests
eval $MVN_CMD

echo ""
echo "=== Integration Tests Complete ==="