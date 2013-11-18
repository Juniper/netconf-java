/**
* Copyright (c) 2013 Juniper Networks, Inc.
* All Rights Reserved
*
* Use is subject to license terms.
*
*/

package net.juniper.netconf;

import java.io.IOException;

/**
 * Describes exceptions related to establishing Netconf session.
 */
public class NetconfException extends IOException {
    
    public final String netconfErrorMsg;
 
    NetconfException(String msg) {
        super(msg);
        netconfErrorMsg = msg;
    }

    public String getNetconfErrorMessage() {
        return netconfErrorMsg;
    }
}
