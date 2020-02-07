/*
 Copyright (c) 2013 Juniper Networks, Inc.
 All Rights Reserved

 Use is subject to license terms.

*/

package net.juniper.netconf;

import java.io.IOException;

/** 
 * Describes exceptions related to load operation
 */
public class LoadException extends IOException {

    LoadException(String msg) {
        super(msg);
    }
}
