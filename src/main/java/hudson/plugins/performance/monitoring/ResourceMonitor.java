package hudson.plugins.performance.monitoring;

import hudson.Launcher;
import hudson.plugins.performance.*;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;


import java.io.*;
import java.util.Map;

/**
 * Parses performance result files into {@link PerformanceReport}s. This object
 * is persisted with {@link PerformancePublisher} into the project
 * configuration.
 *
 * <p>
 * Subtypes can define additional parser-specific parameters as instance fields.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ResourceMonitor implements Describable<ResourceMonitor>, ExtensionPoint {

    /**
     * key words to be used to transfer data between build steps
     */
    public static String ParameterName = "ResourceMonitor";

    @DataBoundConstructor
    public ResourceMonitor() {
    }

    public ResourceMonitorDescriptor getDescriptor() {
        return (ResourceMonitorDescriptor) Hudson.getInstance().getDescriptorOrDie(
                getClass());
    }

    /**
     * start monitor.
     */
    public abstract void startMonitoring(
            AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException;

    /**
     * stop monitor
     */
    public abstract Map stopMonitoring(
            AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException;

    /**
     * All registered implementations.
     */
    public static ExtensionList<ResourceMonitor> all() {
        return Hudson.getInstance().getExtensionList(ResourceMonitor.class);
    }
}
