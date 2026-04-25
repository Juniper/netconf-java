/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import net.juniper.netconf.element.RpcReply;

/**
 * Exception thrown when a NETCONF {@code <validate>} RPC returns
 * one or more {@code <rpc-error>} elements.
 */
public class ValidateException extends RpcErrorException {

    /**
     * Creates a {@code ValidateException} with the supplied message.
     *
     * @param message description of the validation failure
     */
    public ValidateException(String message) {
        super(message);
    }

    /**
     * Creates a {@code ValidateException} with the supplied message and reply.
     *
     * @param message description of the validation failure
     * @param rpcReply parsed NETCONF reply
     */
    public ValidateException(String message, RpcReply rpcReply) {
        super(message, rpcReply);
    }
}
