/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance;

import hudson.model.ModelObject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.util.ChartUtil;
import java.awt.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.DatasetUtilities;
import org.jfree.ui.TextAnchor;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author zxiong
 */
public class BuildComparison implements ModelObject {

    private AbstractBuild<?, ?> build;
    private AbstractProject<?, ?> project;
    private String cmpbuildid = "";
    private Map<String, List<List<String>>> reportDatas = null;
    private Map<String, List<List>> resourceDatas = null;

    public BuildComparison(final AbstractProject<?, ?> project, final AbstractBuild<?, ?> build, String cmpbuildid,
            final StaplerRequest request, Map<String, List<List<String>>> reportDatas, Map<String, List<List>> resourceDatas) {
        this.build = build;
        this.project = project;
        this.reportDatas = reportDatas;
        this.resourceDatas = resourceDatas;
        this.cmpbuildid = cmpbuildid;
    }

    public List<List<String>> getReportDatas(String reportFile) throws IOException {
        if (reportDatas == null) {
            return null;
        }
        return reportDatas.get(reportFile);
    }

    /**
     * Response time comparison
     * 
     * @param request
     * @param response
     * @throws IOException 
     */
    public void doComparisonGraph(StaplerRequest request,
            StaplerResponse response) throws IOException {
        String reportFile = request.getParameter("reportname");
        List<List<String>> reportData = reportDatas.get(reportFile);

        String columnKeys[] = {getTargetBuildId(),getCurrentBuildId()};
        String[] rowKeys = new String[reportData.size()];
        double[][] datas = new double[2][reportData.size()];

        for (int row = 0; row < reportData.size(); row++) {
            List<String> datarow = reportData.get(row);
            rowKeys[row] = datarow.get(0);

            for (int i = 1; i < datarow.size() - 2; i++) {
                String value = datarow.get(i);
                if (value == null) {
                    datas[i - 1][row] = 0.0;
                } else {
                    datas[i - 1][row] = Double.parseDouble(datarow.get(i));
                }
            }
        }


        CategoryDataset dataset = DatasetUtilities.createCategoryDataset(columnKeys,rowKeys,datas);

        JFreeChart chart = ChartFactory.createBarChart3D("Avg Elapase Time Comparison",
                "Page Name",
                "Elapsed Time(ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);
        CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.pink);
        plot.setRangeGridlinePaint(Color.pink);

        BarRenderer renderer = new BarRenderer();
        renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setBaseItemLabelsVisible(true);

        renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BASELINE_LEFT));
        renderer.setItemLabelAnchorOffset(10D);

        renderer.setItemMargin(0);
        plot.setRenderer(renderer);
        
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        plot.setDomainAxis(domainAxis); 
               
        int width=1200;
        int height = 500;
        
        if (reportData.size()>30){
            plot.setOrientation(PlotOrientation.HORIZONTAL); 
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.STANDARD);
            height = reportData.size() * 20;
        }

        ChartUtil.generateGraph(request, response, chart, width, height);
    }

    /**
     * get all report file names
     * @return
     * @throws IOException 
     */
    public List<String> getReportFiles() throws IOException {
        if (reportDatas == null) {
            return null;
        }

        Iterator<String> it = reportDatas.keySet().iterator();
        List<String> reportFiles = new ArrayList<String>();
        while (it.hasNext()) {
            String reportFile = it.next();
            reportFiles.add(reportFile);
        }

        return reportFiles;
    }

    /**
     * get monitored server name
     * @return
     * @throws IOException 
     */
    public List<String> getServers() throws IOException {
        if (resourceDatas == null) {
            return null;
        }

        List<String> hosts = new ArrayList<String>();
        Iterator<String> it = resourceDatas.keySet().iterator();
        while (it.hasNext()) {
            hosts.add(it.next());
        }

        return hosts;
    }

    public List<List> getResourceData(String host) throws IOException {
        return resourceDatas.get(host);
    }

    /**
     * Resource comparison graph
     * 
     * @param request
     * @param response
     * @throws IOException 
     */
    public void doResourceComprisonGraph(StaplerRequest request,
            StaplerResponse response) throws IOException {
        String host = request.getParameter("host");
        String width = request.getParameter("width");
        String height = request.getParameter("height");
        List<List> reportData = resourceDatas.get(host);

        String columnKeys[] = {getTargetBuildId(),getCurrentBuildId()};
        String[] rowKeys = new String[reportData.size()];
        double[][] datas = new double[2][reportData.size()];

        for (int row = 0; row < reportData.size(); row++) {
            List<String> datarow = reportData.get(row);
            rowKeys[row] = datarow.get(0);

            for (int i = 1; i < datarow.size() - 2; i++) {
                String value = datarow.get(i);
                if (value == null) {
                    datas[i - 1][row] = 0.0;
                } else {
                    datas[i - 1][row] = Double.parseDouble(datarow.get(i).replace("%", ""));
                }
            }
        }


        CategoryDataset dataset = DatasetUtilities.createCategoryDataset(columnKeys,rowKeys,datas);

        JFreeChart chart = ChartFactory.createBarChart3D("Avg System Resource Utilization Comparison",
                "Metic Type",
                "Utilization",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);
        CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.pink);
        plot.setRangeGridlinePaint(Color.pink);

        BarRenderer renderer = new BarRenderer();
        renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setBaseItemLabelsVisible(true);

        renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BASELINE_LEFT));
        renderer.setItemLabelAnchorOffset(10D);

        renderer.setItemMargin(0.0);
        plot.setRenderer(renderer);

        ChartUtil.generateGraph(request, response, chart, Integer.parseInt(width), Integer.parseInt(height));
    }

    public String getDisplayName() {
        return Messages.BuildComparison_DisplayName();
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public String getTargetBuildId() {
        return this.cmpbuildid;
    }
    
    public String getCurrentBuildId(){
        return "#" + getBuild().getNumber() + "    --------    " + getBuild().getId();
    }
}
