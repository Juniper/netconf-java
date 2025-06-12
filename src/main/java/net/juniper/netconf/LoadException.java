/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.
*/

package net.juniper.netconf;

import java.io.IOException;

/**
 * Exception thrown when a <em>load</em> RPC returns &lt;rpc-error&gt; or otherwise
 * fails to complete successfully.
 *
 * <p>Three convenient constructors are provided so callers can supply:
 * <ol>
 *   <li>a human‑readable message only,</li>
 *   <li>a message and root cause, or</li>
 *   <li>just the root cause.</li>
 * </ol>
 */
public class LoadException extends IOException {

    /**
     * Creates a {@code LoadException} with the supplied message.
     *
     * @param message description of the load failure
     */
    public LoadException(String message) {
        super(message);
    }

    /**
     * Creates a {@code LoadException} with a message and a root cause.
     *
     * @param message description of the load failure
     * @param cause   underlying exception that triggered the failure
     */
    public LoadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a {@code LoadException} that wraps an underlying cause.
     *
     * @param cause underlying exception that triggered the failure
     */
    public LoadException(Throwable cause) {
        super(cause);
    }
}
