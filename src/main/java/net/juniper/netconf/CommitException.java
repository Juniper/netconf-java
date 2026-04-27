/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import net.juniper.netconf.element.RpcReply;

/**
 * Describes exceptions related to commit operation
 */
public class CommitException extends RpcErrorException {
    CommitException(String msg) {
        super(msg);
    }

    CommitException(String msg, RpcReply rpcReply) {
        super(msg, rpcReply);
    }
}
