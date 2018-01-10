package org.semux.util;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check if current clocktime has drifted from NTP
 */
public class NTPChecker {

    private static final Logger logger = LoggerFactory.getLogger(NTPChecker.class);
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
