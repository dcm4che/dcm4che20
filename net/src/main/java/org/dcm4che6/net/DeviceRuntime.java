package org.dcm4che6.net;

import org.dcm4che6.conf.model.Device;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2019
 */
public class DeviceRuntime {
    private final Device device;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;

    public DeviceRuntime(Device device, ExecutorService executorService,
            ScheduledExecutorService scheduledExecutorService) {
        this.device = Objects.requireNonNull(device);
        this.executorService = Objects.requireNonNull(executorService);
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
    }
}
