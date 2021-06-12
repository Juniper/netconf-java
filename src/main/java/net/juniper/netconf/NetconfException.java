/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import java.io.IOException;

/**
 * Describes exceptions related to establishing Netconf session.
 */
public class NetconfException extends IOException {
    public NetconfException(String msg) {
        super(msg);
    }

    public NetconfException(String msg, Throwable t) {
        super(msg, t);
    }
}
