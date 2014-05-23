package hudson.plugins.performance.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.plugins.performance.Messages;
import hudson.plugins.performance.PerformancePublisher;
import hudson.plugins.performance.PerformanceReportMap;
import hudson.tasks.Builder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.InMemorySourceFile;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link NmonMonitor} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class NmonMonitor extends ResourceMonitor {

    private final String host;
    private final String name;
    private final String password;
    private final String interval;
    private Object monfile;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public NmonMonitor(String host, String name, String password, String interval) {
        this.host = host;
        this.name = name;
        this.password = password;
        this.interval = interval;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public String getInterval() {
        return interval;
    }

    @Override
    public void startMonitoring(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException {
        try {
            startMonitoring(host, name, password);
            listener.getLogger().println("----------- start monitoring by Nmon Monitor");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public Map stopMonitoring(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException {
        String nmonFile = stopMonitoring(host, name, password, build);
        listener.getLogger().println("----------- stop monitoring by Nmon Monitor");
        try {
            return runNmonAnalyzer(build, nmonFile);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(NmonMonitor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;

    }

    /**
     *
     * @param host
     * @param name
     * @param password
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void startMonitoring(String host, String name, String password) throws IOException, ClassNotFoundException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(host);
        try {
            client.authPassword(name, password);

            net.schmizz.sshj.connection.channel.direct.Session.Command cmd;
            net.schmizz.sshj.connection.channel.direct.Session session = client.startSession();
            try {

                cmd = session.exec("ls -lrt /tmp/nmon");
                cmd.join(20, TimeUnit.SECONDS);
                String outResult = IOUtils.readFully(cmd.getInputStream()).toString();

                System.out.println(outResult);



                if (outResult != null && outResult.contains("No such file or directory")) {
                    Class cls = Class.forName("hudson.plugins.performance.monitoring.NmonMonitor");
                    final URL nmonfile = getClass().getResource("/hudson/plugins/performance/monitoring/NmonMonitor/nmon");

                    final InputStream inputStream = nmonfile.openStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int read = 0;
                    byte[] buffer = new byte[1024];
                    while (read != -1) {
                        read = inputStream.read(buffer);
                        if (read != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    out.close();

                    final byte[] bytes = out.toByteArray();
                    client.newSCPFileTransfer().upload(new InMemorySourceFile() {
                        public String getName() {
                            return "nmon";
                        }

                        public long getLength() {
                            return bytes.length;
                        }

                        public InputStream getInputStream() throws IOException {
                            return new ByteArrayInputStream(bytes);
                        }
                    }, "/tmp");
                }

            } finally {
                session.close();
            }


            session = client.startSession();
            try {
                cmd = session.exec("chmod 777 /tmp/nmon;nohup /tmp/nmon -f -t -s" + interval + " -c64080 -m /tmp&");
                cmd.join(20, TimeUnit.SECONDS);
            } finally {
                session.close();
            }
        } finally {
            client.disconnect();
        }
    }

    /**
     * stop monitor
     *
     * @param host
     * @param name
     * @param password
     * @param build
     * @return
     * @throws IOException
     */
    private String stopMonitoring(String host, String name, String password, AbstractBuild build) throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(host);
        client.authPassword(name, password);

        net.schmizz.sshj.connection.channel.direct.Session.Command cmd;
        net.schmizz.sshj.connection.channel.direct.Session session = client.startSession();

        try {
            cmd = session.exec("kill -9  `ps aux | grep nmon | awk '{print $2}'`");
            cmd.join(20, TimeUnit.SECONDS);
        } finally {
            session.close();
        }

        session = client.startSession();
        String fileName = null;
        try {
            cmd = session.exec("ls -lrt /tmp/*.nmon | awk 'END{print}' | awk '{print $NF}'");
            cmd.join(20, TimeUnit.SECONDS);
            fileName = IOUtils.readFully(cmd.getInputStream()).toString();
            System.out.println("----------------------");
            System.out.println(fileName.trim());

            String tmpPlace = "/tmp/" + build.getProject().getName();
            if (fileName != null) {
                FilePath parent = new FilePath(new File(tmpPlace));
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                client.newSCPFileTransfer().download(fileName.trim(), tmpPlace);


                // copy nmon file into builds
                FilePath[] nmons = parent.list("*.nmon");
                for (FilePath src : nmons) {
                    String localMonFileName = src.getName();
                    final File localMonFile = PerformancePublisher.getPerformanceReport(build, "monitoring",
                            localMonFileName);

                    src.copyTo(new FilePath(localMonFile));
                }

                parent.deleteRecursive();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(StopMonitors.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            session.close();
            client.disconnect();
        }

        return fileName;
    }

    /**
     * parse nmon files
     *
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IOException
     */
    private Map runNmonAnalyzer(AbstractBuild<?, ?> build, String nmonFile) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
//        String[] args2 = new String[2];
//        args2[0] = "com.ibm.nmon.ReportGenerator";
//        args2[2] = "--nosummary";

        Map avgData = new HashMap();
        try {
            String nmonDir = build.getRootDir().getAbsolutePath() + "/performance-reports/monitoring/";
            nmonFile = nmonDir + nmonFile.substring(nmonFile.lastIndexOf('/')+1);
            System.err.println("---nmonFile---" + nmonFile);
            com.ibm.nmon.NmonReportGenerator nrg = new com.ibm.nmon.NmonReportGenerator(
                    nmonFile);
            System.out.println(nrg.getDataSets());
            String host = nrg.getDataSets().iterator().next().getHostname();
            File hostPath = new File(nmonDir + host);
            if (!hostPath.exists()) {
                hostPath.mkdir();
            }
            nrg.createCharts(hostPath.getAbsolutePath() + "/");
            avgData.putAll(nrg.getAverage());
        } catch (Exception e) {
            Logger.getLogger(PerformanceReportMap.class.getName()).log(Level.SEVERE, null, e);
        }

//        File rootpath = build.getRootDir();
//        File monitoringDir = new File(rootpath.getAbsoluteFile() + "/performance-reports/monitoring");
//        System.out.println("monitoringDir: " + monitoringDir.getAbsoluteFile());
////        for (File nmonf : monitoringDir.listFiles()) {
//            if (nmonf.getName().equalsIgnoreCase("charts")) {
//                return;
//            }
//        }

        //Runtime.getRuntime().exec("java -jar /tmp/test/NMONVisualizer_2013-08-06.jar com.ibm.nmon.ReportGenerator " + monitoringDir.getAbsolutePath() + " --nosummary");

//        for (File nmonf : monitoringDir.listFiles()) {
//            args2[1] = nmonf.getAbsolutePath();
//            System.out.println("nmonf: " + args2[1]);
//            
//           NmonReportGenerator.startGenerate(args2);
//           com.ibm.nmon.ReportGenerator.main(args2);
//        }



//        String path = build.getRootDir().getAbsolutePath() + "/performance-reports/monitoring";
//        try {
//            File dir = new File(path);
//            if (!dir.exists() || dir.listFiles() == null) {
//                return null;
//            }
//            for (File file : dir.listFiles()) {
//                if (file.getName().endsWith(".nmon")) {
//                    com.ibm.nmon.NmonReportGenerator nrg = new com.ibm.nmon.NmonReportGenerator(
//                            file.getAbsolutePath());
//                    String host = nrg.getDataSets().iterator().next()
//                            .getHostname();
//                    File hostPath = new File(path + "/" + host);
//                    if (!hostPath.exists()) {
//                        hostPath.mkdir();
//                    }
//                    nrg.createCharts(hostPath.getAbsolutePath() + "/");
//                    avgData.putAll(nrg.getAverage());
//                }
//            }
//        } catch (Exception e) {
//            Logger.getLogger(PerformanceReportMap.class.getName()).log(Level.SEVERE, null, e);
//        }

//        ObjectMapper mapper = new ObjectMapper();
//        mapper.writeValue(new File(path + "/resource_nmon.json"), avgData);

        return getAverageDatas(avgData);

    }

    /**
     * get average data from nmon monitored data
     *
     * @param avgData
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IOException
     */
    private Map<String, Map<String, Object>> getAverageDatas(Map avgData) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
        List<String> hosts = new ArrayList<String>();

        for (Object host : avgData.keySet()) {
            hosts.add(String.valueOf(host));
        }

        Map<String, Map<String, Object>> avgAllDatas = new HashMap<String, Map<String, Object>>();

        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        percentFormat.setMaximumFractionDigits(2);

        for (int i = 0; i < hosts.size(); i++) {
            Map<String, Object> datas = new HashMap<String, Object>();
            String host = hosts.get(i);
            avgAllDatas.put(host, datas);

            // CPU Total
            Object value = getAverageData(host, "CPU Total", avgData);
            datas.put("CPU Total", value);

            //Run Queue
            value = getAverageData(host, "Processes", avgData);
            datas.put("Run Queue", value);

            //Memory Used%
            value = getAverageData(host, "Memory MB", avgData);
            datas.put("Memory Used%", value);

            //Disk %Busy
            value = getAverageData(host, "Disk %Busy", avgData);
            datas.put("Disk %Busy", value);

            //Network I/O
            value = getAverageData(host, "Network I/O", avgData);
            datas.put("Network I/O", value);

        }

        return avgAllDatas;
    }

    /**
     * get system resource average
     *
     * @param hostname
     * @param type
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IOException
     */
    private Object getAverageData(String hostname, String type, Map avgData) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {

        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        percentFormat.setMaximumFractionDigits(2);

        if (avgData == null || avgData.get(hostname) == null) {
            return null;
        }

        if ("CPU Total".equalsIgnoreCase(type)) {
            double cpu = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("CPU%");
            return percentFormat.format(cpu / 100);
        }

        if ("Processes".equalsIgnoreCase(type)) {
            double runnableNum = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("Runnable");
            return numberFormat.format(runnableNum);
        }

        if ("Memory MB".equalsIgnoreCase(type)) {
            double cached = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("cached");
            double buffers = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("buffers");
            double memfree = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("memfree");
            double memtotal = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("memtotal");
            return percentFormat.format((memtotal - cached - buffers - memfree) / memtotal);
        }

        if ("Disk %Busy".equalsIgnoreCase(type)) {
            Map diskDatas = (Map) ((Map) avgData.get(hostname)).get(type);
            if (diskDatas.containsKey("sda")) {
                return numberFormat.format(diskDatas.get("sda")) + "%";
            }
            if (diskDatas.containsKey("vda")) {
                return numberFormat.format(diskDatas.get("vda")) + "%";
            }

            return percentFormat.format(0);
        }

        if ("Network I/O".equalsIgnoreCase(type)) {
            Map<String, Double> nettypeMap = (Map) ((Map) avgData.get(hostname)).get(type);
            if (nettypeMap == null) {
                return null;
            }

            double writeTotal = 0, readTotal = 0;

            for (Map.Entry<String, Double> entry : nettypeMap.entrySet()) {
                String netType = entry.getKey();
                Double value = entry.getValue();
                if (netType.endsWith("write-KB/s")) {
                    writeTotal = writeTotal + value;
                } else if (netType.endsWith("read-KB/s")) {
                    readTotal = readTotal + value;
                }
            }

            return numberFormat.format(writeTotal + readTotal);
        }

        return 0;
    }

    @Extension
    public static class DescriptorImpl extends ResourceMonitorDescriptor {

        @Override
        public String getDisplayName() {
            return "Nmon Monitor";
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
}
