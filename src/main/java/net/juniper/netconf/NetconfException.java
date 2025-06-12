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
    /**
     * Constructs a {@code NetconfException} with the specified detail message.
     *
     * @param msg the detail message that describes the exception
     */
    public NetconfException(String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code NetconfException} with the specified detail message
     * and underlying cause.
     *
     * @param msg the detail message
     * @param t   the throwable that caused this exception
     */
    public NetconfException(String msg, Throwable t) {
        super(msg, t);
    }
}
