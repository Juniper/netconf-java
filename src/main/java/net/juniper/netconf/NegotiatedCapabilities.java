package net.juniper.netconf;

import java.util.List;
import java.util.Objects;

/**
 * Immutable view of the capabilities available to a NETCONF session.
 * <p>
 * The base protocol version is negotiated from both peers' {@code <hello>}
 * messages. Optional capabilities reflect what the server advertised, while
 * the raw client and server capability lists are preserved for inspection.
 */
public final class NegotiatedCapabilities {

    /**
     * Negotiated NETCONF base version for the session.
     */
    public enum BaseVersion {
        /**
         * NETCONF 1.0 session using end-of-message delimiter framing.
         */
        NETCONF_1_0,
        /**
         * NETCONF 1.1 session using RFC 6242 chunked framing.
         */
        NETCONF_1_1
    }

    private static final String LEGACY_CAPABILITY_PREFIX =
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#";
    private static final String MODERN_CAPABILITY_PREFIX =
        "urn:ietf:params:netconf:capability:";
    private static final String MODERN_CANDIDATE_PREFIX = MODERN_CAPABILITY_PREFIX + "candidate:";
    private static final String MODERN_VALIDATE_PREFIX = MODERN_CAPABILITY_PREFIX + "validate:";
    private static final String MODERN_CONFIRMED_COMMIT_PREFIX = MODERN_CAPABILITY_PREFIX + "confirmed-commit:";
    private static final String MODERN_WRITABLE_RUNNING_PREFIX = MODERN_CAPABILITY_PREFIX + "writable-running:";
    private static final String MODERN_STARTUP_PREFIX = MODERN_CAPABILITY_PREFIX + "startup:";
    private static final String MODERN_URL_PREFIX = MODERN_CAPABILITY_PREFIX + "url:";
    private static final String MODERN_XPATH_PREFIX = MODERN_CAPABILITY_PREFIX + "xpath:";
    private static final String MODERN_CONFIRMED_COMMIT_1_1 =
        MODERN_CONFIRMED_COMMIT_PREFIX + "1.1";

    private final List<String> clientCapabilities;
    private final List<String> serverCapabilities;
    private final BaseVersion baseVersion;
    private final boolean candidate;
    private final boolean validate;
    private final boolean confirmedCommit;
    private final boolean confirmedCommit11;
    private final boolean writableRunning;
    private final boolean startup;
    private final boolean url;
    private final boolean xpath;

    private NegotiatedCapabilities(
        List<String> clientCapabilities,
        List<String> serverCapabilities,
        BaseVersion baseVersion,
        boolean candidate,
        boolean validate,
        boolean confirmedCommit,
        boolean confirmedCommit11,
        boolean writableRunning,
        boolean startup,
        boolean url,
        boolean xpath
    ) {
        this.clientCapabilities = List.copyOf(clientCapabilities);
        this.serverCapabilities = List.copyOf(serverCapabilities);
        this.baseVersion = Objects.requireNonNull(baseVersion, "baseVersion");
        this.candidate = candidate;
        this.validate = validate;
        this.confirmedCommit = confirmedCommit;
        this.confirmedCommit11 = confirmedCommit11;
        this.writableRunning = writableRunning;
        this.startup = startup;
        this.url = url;
        this.xpath = xpath;
    }

