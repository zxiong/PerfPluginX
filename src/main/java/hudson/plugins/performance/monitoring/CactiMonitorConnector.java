/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance.monitoring;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author zxiong
 */
public class CactiMonitorConnector {
	private String url;
	private String user;
	private String password;
	private String baseUrl = "";

	private CloseableHttpClient httpclient = null;
	private Map<String, String> hostIDs = null;

	public static String monitor_enable = "monitor_enable";
	public static String monitor_disable = "monitor_disable";

	private long subTime = 0;

	public CactiMonitorConnector(String url, String user, String password) {
		this.url = url;
		this.user = user;
		this.password = password;
		this.baseUrl = url.substring(0, url.lastIndexOf('/'));
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		String server1 = "10.66.10.193";

		try {
			CactiMonitorConnector cacti = new CactiMonitorConnector(
					"http://10.66.10.223/cacti/index.php", "admin", "cacti");
			cacti.disableHost(server1);
			List <String>servers = new ArrayList<String>();
			servers.add("10.66.10.193");
			servers.add("127.0.0.1");
			cacti.login();

			long currentTime = Calendar.getInstance().getTimeInMillis() - 100000;
			String startTime = cacti.conver2MonitorServerTime(currentTime - 3600000);
			String endTime = cacti.conver2MonitorServerTime(currentTime);

			Map<String, Map<String, Float>> allAvgDatas = cacti.getAverageMonitoredData(servers,startTime,endTime);
			
			Iterator<String> its = allAvgDatas.keySet().iterator();
			while(its.hasNext()){
				String server =its.next();
				System.out.println(server);
				
				Map<String, Float> avgDatas = allAvgDatas.get(server);
			    Iterator<String> it = avgDatas.keySet().iterator();
			    while(it.hasNext()){
			    	String title = it.next();
			    	System.out.println(title + "====" + avgDatas.get(title));
			    }
			}
			
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

	}

	public Map<String, Map<String,Float>> getAverageMonitoredData(List<String> servers,String startTime,String endTime) throws ClientProtocolException, IOException,
			ParseException {
		Map<String, Map<String,Float>> allAvgDatas = new HashMap<String, Map<String,Float>>();
		
		login();
		for (String server : servers) {
			Map<String, Float> avgDatas = new HashMap<String, Float>();
			enableHost(server);
			login();
			HashMap<String, String> graphMap = getGraphIds(getHostIDs().get(
					server));

			login();
			Iterator<String> it = graphMap.keySet().iterator();
			while (it.hasNext()) {
				String title = it.next();
				String gid = graphMap.get(title);
				float value = getAverage(gid, startTime, endTime);
				
				avgDatas.put(title, value);
			}
			allAvgDatas.put(server, avgDatas);

		}

		return allAvgDatas;

	}

	/**
	 * login
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public boolean login() throws ClientProtocolException, IOException {
		RequestConfig globalConfig = RequestConfig.custom()
	            .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY)
	            .build();
		LaxRedirectStrategy redirectStrategy = new LaxRedirectStrategy();
		httpclient = HttpClients.custom().setRedirectStrategy(redirectStrategy).setDefaultRequestConfig(globalConfig)
				.build();		

		// Post Request Example
		ArrayList<NameValuePair> pairList = new ArrayList<NameValuePair>();
		pairList.add(new BasicNameValuePair("action", "login"));
		pairList.add(new BasicNameValuePair("login_username", user));
		pairList.add(new BasicNameValuePair("login_password", password));
		UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(pairList,
				"UTF-8");

		HttpPost httpPost = new HttpPost(baseUrl + "/index.php");
		httpPost.setEntity(formEntity);
		HttpResponse response = httpclient.execute(httpPost);

		int status = response.getStatusLine().getStatusCode();
		if (status == 302 || status == 200) {
			getHostIds();
			System.out.println(hostIDs);
			return true;
		}

		return false;
	}

	/**
	 * get hosts
	 * 
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private void getHostIds() throws ClientProtocolException, IOException {
		hostIDs = new HashMap<String, String>();
		HttpGet httpGet = new HttpGet(baseUrl + "/host.php");
		HttpResponse response = httpclient.execute(httpGet);
		String responseString = EntityUtils.toString(response.getEntity());

		// Pattern p = Pattern.compile("id=([0-9]*)'>([^<]*)<");
		// Matcher m = p.matcher(responseString);
		// while (m.find()) {
		// hostIDs.put(m.group(2), m.group(1));
		// }

		Pattern p = Pattern
				.compile("<td onClick='select_line\\(\"([0-9]*)\"\\)'>([^<]*)</td>");
		Matcher m = p.matcher(responseString);

		int i = 0;
		while (m.find()) {
			if (i == 4)
				hostIDs.put(m.group(2), m.group(1));

			i++;
			i = i % 8;
		}
	}

	/**
	 * 
	 * @param hostId
	 * @param action
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public boolean monitor_enable(String hostName)
			throws ClientProtocolException, IOException {
		return action(hostName, "monitor_enable");
	}

	/**
	 * 
	 * @param hostId
	 * @param action
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public boolean monitor_disable(String hostName)
			throws ClientProtocolException, IOException {
		return action(hostName, "monitor_disable");
	}

	public boolean enableHost(String hostName) throws ClientProtocolException,
			IOException {
		return action(hostName, "2");
	}

	public boolean disableHost(String hostName) throws ClientProtocolException,
			IOException {
		return action(hostName, "3");
	}

	/**
	 * 
	 * @param hostId
	 * @param action
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public boolean action(String hostName, String action)
			throws ClientProtocolException, IOException {
		String hostId = hostIDs.get(hostName);

		ArrayList<NameValuePair> pairList = new ArrayList<NameValuePair>();
		pairList.add(new BasicNameValuePair("action", "actions"));
		pairList.add(new BasicNameValuePair("drp_action", action));
		pairList.add(new BasicNameValuePair("selected_items", "a:1:{i:0;s:2:\""
				+ hostId + "\";}"));
		UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(pairList,
				"UTF-8");

		HttpPost httpPost = new HttpPost(baseUrl + "/host.php");
		httpPost.setEntity(formEntity);
		HttpResponse response = httpclient.execute(httpPost);

		int status = response.getStatusLine().getStatusCode();
		if (status == 302 || status == 200)
			return true;

		return false;
	}

	public HashMap<String, String> getGraphIds(String hostId)
			throws ClientProtocolException, IOException {
		String url = baseUrl + "/graphs.php?host_id=" + hostId
				+ "&graph_rows=5000&filter=&template_id=-1";
		System.out.println(url);
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response = httpclient.execute(httpGet);
		String responseString = EntityUtils.toString(response.getEntity());

		Pattern p = Pattern.compile("id=([0-9]*)' title='([^']*)'");
		Matcher m = p.matcher(responseString);

		HashMap<String, String> graphIds = new HashMap<String, String>();

		while (m.find()) {
			String title = m.group(2).substring(m.group(2).indexOf('-') + 1);
			graphIds.put(title.trim(), m.group(1));
		}

		return graphIds;

	}

	public float getAverage(String graphId, String startTime, String endTime)
			throws ClientProtocolException, IOException {
		String url = baseUrl + "/graph_xport.php?local_graph_id=" + graphId
				+ "&rra_id=5&view_type=&graph_start=" + startTime
				+ "&graph_end=" + endTime;

		// url =
		// "http://10.66.10.223/cacti/graph_xport.php?local_graph_id=89&rra_id=5&view_type=&graph_start=1390922052&graph_end=1390924761";
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response = httpclient.execute(httpGet);
		String responseString = EntityUtils.toString(response.getEntity());
		String[] rows = responseString.split("\n");

		float sum = 0;
		int count = 0;
		float avg = 0;
		for (int i = 0; i < rows.length; i++) {
			if (i > 9) {
				String row = rows[i].replace("\"", "");
				String[] fields = row.split(",");
				Float value = new Float(fields[1]);
				if (!value.isNaN()) {
					sum += new Float(fields[1]);
					count++;
				}

			}
		}

		if (count > 0)
			avg = sum / count;

		return avg;
	}

	public long getTimeDifference() throws ClientProtocolException,
			IOException, ParseException {
		if (subTime != 0) {
			return subTime;
		}

		String url = baseUrl + "/images/move_right.gif";
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response = httpclient.execute(httpGet);
		Date jenkinsServerTime = Calendar.getInstance().getTime();

		SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		Date monitoringServerTime = f.parse(response.getFirstHeader("Date")
				.getValue());

		subTime = monitoringServerTime.getTime() - jenkinsServerTime.getTime();

		return subTime;
	}

	public String conver2MonitorServerTime(long jenkinsServerTime)
			throws ClientProtocolException, IOException, ParseException {
		long startTime = jenkinsServerTime + getTimeDifference();
		String unixDate = String.valueOf(startTime).substring(0, 10);

		return unixDate;
	}

	public Map<String, String> getHostIDs() {
		return hostIDs;
	}

	public void setHostIDs(Map<String, String> hostIDs) {
		this.hostIDs = hostIDs;
	}

}

