/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import net.juniper.netconf.element.RpcError;
import net.juniper.netconf.element.RpcReply;

import java.util.List;
import java.util.StringJoiner;

/**
 * Exception thrown when a NETCONF RPC returns one or more {@code <rpc-error>}
 * elements.
 */
public class RpcErrorException extends NetconfException {

    /**
     * Parsed NETCONF reply associated with the exception, when available.
     */
    private final RpcReply rpcReply;

    /**
     * Creates a new exception with the provided message.
     *
     * @param message human-readable description of the failure
     */
    public RpcErrorException(String message) {
        this(message, null, null);
    }

    /**
     * Creates a new exception with the provided message and root cause.
     *
     * @param message human-readable description of the failure
     * @param cause root cause for the failure
     */
    public RpcErrorException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Creates a new exception that wraps a root cause.
     *
     * @param cause root cause for the failure
     */
    public RpcErrorException(Throwable cause) {
        this(cause == null ? null : cause.getMessage(), null, cause);
    }

    /**
     * Creates a new exception with the provided message and parsed RPC reply.
     *
     * @param message human-readable description of the failure
     * @param rpcReply parsed NETCONF reply that triggered the exception
     */
    public RpcErrorException(String message, RpcReply rpcReply) {
        this(message, rpcReply, null);
    }

    /**
     * Creates a new exception with a message, parsed reply, and root cause.
     *
     * @param message human-readable description of the failure
     * @param rpcReply parsed NETCONF reply that triggered the exception
     * @param cause root cause for the failure
     */
    public RpcErrorException(String message, RpcReply rpcReply, Throwable cause) {
        super(message, cause);
        this.rpcReply = rpcReply;
    }

    /**
     * Returns the parsed NETCONF {@code rpc-reply}, if available.
     *
     * @return parsed reply or {@code null} when unavailable
     */
    public RpcReply getRpcReply() {
        return rpcReply;
    }

    /**
     * Returns the parsed NETCONF {@code rpc-error} elements from the reply.
     *
     * <p>The returned list may contain both error- and warning-severity
     * {@link RpcError} objects because both are represented as
     * {@code <rpc-error>} elements on the wire.</p>
     *
     * @return immutable snapshot of parsed rpc-error elements
     */
    public List<RpcError> getRpcErrors() {
        return rpcReply == null ? List.of() : List.copyOf(rpcReply.getErrors());
    }

    static String buildMessage(String operation, RpcReply rpcReply) {
        String normalizedOperation = operation == null || operation.isBlank()
            ? "NETCONF operation"
            : operation;

        if (rpcReply == null) {
            return normalizedOperation + " returned an error reply.";
        }

        List<RpcError> rpcErrors = rpcReply.getErrors();
        if (rpcErrors.isEmpty()) {
            return normalizedOperation + " returned a non-ok rpc-reply without rpc-error details.";
        }

        String issueLabel;
        if (rpcReply.hasErrors() && rpcReply.hasWarnings()) {
            issueLabel = rpcErrors.size() == 1 ? "rpc issue" : "rpc issues";
        } else if (rpcReply.hasErrors()) {
            issueLabel = rpcErrors.size() == 1 ? "rpc-error" : "rpc-errors";
        } else {
            issueLabel = rpcErrors.size() == 1 ? "rpc-warning" : "rpc-warnings";
        }

        StringJoiner summaries = new StringJoiner("; ");
        for (RpcError rpcError : rpcErrors) {
            summaries.add(formatRpcError(rpcError));
        }

        return normalizedOperation + " returned " + rpcErrors.size() + " " + issueLabel
            + ": " + summaries;
    }

    private static String formatRpcError(RpcError rpcError) {
        StringJoiner descriptors = new StringJoiner(", ", "[", "]");

        if (rpcError.errorSeverity() != null) {
            descriptors.add(rpcError.errorSeverity().getTextContent());
        }
        if (rpcError.errorTag() != null) {
            descriptors.add(rpcError.errorTag().getTextContent());
        }
        if (rpcError.errorType() != null) {
            descriptors.add(rpcError.errorType().getTextContent());
        }

        StringBuilder summary = new StringBuilder(descriptors.toString());
        String detail = firstNonBlank(rpcError.errorMessage(), rpcError.errorPath());
        if (detail != null) {
            summary.append(' ').append(detail);
        }

        if (rpcError.errorInfo() != null) {
            appendNamedDetail(summary, "bad-element", rpcError.errorInfo().getBadElement());
            appendNamedDetail(summary, "bad-attribute", rpcError.errorInfo().getBadAttribute());
            appendNamedDetail(summary, "bad-namespace", rpcError.errorInfo().getBadNamespace());
            appendNamedDetail(summary, "session-id", rpcError.errorInfo().getSessionId());
        }

        return summary.toString();
    }

    private static void appendNamedDetail(StringBuilder summary, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        summary.append(" (").append(name).append('=').append(value).append(')');
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
