package hudson.plugins.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.AbstractBuild;
import hudson.model.ModelObject;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

import hudson.model.TaskListener;
import hudson.util.ChartUtil;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.DataSetBuilder;
import java.awt.image.BufferedImage;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Root object of a performance report.
 */
public class PerformanceReportMap implements ModelObject {

    /**
     * The {@link PerformanceBuildAction} that this report belongs to.
     */
    private transient PerformanceBuildAction buildAction;
    /**
     * {@link PerformanceReport}s are keyed by
     * {@link PerformanceReport#reportFileName}
     *
     * Test names are arbitrary human-readable and URL-safe string that
     * identifies an individual report.
     */
    private Map<String, PerformanceReport> performanceReportMap = new LinkedHashMap<String, PerformanceReport>();
    private static final String PERFORMANCE_REPORTS_DIRECTORY = "performance-reports";
    private static final String PLUGIN_NAME = "performance";
    private static final String TRENDREPORT_LINK = "trendReport";
    private static AbstractBuild<?, ?> currentBuild = null;
    private Map avgData = null;
    // cache the compared build id
    private String cmpbuildid = "";
    private Map<String, Object> cactiMonitoredDatas = null;

    /**
     * Parses the reports and build a {@link PerformanceReportMap}.
     *
     * @throws IOException If a report fails to parse.
     */
    PerformanceReportMap(final PerformanceBuildAction buildAction,
            TaskListener listener) throws IOException {
        this.buildAction = buildAction;
        parseReports(getBuild(), listener, new PerformanceReportCollector() {
            public void addAll(Collection<PerformanceReport> reports) {
                for (PerformanceReport r : reports) {
                    r.setBuildAction(buildAction);
                    performanceReportMap.put(r.getReportFileName(), r);
                }
            }
        }, null);
    }

    private void addAll(Collection<PerformanceReport> reports) {
        for (PerformanceReport r : reports) {
            r.setBuildAction(buildAction);
            performanceReportMap.put(r.getReportFileName(), r);
        }
    }

    public AbstractBuild<?, ?> getBuild() {
        return buildAction.getBuild();
    }

    PerformanceBuildAction getBuildAction() {
        return buildAction;
    }

    public String getDisplayName() {
        return Messages.Report_DisplayName();
    }

    public List<PerformanceReport> getPerformanceListOrdered() {
        List<PerformanceReport> listPerformance = new ArrayList<PerformanceReport>(
                getPerformanceReportMap().values());
        Collections.sort(listPerformance);
        return listPerformance;
    }

    public Map<String, PerformanceReport> getPerformanceReportMap() {
        return performanceReportMap;
    }

    /**
     * <p>
     * Give the Performance report with the parameter for name in Bean
     * </p>
     *
     * @param performanceReportName
     * @return
     */
    public PerformanceReport getPerformanceReport(String performanceReportName) {
        return performanceReportMap.get(performanceReportName);
    }

