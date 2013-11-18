/**
* Copyright (c) 2011 Juniper Networks, Inc.
* All Rights Reserved
*
* JUNIPER PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
*
*/

package net.juniper.netconf;

import java.io.IOException;

/**
 * Describes exceptions related to commit operation
 */
public class CommitException extends IOException {
    
    public final String CommitErrorMsg;
 
    CommitException(String msg) {
        super(msg);
        CommitErrorMsg = msg;
    }

    public String getCommitErrorMessage() {
        return CommitErrorMsg;
    }
}
