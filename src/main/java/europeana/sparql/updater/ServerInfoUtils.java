package europeana.sparql.updater;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ServerInfoUtils {

    private static final Logger LOG = LogManager.getLogger(ServerInfoUtils.class);
    private static final String KUBERNETES_LOCAL_HOST = ".svc.cluster.local";
    private static final int BYTES_PER_GIGABYTE = 1024 * 1024 * 1024;

    private ServerInfoUtils() {
        // empty constructor to prevent initialization
    }

    /**
     * Retrieve host name or IP address of the server.
     * @return string containing host name or IP address
     */
    public static String getServerId() {
        String result = "unknown";
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            result = inetAddress.getCanonicalHostName();
            if (result == null) {
                result = inetAddress.getHostName();
            }
            if (result == null) {
                result = inetAddress.getHostAddress();
            }
            if (result != null && result.endsWith(KUBERNETES_LOCAL_HOST)) {
                result = result.split("\\.")[0]; // only keep first part in long k8s hostnames
            }
        } catch (UnknownHostException e) {
            LOG.warn("Unable to retrieve local IP address", e);
        }
        return result;
    }

    /**
     * Check how much disk space is used on a particular disk
     * @param file any accessible file located on the disk to report on
     * @return descriptive string indicating how much disk space is used on the disk where the provided file is located,
     * in the form of "Disk used is <x> GB (<y>%)".
     */
    public static String getDiskUsage(File file) {
        long free = file.getFreeSpace();
        long total = file.getTotalSpace();
        double used = total - (double) free;
        StringBuilder s = new StringBuilder("Disk usage is ");
        s.append(round(used / BYTES_PER_GIGABYTE, 1))
                .append(" GB (")
                .append(Math.round(used / total * 100))
                .append("%)");
        return s.toString();
    }

    private static double round (double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
    }


}
