/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.util;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Check if current clocktime has drifted from NTP
 */
public class NtpChecker {

    private static final Logger logger = LoggerFactory.getLogger(NtpChecker.class);
    public static final String NTP_POOL = "pool.ntp.org";

    /**
     * Get time offset from NTP
     *
     * @return offset
     */
    public static long getTimeOffset() {
        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(10000);
        try {
            client.open();
            InetAddress hostAddr = InetAddress.getByName(NTP_POOL);
            TimeInfo info = client.getTime(hostAddr);
            info.computeDetails();
            return info.getOffset();
        } catch (IOException e) {
            logger.warn("Unable to retrieve NTP time", e);
        } finally {
            client.close();
        }

        return 0;
    }
}