    /**
     * Get a URI report within a Performance report file
     *
     * @param uriReport "Performance report file name";"URI name"
     * @return
     */
    public UriReport getUriReport(String uriReport) {
        if (uriReport != null) {
            String uriReportDecoded;
            try {
                uriReportDecoded = URLDecoder
                        .decode(uriReport.replace(UriReport.END_PERFORMANCE_PARAMETER, ""),
                        "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
            StringTokenizer st = new StringTokenizer(uriReportDecoded,
                    GraphConfigurationDetail.SEPARATOR);
            return getPerformanceReportMap().get(st.nextToken()).getUriReportMap()
                    .get(st.nextToken());
        } else {
            return null;
        }
    }

    public String getUrlName() {
        return PLUGIN_NAME;
    }

    void setBuildAction(PerformanceBuildAction buildAction) {
        this.buildAction = buildAction;
    }

    public void setPerformanceReportMap(
            Map<String, PerformanceReport> performanceReportMap) {
        this.performanceReportMap = performanceReportMap;
    }

    public static String getPerformanceReportFileRelativePath(
            String parserDisplayName, String reportFileName) {
        return getRelativePath(parserDisplayName, reportFileName);
    }

    public static String getPerformanceReportDirRelativePath() {
        return getRelativePath();
    }

    private static String getRelativePath(String... suffixes) {
        StringBuilder sb = new StringBuilder(100);
        sb.append(PERFORMANCE_REPORTS_DIRECTORY);
        for (String suffix : suffixes) {
            sb.append(File.separator).append(suffix);
        }
        return sb.toString();
    }

    /**
     * <p>
     * Verify if the PerformanceReport exist the performanceReportName must to
     * be like it is in the build
     * </p>
     *
     * @param performanceReportName
     * @return boolean
     */
    public boolean isFailed(String performanceReportName) {
        return getPerformanceReport(performanceReportName) == null;
    }

    public void doRespondingTimeGraph(StaplerRequest request,
            StaplerResponse response) throws IOException {
        String parameter = request.getParameter("performanceReportPosition");
        AbstractBuild<?, ?> previousBuild = getBuild();
        final Map<AbstractBuild<?, ?>, Map<String, PerformanceReport>> buildReports = new LinkedHashMap<AbstractBuild<?, ?>, Map<String, PerformanceReport>>();
        while (previousBuild != null) {
            final AbstractBuild<?, ?> currentBuild = previousBuild;
            parseReports(currentBuild, TaskListener.NULL,
                    new PerformanceReportCollector() {
                public void addAll(Collection<PerformanceReport> parse) {
                    for (PerformanceReport performanceReport : parse) {
                        if (buildReports.get(currentBuild) == null) {
                            Map<String, PerformanceReport> map = new LinkedHashMap<String, PerformanceReport>();
                            buildReports.put(currentBuild, map);
                        }
                        buildReports.get(currentBuild).put(
                                performanceReport.getReportFileName(), performanceReport);
                    }
                }
            }, parameter);
            previousBuild = previousBuild.getPreviousBuild();
        }
        // Now we should have the data necessary to generate the graphs!
        DataSetBuilder<String, NumberOnlyBuildLabel> dataSetBuilderAverage = new DataSetBuilder<String, NumberOnlyBuildLabel>();
        for (AbstractBuild<?, ?> currentBuild : buildReports.keySet()) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(currentBuild);
            PerformanceReport report = buildReports.get(currentBuild).get(parameter);
            dataSetBuilderAverage.add(report.getAverage(),
                    Messages.ProjectAction_Average(), label);
        }
        ChartUtil.generateGraph(request, response, PerformanceProjectAction
                .createRespondingTimeChart(dataSetBuilderAverage.build()), 400, 200);
    }

    public void doSummarizerGraph(StaplerRequest request, StaplerResponse response)
            throws IOException {
        String parameter = request.getParameter("performanceReportPosition");
        AbstractBuild<?, ?> previousBuild = getBuild();
        final Map<AbstractBuild<?, ?>, Map<String, PerformanceReport>> buildReports = new LinkedHashMap<AbstractBuild<?, ?>, Map<String, PerformanceReport>>();

        while (previousBuild != null) {
            final AbstractBuild<?, ?> currentBuild = previousBuild;
            parseReports(currentBuild, TaskListener.NULL,
                    new PerformanceReportCollector() {
                public void addAll(Collection<PerformanceReport> parse) {
                    for (PerformanceReport performanceReport : parse) {
                        if (buildReports.get(currentBuild) == null) {
                            Map<String, PerformanceReport> map = new LinkedHashMap<String, PerformanceReport>();
                            buildReports.put(currentBuild, map);
                        }
                        buildReports.get(currentBuild).put(
                                performanceReport.getReportFileName(), performanceReport);
                    }
                }
            }, parameter);
            previousBuild = previousBuild.getPreviousBuild();
        }
        DataSetBuilder<NumberOnlyBuildLabel, String> dataSetBuilderSummarizer = new DataSetBuilder<NumberOnlyBuildLabel, String>();
        for (AbstractBuild<?, ?> currentBuild : buildReports.keySet()) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(currentBuild);
            PerformanceReport report = buildReports.get(currentBuild).get(parameter);

            // Now we should have the data necessary to generate the graphs!
            for (String key : report.getUriReportMap().keySet()) {
                Long methodAvg = report.getUriReportMap().get(key).getAverage();
                dataSetBuilderSummarizer.add(methodAvg, label, key);
            }
            ;
        }
        ChartUtil.generateGraph(
                request,
                response,
                PerformanceProjectAction.createSummarizerChart(
                dataSetBuilderSummarizer.build(), "ms",
                Messages.ProjectAction_RespondingTime()), 400, 200);
    }