    static NegotiatedCapabilities fromCapabilities(
        List<String> clientCapabilities,
        List<String> serverCapabilities
    ) throws NetconfException {
        List<String> safeClientCapabilities =
            clientCapabilities == null ? List.of() : List.copyOf(clientCapabilities);
        List<String> safeServerCapabilities =
            serverCapabilities == null ? List.of() : List.copyOf(serverCapabilities);

        BaseVersion negotiatedBaseVersion =
            negotiateBaseVersion(safeClientCapabilities, safeServerCapabilities);

        return new NegotiatedCapabilities(
            safeClientCapabilities,
            safeServerCapabilities,
            negotiatedBaseVersion,
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "candidate",
                MODERN_CANDIDATE_PREFIX
            ),
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "validate",
                MODERN_VALIDATE_PREFIX
            ),
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "confirmed-commit",
                MODERN_CONFIRMED_COMMIT_PREFIX
            ),
            safeServerCapabilities.contains(MODERN_CONFIRMED_COMMIT_1_1),
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "writable-running",
                MODERN_WRITABLE_RUNNING_PREFIX
            ),
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "startup",
                MODERN_STARTUP_PREFIX
            ),
            hasLegacyOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "url",
                MODERN_URL_PREFIX
            ),
            hasLegacyCapabilityOrModernPrefix(
                safeServerCapabilities,
                LEGACY_CAPABILITY_PREFIX + "xpath",
                MODERN_XPATH_PREFIX
            )
        );
    }

    private static BaseVersion negotiateBaseVersion(
        List<String> clientCapabilities,
        List<String> serverCapabilities
    ) throws NetconfException {
        boolean clientSupportsBase11 =
            clientCapabilities.contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);
        boolean serverSupportsBase11 =
            serverCapabilities.contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);
        if (clientSupportsBase11 && serverSupportsBase11) {
            return BaseVersion.NETCONF_1_1;
        }

        boolean clientSupportsBase10 =
            clientCapabilities.contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);
        boolean serverSupportsBase10 =
            serverCapabilities.contains(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0);
        if (clientSupportsBase10 && serverSupportsBase10) {
            return BaseVersion.NETCONF_1_0;
        }

        throw new NetconfException(
            "Client and server do not share a common NETCONF base capability. client="
                + clientCapabilities + ", server=" + serverCapabilities
        );
    }

    private static boolean hasLegacyCapabilityOrModernPrefix(
        List<String> capabilities,
        String legacyCapability,
        String modernPrefix
    ) {
        return capabilities.stream()
            .anyMatch(capability -> capability.equals(legacyCapability)
                || capability.startsWith(modernPrefix));
    }

    private static boolean hasLegacyOrModernPrefix(
        List<String> capabilities,
        String legacyPrefix,
        String modernPrefix
    ) {
        return capabilities.stream()
            .anyMatch(capability -> capability.startsWith(legacyPrefix)
                || capability.startsWith(modernPrefix));
    }

    /**
     * Returns the negotiated NETCONF base version.
     *
     * @return negotiated base version shared by the client and server
     */
    public BaseVersion getBaseVersion() {
        return baseVersion;
    }

    /**
     * Indicates whether the session should use NETCONF 1.1 chunked framing.
     *
     * @return {@code true} for NETCONF 1.1 sessions, {@code false} for 1.0
     */
    public boolean usesChunkedFraming() {
        return baseVersion == BaseVersion.NETCONF_1_1;
    }

    /**
     * Indicates whether the server advertised candidate datastore support.
     *
     * @return {@code true} if candidate operations are supported
     */
    public boolean supportsCandidate() {
        return candidate;
    }

    /**
     * Indicates whether the server advertised validate support.
     *
     * @return {@code true} if {@code <validate>} is supported
     */
    public boolean supportsValidate() {
        return validate;
    }

    /**
     * Indicates whether the server advertised confirmed-commit support.
     *
     * @return {@code true} if confirmed commit is supported
     */
    public boolean supportsConfirmedCommit() {
        return confirmedCommit;
    }

    /**
     * Indicates whether the server advertised confirmed-commit 1.1 support.
     *
     * @return {@code true} if persist/persist-id confirmed commit is supported
     */
    public boolean supportsConfirmedCommit11() {
        return confirmedCommit11;
    }

    /**
     * Indicates whether the server advertised writable-running support.
     *
     * @return {@code true} if direct running datastore edits are supported
     */
    public boolean supportsWritableRunning() {
        return writableRunning;
    }

    /**
     * Indicates whether the server advertised startup datastore support.
     *
     * @return {@code true} if startup datastore operations are supported
     */
    public boolean supportsStartup() {
        return startup;
    }

    /**
     * Indicates whether the server advertised URL capability support.
     *
     * @return {@code true} if URL-based operations are supported
     */
    public boolean supportsUrl() {
        return url;
    }

    /**
     * Indicates whether the server advertised XPath filtering support.
     *
     * @return {@code true} if XPath filters are supported
     */
    public boolean supportsXPath() {
        return xpath;
    }

    /**
     * Returns the client capability list used during the hello exchange.
     *
     * @return immutable list of client-advertised capabilities
     */
    public List<String> getClientCapabilities() {
        return clientCapabilities;
    }

    /**
     * Returns the capability list advertised by the server hello.
     *
     * @return immutable list of server-advertised capabilities
     */
    public List<String> getServerCapabilities() {
        return serverCapabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NegotiatedCapabilities that)) {
            return false;
        }
        return candidate == that.candidate
            && validate == that.validate
            && confirmedCommit == that.confirmedCommit
            && confirmedCommit11 == that.confirmedCommit11
            && writableRunning == that.writableRunning
            && startup == that.startup
            && url == that.url
            && xpath == that.xpath
            && clientCapabilities.equals(that.clientCapabilities)
            && serverCapabilities.equals(that.serverCapabilities)
            && baseVersion == that.baseVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            clientCapabilities,
            serverCapabilities,
            baseVersion,
            candidate,
            validate,
            confirmedCommit,
            confirmedCommit11,
            writableRunning,
            startup,
            url,
            xpath
        );
    }

    @Override
    public String toString() {
        return "NegotiatedCapabilities{"
            + "baseVersion=" + baseVersion
            + ", candidate=" + candidate
            + ", validate=" + validate
            + ", confirmedCommit=" + confirmedCommit
            + ", confirmedCommit11=" + confirmedCommit11
            + ", writableRunning=" + writableRunning
            + ", startup=" + startup
            + ", url=" + url
            + ", xpath=" + xpath
            + '}';
    }
}
