/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance.monitoring;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.performance.Messages;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author zxiong
 */
public class ZabbixMonitor extends ResourceMonitor {

    private String zabbixUrl;
    private String username;
    private String password;
    private String hosts;
    private String items;
    private List<String> monitoredHosts;
    private List<String> itemKeys;
    private ZabbixMonitorUtil zabbix;
    private long startTime = 0;
    private long endTime = 0;

    @DataBoundConstructor
    public ZabbixMonitor(String zabbixUrl, String username, String password, String hosts, String items) {
        super();
        this.zabbixUrl = zabbixUrl;
        this.username = username;
        this.password = password;
        this.hosts = hosts;
        this.items = items;

        if (hosts != null && !"".equals(hosts)) {
            monitoredHosts = new ArrayList<String>();
            String[] allHosts = hosts.split(";");
            for (String host : allHosts) {
                monitoredHosts.add(host);
            }
            itemKeys = new ArrayList<String>();
            String[] allKeys = items.split(";");
            for (String key : allKeys) {
                itemKeys.add(key);
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends ResourceMonitorDescriptor {

        @Override
        public String getDisplayName() {
            return "Zabbix Monitor";
        }

        public FormValidation doCheckDelimiter(@QueryParameter String delimiter) {
            if (delimiter == null || delimiter.isEmpty()) {
                return FormValidation.error(Messages.CsvParser_validation_delimiterEmpty());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPattern(@QueryParameter String pattern) {
            if (pattern == null || pattern.isEmpty()) {
                FormValidation.error(Messages.CsvParser_validation_patternEmpty());
            }

            return null;
        }

        private void validatePresent(Set<String> missing, String pattern,
                String string) {
            if (!pattern.contains(string)) {
                missing.add(string);
            }
        }
    }

    @Override
    public void startMonitoring(
            AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException {
        listener.getLogger().println("----------- start monitoring " + hosts + " by Cacti");

        zabbix = new ZabbixMonitorUtil(zabbixUrl, username, password);
        zabbix.login();

        for (String host : monitoredHosts) {
            zabbix.setHostStatus(host, "0");
        }
        zabbix.logout();

        startTime = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    public Map stopMonitoring(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException {
        listener.getLogger().println("----------- stop monitoring " + hosts + " by Zabbix");

        zabbix.login();
        for (String host : monitoredHosts) {
            zabbix.setHostStatus(host, "1");
        }

        endTime = Calendar.getInstance().getTimeInMillis();
        Map<String, Map<String, Float>> allAvgDatas = zabbix.getAverageMonitoredData(monitoredHosts, itemKeys, String.valueOf(startTime), String.valueOf(endTime));

        zabbix.logout();

        return allAvgDatas;
    }

    public String getZabbixUrl() {
        return this.zabbixUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHosts() {
        return hosts;
    }
    
    public String getItems() {
        return items;
    }

    public List<String> getMonitoredHosts() {
        return monitoredHosts;
    }
}