    private void parseReports(AbstractBuild<?, ?> build, TaskListener listener,
            PerformanceReportCollector collector, final String filename)
            throws IOException {
        File repo = new File(build.getRootDir(),
                PerformanceReportMap.getPerformanceReportDirRelativePath());

        // files directly under the directory are for JMeter, for compatibility
        // reasons.
        File[] files = repo.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return !f.isDirectory() && !f.getName().contains(".serialized");
            }
        });
        // this may fail, if the build itself failed, we need to recover gracefully
        if (files != null) {
            addAll(new JMeterParser("").parse(build, Arrays.asList(files), listener));
        }

        // otherwise subdirectory name designates the parser ID.
        File[] dirs = repo.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });
        // this may fail, if the build itself failed, we need to recover gracefully
        if (dirs != null) {
            for (File dir : dirs) {
                PerformanceReportParser p = buildAction.getParserByDisplayName(dir
                        .getName());
                if (p != null) {
                    File[] listFiles = dir.listFiles(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            if (filename == null && !name.contains(".serialized")) {
                                return true;
                            }
                            if (name.equals(filename)) {
                                return true;
                            }
                            return false;
                        }
                    });
                    collector.addAll(p.parse(build, Arrays.asList(listFiles), listener));
                }
            }
        }

        addPreviousBuildReports();
    }

    private void addPreviousBuildReports() {

        // Avoid parsing all builds.
        if (PerformanceReportMap.currentBuild == null) {
            PerformanceReportMap.currentBuild = getBuild();
        } else {
            if (PerformanceReportMap.currentBuild != getBuild()) {
                PerformanceReportMap.currentBuild = null;
                return;
            }
        }

        AbstractBuild<?, ?> previousBuild = getBuild().getPreviousBuild();
        if (previousBuild == null) {
            return;
        }

        PerformanceBuildAction previousPerformanceAction = previousBuild
                .getAction(PerformanceBuildAction.class);
        if (previousPerformanceAction == null) {
            return;
        }

        PerformanceReportMap previousPerformanceReportMap = previousPerformanceAction
                .getPerformanceReportMap();
        if (previousPerformanceReportMap == null) {
            return;
        }

        for (Map.Entry<String, PerformanceReport> item : getPerformanceReportMap()
                .entrySet()) {
            PerformanceReport lastReport = previousPerformanceReportMap
                    .getPerformanceReportMap().get(item.getKey());
            if (lastReport != null) {
                item.getValue().setLastBuildReport(lastReport);
            }
        }
    }

    private interface PerformanceReportCollector {

        public void addAll(Collection<PerformanceReport> parse);
    }

    public Object getDynamic(final String link, final StaplerRequest request, final StaplerResponse response) throws ServletException, IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        if (TRENDREPORT_LINK.equals(link)) {
            return createTrendReportGraphs(request);
        } else if ("buildComparison".equals(link)) {
            // add build comparison result

            String buildid = request.getParameter("buildid");
            if (buildid == null) {
                buildid = cmpbuildid;
            } else {
                cmpbuildid = buildid;
            }

            buildid = parseBuildId(buildid);
            Map<String, List<List<String>>> reportDatas = getComparisonResult(buildid);
            Map<String, List<List>> resourceData = getAverageDatas(buildid);
            return new BuildComparison(getBuild().getProject(), getBuild(), cmpbuildid, request, reportDatas, resourceData);
        } else {
            return null;
        }
    }

    public Object createTrendReportGraphs(final StaplerRequest request) {
        String filename = getTrendReportFilename(request);
        PerformanceReport report = performanceReportMap.get(filename);
        AbstractBuild<?, ?> build = getBuild();

        TrendReportGraphs trendReport = new TrendReportGraphs(build.getProject(),
                build, request, filename, report);

        return trendReport;
    }

    private String getTrendReportFilename(final StaplerRequest request) {
        PerformanceReportPosition performanceReportPosition = new PerformanceReportPosition();
        request.bindParameters(performanceReportPosition);
        return performanceReportPosition.getPerformanceReportPosition();
    }

    /**
     * get all builds and save as list
     *
     * @return
     */
    public List<String> getBuildList() {
        List<String> buildList = new ArrayList<String>();
        AbstractBuild<?, ?> build = getBuild();
        AbstractBuild<?, ?> preBuild = build.getPreviousBuild();
        while (preBuild != null) {
            buildList.add("#" + preBuild.getNumber() + "    --------    " + preBuild.getId());
            preBuild = preBuild.getPreviousBuild();
        }

        return buildList;
    }

    /**
     * get all builds and save as list
     *
     * @return
     */
    public String parseBuildId(String buildid) {
        if (buildid != null) {
            buildid = buildid.split("    --------    ")[1];
        }

        return buildid;
    }

    /**
     * get all builds and save as list
     *
     * @return
     */
    private AbstractBuild<?, ?> getBuildByBuildId(String buildid) {
        AbstractBuild<?, ?> build = getBuild();
        AbstractBuild<?, ?> preBuild = build;
        while (preBuild != null) {
            if (preBuild.getId().equals(buildid)) {
                return preBuild;
            }
            preBuild = preBuild.getPreviousBuild();
        }

        return null;
    }

    /**
     * save data in a map classified by report file name, every row has url,
     * value 0f current build, value of compared build
     *
     * @param buildId
     * @return
     * @throws IOException
     */
    public Map<String, List<List<String>>> getComparisonResult(String buildId) throws IOException {
        Map<String, List<List<String>>> reportDatas = new HashMap<String, List<List<String>>>();

        AbstractBuild<?, ?> build = getBuild();
        AbstractBuild<?, ?> preBuild = build.getPreviousBuild();
        while (preBuild != null) {
            if (buildId.equals(preBuild.getId())) {
                break;
            }
            preBuild = preBuild.getPreviousBuild();
        }

        final AbstractBuild<?, ?> compareBuild = preBuild;
        final Map<AbstractBuild<?, ?>, Map<String, PerformanceReport>> buildReports = new LinkedHashMap<AbstractBuild<?, ?>, Map<String, PerformanceReport>>();
        parseReports(compareBuild, TaskListener.NULL, new PerformanceReportMap.PerformanceReportCollector() {
            public void addAll(Collection<PerformanceReport> reports) {
                for (PerformanceReport performanceReport : reports) {
                    if (buildReports.get(compareBuild) == null) {
                        Map<String, PerformanceReport> map = new LinkedHashMap<String, PerformanceReport>();
                        buildReports.put(compareBuild, map);
                    }
                    buildReports.get(compareBuild).put(performanceReport.getReportFileName(), performanceReport);

                }
            }
        }, null);

        if (buildReports.get(compareBuild) == null) {
            return null;
        }

        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);

        Iterator<String> reportNames = getPerformanceReportMap().keySet().iterator();
        while (reportNames.hasNext()) {
            String reportName = reportNames.next();
            PerformanceReport currentReport = getPerformanceReportMap().get(reportName);
            PerformanceReport compReport = buildReports.get(compareBuild).get(reportName);

            if (compReport == null) {
                continue;
            }

            Map<String, UriReport> currentUriReportMap = currentReport.getUriReportMap();
            Iterator<String> uris = currentUriReportMap.keySet().iterator();

            List<List<String>> datas = new ArrayList<List<String>>();
            while (uris.hasNext()) {
                String uriKey = uris.next();
                UriReport currentUriReport = currentUriReportMap.get(uriKey);
                UriReport compUriReport = compReport.getUriReportMap().get(uriKey);
                ArrayList<String> dataRow = new ArrayList<String>();
                dataRow.add(uriKey);
                if (compUriReport != null) {
                    dataRow.add(String.valueOf(compUriReport.getAverage()));
                    dataRow.add(String.valueOf(currentUriReport.getAverage()));
                    double subValue = compUriReport.getAverage() - currentUriReport.getAverage();
                    dataRow.add(String.valueOf(subValue));
                    dataRow.add(String.valueOf(percentFormat.format(subValue / compUriReport.getAverage())));
                } else {
                    dataRow.add(String.valueOf(currentUriReport.getAverage()));
                    dataRow.add("");
                }

                datas.add(dataRow);
            }

            reportDatas.put(reportName, datas);
        }

        return reportDatas;
    }

    /**
     * get resource comparison result as list
     *
     * @param cmpBuildId
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IOException
     */
    private Map<String, List<List>> getAverageDatas(String cmpBuildId) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
        List<String> hosts = getMonitoredHosts();
        if (hosts == null || avgData == null) {
            return null;
        }

        Map<String, List<List>> hostDatas = new HashMap<String, List<List>>();

        // 
        if (this.ifNmonMonitor()) {
            NumberFormat percentFormat = NumberFormat.getPercentInstance();
            NumberFormat numberFormat = NumberFormat.getNumberInstance();
            percentFormat.setMaximumFractionDigits(2);

            Map avgDataCmp = getAverageMonitoredData(getBuildByBuildId(cmpBuildId));

            for (int i = 0; i < hosts.size(); i++) {
                String host = hosts.get(i);
                List<List> resourceDatas = new ArrayList<List>();

                List rowData = new ArrayList<String>();
                Object value1 = getAverageData(host, "CPU Total", avgDataCmp);
                Object value2 = getAverageData(host, "CPU Total", avgData);
                rowData.add("CPU Total");
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "")) - Double.valueOf(String.valueOf(value2).replace("%", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                } else {
                    rowData.add(null);
                    rowData.add(null);
                }
                resourceDatas.add(rowData);

                rowData = new ArrayList<String>();
                value1 = getAverageData(host, "Processes", avgDataCmp);
                value2 = getAverageData(host, "Processes", avgData);
                rowData.add("Run Queue");
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "")) - Double.valueOf(String.valueOf(value2).replace("%", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                } else {
                    rowData.add(null);
                    rowData.add(null);
                }
                resourceDatas.add(rowData);

                rowData = new ArrayList<String>();
                value1 = getAverageData(host, "Memory MB", avgDataCmp);
                value2 = getAverageData(host, "Memory MB", avgData);
                rowData.add("Memory Used%");
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "")) - Double.valueOf(String.valueOf(value2).replace("%", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                } else {
                    rowData.add(null);
                    rowData.add(null);
                }
                resourceDatas.add(rowData);

                rowData = new ArrayList<String>();
                value1 = getAverageData(host, "Disk %Busy", avgDataCmp);
                value2 = getAverageData(host, "Disk %Busy", avgData);
                rowData.add("Disk %Busy");
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "")) - Double.valueOf(String.valueOf(value2).replace("%", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                } else {
                    rowData.add(null);
                    rowData.add(null);
                }
                resourceDatas.add(rowData);

                rowData = new ArrayList<String>();
                value1 = getAverageData(host, "Network I/O", avgDataCmp);
                value2 = getAverageData(host, "Network I/O", avgData);
                rowData.add("Network I/O");
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "").replace(",", "")) - Double.valueOf(String.valueOf(value2).replace("%", "").replace(",", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", "").replace(",", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                } else {
                    rowData.add(null);
                    rowData.add(null);
                }
                resourceDatas.add(rowData);

                hostDatas.put(host, resourceDatas);
            }
        }
        
        
        //put cacti monitor data into comparison data map
        if (ifOtherMonitor()) {
            Map<String, List<List>> hostDatas4Cacti = getAverageDatas4Cacti(cmpBuildId);
            hostDatas.putAll(hostDatas4Cacti);
        }
        
        if(hostDatas.size()==0){
            hostDatas = null;
        }        

        return hostDatas;
    }

    private Map<String, List<List>> getAverageDatas4Cacti(String cmpBuildId) throws IOException {
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        
        Map<String, List<List>> hostDatas = new HashMap<String, List<List>>();

        AbstractBuild<?, ?> cmpBuild = getBuildByBuildId(cmpBuildId);
        ObjectMapper mapper = new ObjectMapper();
        Map cmpCactiMonitoredDatas = mapper.readValue(new File(cmpBuild.getRootDir().getAbsoluteFile() + "/performance-reports/monitoring/resources.json"), Map.class);

        for (Map.Entry<String, Object> entry : cactiMonitoredDatas.entrySet()) {
            List<List> resourceDatas = new ArrayList<List>();

            String host = entry.getKey();
            Map<String, String> values = (Map<String, String>) entry.getValue();
            for (Map.Entry<String, String> entry1 : values.entrySet()) {
                String type = entry1.getKey();
                String value = String.valueOf(entry1.getValue());

                List rowData = new ArrayList<String>();
                String value1 = value;
                String value2 = ((Map) cmpCactiMonitoredDatas.get(host)).get(type).toString();
              
                rowData.add(type);
                rowData.add(value1);
                rowData.add(value2);
                if (value1 != null && value2 != null) {
                    double subValue = Double.valueOf(String.valueOf(value1).replace("%", "").replace(",", "")) - Double.valueOf(String.valueOf(value2).replace("%", "").replace(",", ""));
                    double rate = subValue / Double.valueOf(String.valueOf(value1).replace("%", "").replace(",", ""));
                    rowData.add(numberFormat.format(subValue));
                    rowData.add(percentFormat.format(rate));
                }


                resourceDatas.add(rowData);
            }
            hostDatas.put(host, resourceDatas);
        }

        return hostDatas;
    }

    public List<String> getCactiMonitoredTypes() throws IOException {;
        List<String> datarow = new ArrayList<String>();

        if (cactiMonitoredDatas == null) {
            ObjectMapper mapper = new ObjectMapper();
            cactiMonitoredDatas = mapper.readValue(new File(getBuild().getRootDir().getAbsoluteFile() + "/performance-reports/monitoring/resources.json"), Map.class);
        }

        for (Map.Entry<String, Object> entry : cactiMonitoredDatas.entrySet()) {
            String host = entry.getKey();
            Map<String, String> values = (Map<String, String>) entry.getValue();
            for (Map.Entry<String, String> entry1 : values.entrySet()) {
                String type = entry1.getKey();
                if (!datarow.contains(type)) {
                    datarow.add(type);
                }
            }
        }

        return datarow;
    }

    public Map<String, Object> getCactiMonitoredDatas() throws IOException {

        return cactiMonitoredDatas;
    }

    public boolean ifOtherMonitor() {
        String path = getBuild().getRootDir().getAbsoluteFile() + "/performance-reports/monitoring/resources.json";

        return new File(path).exists();
    }

    public boolean ifNmonMonitor() {
        String path = getBuild().getRootDir().getAbsoluteFile() + "/performance-reports/monitoring/resource_nmon.json";

        return new File(path).exists();
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
    public Object getAverageData(String hostname, String type) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
        if (this.avgData == null) {
            this.avgData = getAverageMonitoredData(getBuild());
        }
        return getAverageData(hostname, type, this.avgData);
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

//            Object networkWrite = ((Map) ((Map) avgData.get(hostname)).get(type)).get("eth0-write-KB/s");
//            if (networkWrite == null){
//                return null;
//            }
//            double write = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("eth0-write-KB/s");
//            double read = (Double) ((Map) ((Map) avgData.get(hostname)).get(type)).get("eth0-read-KB/s");
//            return numberFormat.format(write + read);
        }

        return 0;
    }

    /**
     * parse all host name from all nmon files
     *
     * @return
     */
    public List<String> getMonitoredHosts() throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {
        List<String> listHosts = new ArrayList<String>();
//        File rootpath = getBuild().getRootDir();
//        File hosts = new File(rootpath.getAbsoluteFile() + "/performance-reports/monitoring/");
//        File[] nmonFiles = hosts.listFiles();
//        if (nmonFiles == null || nmonFiles.length == 0) {
//            return null;
//        }
//
//        for (File host : hosts.listFiles()) {
//            String filename = host.getName();
//            if (filename.toLowerCase().endsWith(".nmon")) {
//                listHosts.add(filename.substring(0, filename.length() - 17));
//                System.out.println("hostname: " + filename.substring(0, filename.length() - 17));
//            }
//        }        
        
        if(avgData == null){
            avgData = getAverageMonitoredData(getBuild());
        }

        for (Object host : avgData.keySet()) {
            listHosts.add(String.valueOf(host));
        }

        return listHosts;
    }

    /**
     * load all system resources
     *
     * @param request
     * @param response
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     */
    private void doSystemResourceGraph(StaplerRequest request,
            StaplerResponse response, String resource) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        if(avgData == null){
            avgData = getAverageMonitoredData(getBuild());
        }

        if (avgData == null) {
            return;
        }

        String host = request.getParameter("hostname");

        File rootpath = getBuild().getRootDir();
        File img = new File(rootpath.getAbsoluteFile() + "/performance-reports/monitoring/" + host + "/" + resource + ".png");

        //if still do not export picture
        if (!img.exists()) {
            ServletOutputStream os = response.getOutputStream();
            os.print("waiting...");
            os.close();
            return;
        }

        BufferedImage image = ImageIO.read(img);
        response.setContentType("image/png");
        ServletOutputStream os = response.getOutputStream();
        ImageIO.write(image, "PNG", os);
        os.close();
    }

    public void doCpuUtilizationGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "CPU Utilization");
    }

    public void doRunQueueGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "Run Queue");
    }

    public void doMemoryGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "Memory Usage");
    }

    public void doPagingAmountGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "Amount Paged to File System");
    }

    public void doDiskBusyGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "Disk Percent Busy");
    }

    public void doNetworkReadsGraph(StaplerRequest request,
            StaplerResponse response) throws IOException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        doSystemResourceGraph(request, response, "Total Ethernet Read and Write");
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
    private Map getAverageMonitoredData(AbstractBuild<?, ?> build) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException, IOException {

        ObjectMapper mapper = new ObjectMapper();
        String path = build.getRootDir().getAbsoluteFile() + "/performance-reports/monitoring";
        if (ifOtherMonitor()) {
            path = path + "/resources.json";
        }else if (ifNmonMonitor()) {
            path = path + "/resource_nmon.json";
        }
        
        Map<String, Object> avgData = mapper.readValue(new File(path), Map.class);

        return avgData;
    }
}
