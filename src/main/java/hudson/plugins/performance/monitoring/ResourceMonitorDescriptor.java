package hudson.plugins.performance.monitoring;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.model.Hudson;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class ResourceMonitorDescriptor extends
    Descriptor<ResourceMonitor> {

  /**
   * Internal unique ID that distinguishes a parser.
   */
  public final String getId() {
    return getClass().getName();
  }

  /**
   * Returns all the registered {@link ResourceMonitorDescriptor}s.
   */
  public static DescriptorExtensionList<ResourceMonitor, ResourceMonitorDescriptor> all() {
    return Hudson.getInstance().<ResourceMonitor, ResourceMonitorDescriptor>getDescriptorList(ResourceMonitor.class);
  }

  public static ResourceMonitorDescriptor getById(String id) {
    for (ResourceMonitorDescriptor d : all())
      if (d.getId().equals(id))
        return d;
    return null;
  }
}
