package net.juniper.netconf;

/**
 * Raised when an operation requires a NETCONF capability the server did not
 * advertise in its {@code <hello>}.
 */
public class UnsupportedCapabilityException extends NetconfException {

    /**
     * Operation blocked because the server did not advertise the required capability.
     */
    private final String operation;
    /**
     * Capability required for the attempted operation.
     */
    private final String requiredCapability;

    /**
     * Creates an exception describing the missing capability.
     *
     * @param operation          operation that was attempted
     * @param requiredCapability capability required to perform it
     */
    public UnsupportedCapabilityException(String operation, String requiredCapability) {
        super("NETCONF server does not advertise capability '" + requiredCapability
            + "' required for operation '" + operation + "'");
        this.operation = operation;
        this.requiredCapability = requiredCapability;
    }

    /**
     * Returns the operation that was blocked by capability negotiation.
     *
     * @return operation name used in the exception message
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the capability required for the attempted operation.
     *
     * @return NETCONF capability URI or capability label
     */
    public String getRequiredCapability() {
        return requiredCapability;
    }
}
