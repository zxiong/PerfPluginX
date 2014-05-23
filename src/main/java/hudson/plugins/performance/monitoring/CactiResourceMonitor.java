/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.performance.Messages;
import hudson.util.FormValidation;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.client.ClientProtocolException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author zxiong
 */
public class CactiResourceMonitor extends ResourceMonitor {

    private String cactiUrl;
    private String username;
    private String password;
    private String hosts;
    private List<String> monitoredHosts;
    private CactiMonitorConnector cacti;
    private long startTime = 0;
    private long endTime = 0;

    @DataBoundConstructor
    public CactiResourceMonitor(String cactiUrl, String username, String password, String hosts) {
        super();
        this.cactiUrl = cactiUrl;
        this.username = username;
        this.password = password;
        this.hosts = hosts;

        if (hosts != null && !"".equals(hosts)) {
            monitoredHosts = new ArrayList<String>();
            String[] allHosts = hosts.split(",");
            for (String host : allHosts) {
                monitoredHosts.add(host);
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends ResourceMonitorDescriptor {

        @Override
        public String getDisplayName() {
            return "Cacti Monitor";
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


        cacti = new CactiMonitorConnector(
                cactiUrl, username, password);
        cacti.login();

        for (String host : monitoredHosts) {
            cacti.enableHost(host);
        }

        startTime = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    public Map stopMonitoring(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException {
        listener.getLogger().println("----------- stop monitoring " + hosts + " by Cacti");

        cacti.login();

        for (String host : monitoredHosts) {
            cacti.disableHost(host);
        }

        endTime = Calendar.getInstance().getTimeInMillis();

        return getCactiMonitoredDatas(build,launcher,listener);


    }

    public Map<String, Map<String, Float>> getCactiMonitoredDatas(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) {
        Map<String, Map<String, Float>> allAvgDatas = null;
        try {
            File rootpath = build.getRootDir();
            File monitoringDir = new File(rootpath.getAbsoluteFile() + "/performance-reports/monitoring");
            if (!monitoringDir.exists()){
                monitoringDir.mkdirs();
            }
            
            System.out.println(monitoringDir.getAbsolutePath());
            
            BufferedWriter avgDataFile = new BufferedWriter(new FileWriter(new File(monitoringDir.getAbsolutePath()+"/avgdata.txt")));

            cacti.login();
            String startTime = cacti.conver2MonitorServerTime(this.startTime-3600000);
            String endTime = cacti.conver2MonitorServerTime(this.endTime-100000);
            allAvgDatas = cacti.getAverageMonitoredData(monitoredHosts, startTime, endTime);
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(monitoringDir.getAbsolutePath() +"/resource_cacti.json"), allAvgDatas);            

//            List<String> types = new ArrayList<String>();
//
//            Iterator<String> its = allAvgDatas.keySet().iterator();
//            while (its.hasNext()) {
//                String server = its.next();
//
//                Map<String, Float> avgDatas = allAvgDatas.get(server);
//                if (types.size() == 0) {
//                    ArrayList<Float> values = new ArrayList<Float>();
//                    avgDataFile.append("Host");
//                    Iterator<String> it = avgDatas.keySet().iterator();
//                    while (it.hasNext()) {
//                        String title = it.next();
//                        types.add(title);                       
//                        avgDataFile.append(",").append(title);
//                        values.add(avgDatas.get(title));
//                    }
//
//                    avgDataFile.append("\n").append(server);
//                    for (Float value : values) {
//                        avgDataFile.append(",").append(value.toString());
//                    }
//                    avgDataFile.append("\n");
//                }else{
//                    avgDataFile.append("\n").append(server);
//                    for (String type : types) {
//                        Float value = avgDatas.get(type);
//                        avgDataFile.append(",").append(value.toString());
//                    }
//                }
//            }
            
            avgDataFile.flush();
            avgDataFile.close();

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return allAvgDatas;
    }

    public String getCactiUrl() {
        return cactiUrl;
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

    public List<String> getMonitoredHosts() {
        return monitoredHosts;
    }
}
