package net.juniper.netconf.element;

import java.util.Objects;

/**
 * Represents a NETCONF RPC error with structured details as per RFC standards.
 *
 * @param errorType The type of error (e.g., transport, rpc, protocol, application).
 * @param errorTag The error tag indicating the nature of the error (e.g., unknown-element, bad-attribute).
 * @param errorSeverity The severity of the error (e.g., error or warning).
 * @param errorPath The path to the node where the error occurred.
 * @param errorMessage The human-readable message describing the error.
 * @param errorMessageLanguage The language of the error message.
 * @param errorInfo Additional structured error information.
 */
public record RpcError(net.juniper.netconf.element.RpcError.ErrorType errorType,
                       net.juniper.netconf.element.RpcError.ErrorTag errorTag,
                       net.juniper.netconf.element.RpcError.ErrorSeverity errorSeverity, String errorPath,
                       String errorMessage, String errorMessageLanguage,
                       net.juniper.netconf.element.RpcError.RpcErrorInfo errorInfo) {

    @Override
    public String toString() {
        return "RpcError{" +
            "errorType=" + errorType +
            ", errorTag=" + errorTag +
            ", errorSeverity=" + errorSeverity +
            ", errorPath='" + errorPath + '\'' +
            ", errorMessage='" + errorMessage + '\'' +
            ", errorMessageLanguage='" + errorMessageLanguage + '\'' +
            ", errorInfo=" + errorInfo +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RpcError)) return false;
        RpcError rpcError = (RpcError) o;
        return errorType == rpcError.errorType &&
            errorTag == rpcError.errorTag &&
            errorSeverity == rpcError.errorSeverity &&
            Objects.equals(errorPath, rpcError.errorPath) &&
            Objects.equals(errorMessage, rpcError.errorMessage) &&
            Objects.equals(errorMessageLanguage, rpcError.errorMessageLanguage) &&
            Objects.equals(errorInfo, rpcError.errorInfo);
    }

    /**
     * Enum representing the type of NETCONF error.
     */
    public enum ErrorType {
        /** Transport layer error. */
        TRANSPORT("transport"),
        /** Error occurred in the NETCONF RPC layer. */
        RPC("rpc"),
        /** Protocol layer error. */
        PROTOCOL("protocol"),
        /** Application layer error. */
        APPLICATION("application");

        private final String textContent;

        ErrorType(String textContent) {
            this.textContent = textContent;
        }

        /**
         * Returns the string representation of the error type.
         *
         * @return the text content of the error type
         */
        public String getTextContent() {
            return textContent;
        }

        /**
         * Converts a string to the corresponding ErrorType enum.
         *
         * @param textContent the string representation of the error type
         * @return the corresponding ErrorType, or null if no match is found
         */
        public static ErrorType from(final String textContent) {
            for (final ErrorType errorType : ErrorType.values()) {
                if (errorType.textContent.equals(textContent)) {
                    return errorType;
                }
            }
            return null;
        }
    }

    /**
     * Enum representing the tag categorizing the NETCONF error.
     */
    public enum ErrorTag {
        /** The request or response references an in-use resource. */
        IN_USE("in-use"),
        /** A value in the request is not valid. */
        INVALID_VALUE("invalid-value"),
        /** The request is too large to be processed. */
        TOO_BIG("too-big"),
        /** A required attribute is missing from the request. */
        MISSING_ATTRIBUTE("missing-attribute"),
        /** An attribute value is not correct. */
        BAD_ATTRIBUTE("bad-attribute"),
        /** An unknown or unexpected attribute is present. */
        UNKNOWN_ATTRIBUTE("unknown-attribute"),
        /** A required element is missing from the request. */
        MISSING_ELEMENT("missing-element"),
        /** An element's value is invalid or unexpected. */
        BAD_ELEMENT("bad-element"),
        /** An unknown or unexpected element is present. */
        UNKNOWN_ELEMENT("unknown-element"),
        /** The specified namespace is not recognized. */
        UNKNOWN_NAMESPACE("unknown-namespace"),
        /** The request was denied due to insufficient access rights. */
        ACCESS_DENIED("access-denied"),
        /** The requested lock could not be obtained. */
        LOCK_DENIED("lock-denied"),
        /** The data item already exists and cannot be created again. */
        DATA_EXISTS("data-exists"),
        /** The requested data item does not exist. */
        DATA_MISSING("data-missing"),
        /** The operation is not supported by the server or resource. */
        OPERATION_NOT_SUPPORTED("operation-not-supported"),
        /** The operation failed for an unspecified reason. */
        OPERATION_FAILED("operation-failed"),
        /** The operation was only partially completed. */
        PARTIAL_OPERATION("partial-operation"),
        /** The message is not well-formed or violates syntax rules. */
        MALFORMED_MESSAGE("malformed-message");

        private final String textContent;

        ErrorTag(String textContent) {
            this.textContent = textContent;
        }

        /**
         * Returns the string representation of the error tag.
         *
         * @return the text content of the error tag
         */
        public String getTextContent() {
            return textContent;
        }

        /**
         * Converts a string to the corresponding ErrorTag enum.
         *
         * @param textContent the string representation of the error tag
         * @return the corresponding ErrorTag, or null if no match is found
         */
        public static ErrorTag from(final String textContent) {
            for (final ErrorTag errorTag : ErrorTag.values()) {
                if (errorTag.textContent.equals(textContent)) {
                    return errorTag;
                }
            }
            return null;
        }
    }

    /**
     * Enum representing the severity level of the NETCONF error.
     */
    public enum ErrorSeverity {
        /** An error that causes the operation to fail. */
        ERROR("error"),
        /** A warning that does not stop the operation. */
        WARNING("warning");

        private final String textContent;

        ErrorSeverity(String textContent) {
            this.textContent = textContent;
        }

        /**
         * Returns the string representation of the error severity.
         *
         * @return the text content of the error severity
         */
        public String getTextContent() {
            return textContent;
        }

        /**
         * Converts a string to the corresponding ErrorSeverity enum.
         *
         * @param textContent the string representation of the error severity
         * @return the corresponding ErrorSeverity, or null if no match is found
         */
        public static ErrorSeverity from(final String textContent) {
            for (final ErrorSeverity errorSeverity : ErrorSeverity.values()) {
                if (errorSeverity.textContent.equals(textContent)) {
                    return errorSeverity;
                }
            }
            return null;
        }
    }

    /**
     * Represents detailed NETCONF error information contained within the rpc-error's error-info element.
     */
    public static class RpcErrorInfo {

        private final String badAttribute;
        private final String badElement;
        private final String badNamespace;
        private final String sessionId;
        private final String okElement;
        private final String errElement;
        private final String noOpElement;

        /**
         * Constructs a new RpcErrorInfo instance with detailed fields.
         *
         * @param badAttribute the name of the attribute that caused the error
         * @param badElement the name of the element that caused the error
         * @param badNamespace the namespace involved in the error
         * @param sessionId the session ID where the error occurred
         * @param okElement the XML element indicating successful operation
         * @param errElement the XML element indicating error operation
         * @param noOpElement the XML element indicating no-operation
         */
        public RpcErrorInfo(String badAttribute, String badElement, String badNamespace, String sessionId, String okElement, String errElement, String noOpElement) {
            this.badAttribute = badAttribute;
            this.badElement = badElement;
            this.badNamespace = badNamespace;
            this.sessionId = sessionId;
            this.okElement = okElement;
            this.errElement = errElement;
            this.noOpElement = noOpElement;
        }

        /**
         * Returns the bad attribute associated with the error.
         *
         * @return the bad attribute string
         */
        public String getBadAttribute() {
            return badAttribute;
        }

        /**
         * Returns the bad element associated with the error.
         *
         * @return the bad element string
         */
        public String getBadElement() {
            return badElement;
        }

        /**
         * Returns the bad namespace associated with the error.
         *
         * @return the bad namespace string
         */
        public String getBadNamespace() {
            return badNamespace;
        }

        /**
         * Returns the session ID related to the error.
         *
         * @return the session ID string
         */
        public String getSessionId() {
            return sessionId;
        }

        /**
         * Returns the ok element related to the error.
         *
         * @return the ok element string
         */
        public String getOkElement() {
            return okElement;
        }

        /**
         * Returns the error element related to the error.
         *
         * @return the error element string
         */
        public String getErrElement() {
            return errElement;
        }

        /**
         * Returns the no-op element related to the error.
         *
         * @return the no-op element string
         */
        public String getNoOpElement() {
            return noOpElement;
        }

        @Override
        public String toString() {
            return "RpcErrorInfo{" +
                "badAttribute='" + badAttribute + '\'' +
                ", badElement='" + badElement + '\'' +
                ", badNamespace='" + badNamespace + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", okElement='" + okElement + '\'' +
                ", errElement='" + errElement + '\'' +
                ", noOpElement='" + noOpElement + '\'' +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RpcErrorInfo)) return false;
            RpcErrorInfo that = (RpcErrorInfo) o;
            return Objects.equals(badAttribute, that.badAttribute) &&
                Objects.equals(badElement, that.badElement) &&
                Objects.equals(badNamespace, that.badNamespace) &&
                Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(okElement, that.okElement) &&
                Objects.equals(errElement, that.errElement) &&
                Objects.equals(noOpElement, that.noOpElement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(badAttribute, badElement, badNamespace, sessionId, okElement, errElement, noOpElement);
        }

        /**
         * Returns a new builder for constructing RpcErrorInfo instances.
         *
         * @return a new RpcErrorInfo.Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Fluent builder for constructing immutable {@link RpcErrorInfo} instances.
         * <p>
         * Call the setter‑style methods to populate fields, then invoke {@link #build()}
         * to obtain a ready‑to‑use object.  The builder is not thread‑safe.
         */
        public static class Builder {
            private String badAttribute;
            private String badElement;
            private String badNamespace;
            private String sessionId;
            private String okElement;
            private String errElement;
            private String noOpElement;

            /**
             * Creates a new builder instance with all fields initialized to null.
             */
            public Builder() {
                // Default constructor - all fields start as null
            }

            /**
             * Sets the name of the attribute that caused the error.
             *
             * @param badAttribute the attribute name
             * @return this {@code Builder} for chaining
             */
            public Builder badAttribute(String badAttribute) {
                this.badAttribute = badAttribute;
                return this;
            }

            /**
             * Sets the name of the element that caused the error.
             *
             * @param badElement the element name
             * @return this {@code Builder}
             */
            public Builder badElement(String badElement) {
                this.badElement = badElement;
                return this;
            }

            /**
             * Sets the namespace involved in the error.
             *
             * @param badNamespace the namespace URI
             * @return this {@code Builder}
             */
            public Builder badNamespace(String badNamespace) {
                this.badNamespace = badNamespace;
                return this;
            }

            /**
             * Sets the NETCONF session ID related to the error.
             *
             * @param sessionId the session ID
             * @return this {@code Builder}
             */
            public Builder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            /**
             * Sets the &lt;ok-element&gt; string.
             *
             * @param okElement element indicating success
             * @return this {@code Builder}
             */
            public Builder okElement(String okElement) {
                this.okElement = okElement;
                return this;
            }

            /**
             * Sets the &lt;err-element&gt; string.
             *
             * @param errElement element indicating error
             * @return this {@code Builder}
             */
            public Builder errElement(String errElement) {
                this.errElement = errElement;
                return this;
            }

            /**
             * Sets the &lt;noop-element&gt; string.
             *
             * @param noOpElement element indicating no‑operation
             * @return this {@code Builder}
             */
            public Builder noOpElement(String noOpElement) {
                this.noOpElement = noOpElement;
                return this;
            }

            /**
             * Builds the corresponding RpcErrorInfo instance.
             *
             * @return a new RpcErrorInfo object
             */
            public RpcErrorInfo build() {
                return new RpcErrorInfo(badAttribute, badElement, badNamespace, sessionId, okElement, errElement, noOpElement);
            }
        }
    }

    /**
     * Builder for assembling {@link RpcError} objects with optional fields.
     * <p>
     * Populate the desired attributes via the fluent setters and finish with
     * {@link #build()} to create an immutable {@code RpcError}.
     */
    public static class Builder {
        /**
         * Creates an empty {@code Builder} instance.
         */
        public Builder() {
        }
        private ErrorType errorType;
        private ErrorTag errorTag;
        private ErrorSeverity errorSeverity;
        private String errorPath;
        private String errorMessage;
        private String errorMessageLanguage;
        private RpcErrorInfo errorInfo;

        /**
         * Sets the NETCONF error type.
         *
         * @param errorType the error type
         * @return this {@code Builder}
         */
        public Builder errorType(ErrorType errorType) {
            this.errorType = errorType;
            return this;
        }

        /**
         * Sets the NETCONF error tag.
         *
         * @param errorTag the error tag
         * @return this {@code Builder}
         */
        public Builder errorTag(ErrorTag errorTag) {
            this.errorTag = errorTag;
            return this;
        }

        /**
         * Sets the severity of the error.
         *
         * @param errorSeverity severity level
         * @return this {@code Builder}
         */
        public Builder errorSeverity(ErrorSeverity errorSeverity) {
            this.errorSeverity = errorSeverity;
            return this;
        }

        /**
         * Sets the XPath path to the node where the error occurred.
         *
         * @param errorPath NETCONF error-path value
         * @return this {@code Builder}
         */
        public Builder errorPath(String errorPath) {
            this.errorPath = errorPath;
            return this;
        }

        /**
         * Sets the human‑readable error message.
         *
         * @param errorMessage descriptive message
         * @return this {@code Builder}
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Sets the language tag of the error message (e.g., "en").
         *
         * @param errorMessageLanguage IETF language tag
         * @return this {@code Builder}
         */
        public Builder errorMessageLanguage(String errorMessageLanguage) {
            this.errorMessageLanguage = errorMessageLanguage;
            return this;
        }

        /**
         * Attaches structured {@link RpcErrorInfo} details to the error.
         *
         * @param errorInfo additional structured info
         * @return this {@code Builder}
         */
        public Builder errorInfo(RpcErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
            return this;
        }

        /**
         * Builds the corresponding RpcError instance.
         *
         * @return a new RpcError object
         */
        public RpcError build() {
            return new RpcError(errorType, errorTag, errorSeverity, errorPath, errorMessage, errorMessageLanguage, errorInfo);
        }
    }

    /**
     * Returns a new builder for constructing RpcError instances.
     *
     * @return a new RpcError.Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
