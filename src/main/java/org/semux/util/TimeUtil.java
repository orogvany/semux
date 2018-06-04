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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class TimeUtil {

    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);

    public static final String DEFAULT_DURATION_FORMAT = "%02d:%02d:%02d";
    public static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String NTP_POOL = "pool.ntp.org";

    /**
     * Returns a human-readable duration
     * 
     * @param duration
     *            duration object to be formatted
     * @return formatted duration in 00:00:00
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        return String.format(DEFAULT_DURATION_FORMAT, seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }

    /**
     *
     * @param timestamp
     *            timestamp in milliseconds to be formatter
     * @return formatted timestamp in yyyy-MM-dd HH:mm:ss
     */
    public static String formatTimestamp(Long timestamp) {
        return new SimpleDateFormat(DEFAULT_TIMESTAMP_FORMAT).format(new Date(timestamp));
    }

    /**
     * Get time offset from NTP
     *
     * @return offset
     */
    public static long getNetworkTimeOffset() {
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

    private TimeUtil() {
    }
}
