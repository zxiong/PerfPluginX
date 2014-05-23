/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance.monitoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author zxiong
 */
public class ZabbixMonitorUtil {

    private String auth = null;
    private String url = "http://10.66.141.213/zabbix/api_jsonrpc.php";
    private String user;
    private String password;

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub
        ZabbixMonitorUtil tz = new ZabbixMonitorUtil(
                "http://10.66.141.213/zabbix/api_jsonrpc.php", "Admin",
                "zabbix");
        tz.login();
        // String hostid = tz.getHostId("10.66.137.178", null);

        // disable
        // tz.setHostStatus(String.valueOf(hostid), "1");
        // enable
        // tz.setHostStatus(String.valueOf(hostid), "0");

        // String templateId = tz.getTemplate("Template OS Linux");

        // String hostGroup = tz.getHostgroup("Linux servers");

        // tz.createHost("10.66.137.179", "Linux servers","Template OS Linux");

        // tz.removeHost("10.66.137.179");

        long start = Calendar.getInstance().getTimeInMillis() / 1000 - 3600;
        long end = start + 3600;

        String item = tz.getHistory("10.66.141.213", "system.cpu.util[,user]",
                start + "", end + "");

        // String item = tz.getItem("localhost", "system.cpu.util[,user]");
        // String item = tz.getAllItems(null, "localhost");

        System.out.println(item);
    }

    public ZabbixMonitorUtil(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * get host id by host name or IP addr
     *
     * @param host
     * @return
     * @throws JSONException
     */
    public String getHostId(String host) throws JSONException {
        JSONObject filterCondition = new JSONObject();
        if (isIp(host)) {
            filterCondition.putOpt("ip", host);
        } else {
            filterCondition.putOpt("host", host);
        }

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "host.get");
        getHostReqObj.put("id", "1");
        getHostReqObj.put("params",
                (new JSONObject().put("filter", filterCondition)));

        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("hostid");
                }
            }
        }
        return null;
    }

    /**
     * disable enable host 1 --- disable, 0---- enable
     *
     * @param host
     * @param status
     * @return
     * @throws JSONException
     */
    public String setHostStatus(String host, String status)
            throws JSONException {
        String hostId = getHostId(host);
        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "host.update");
        getHostReqObj.put("id", status);
        getHostReqObj.put("params",
                (new JSONObject().put("hostid", hostId).put("status", status)));

        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("hostid");
                }
            }
        }
        return null;
    }

    /**
     * get template
     *
     * @param templateName
     * @return
     * @throws JSONException
     */
    public String getTemplate(String templateName) throws JSONException {
        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "template.get");
        getHostReqObj.put("params", (new JSONObject().put("filter",
                (new JSONObject()).putOpt("name", templateName)).put("output",
                "extend")));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("templateid");
                }
            }
        }
        return null;
    }

    /**
     *
     * @param hostgroupName
     * @return
     * @throws JSONException
     */
    public String getHostgroup(String hostgroupName) throws JSONException {
        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "hostgroup.get");
        getHostReqObj.put("params", (new JSONObject().put("filter",
                (new JSONObject()).putOpt("name", hostgroupName)).put("output",
                "extend")));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("groupid");
                }
            }
        }
        return null;
    }

    /**
     * create a host in zabbix server
     *
     * @param hostIp
     * @param hostgroupName
     * @param templateName
     * @return
     * @throws JSONException
     */
    public String createHost(String hostIp, String hostgroupName,
            String templateName) throws JSONException {
        String hostgroupId = getHostgroup(hostgroupName);
        String templateId = getTemplate(templateName);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "host.create");
        getHostReqObj.put(
                "params",
                (new JSONObject()
                .put("interfaces",
                (new JSONObject()).putOpt("type", "1")
                .put("main", "1").put("useip", "1")
                .put("ip", hostIp).put("dns", "")
                .put("port", "10050"))
                .put("host", hostIp)
                .put("groups",
                (new JSONObject()).put("groupid", hostgroupId))
                .put("templates", (new JSONObject()).put("templateid",
                templateId))));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("hostids");
                }
            }
        }
        return null;
    }

    /**
     * remove a host in zabbix
     *
     * @param host
     * @return
     * @throws JSONException
     */
    public String removeHost(String host) throws JSONException {
        String hostId = getHostId(host);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "host.delete");
        getHostReqObj
                .put("params", (new JSONObject()).putOpt("hostid", hostId));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("hostids");
                }
            }
        }
        return null;
    }

    /**
     * get item id
     *
     * @param hostName
     * @param itemKeys
     * @return
     * @throws JSONException
     */
    public String getItem(String host, String itemKey) throws JSONException {
        String hostId = getHostId(host);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "item.get");
        getHostReqObj.put(
                "params",
                (new JSONObject()).put("hostids", hostId)
                .put("output", "itemids")
                .put("search", new JSONObject().put("key_", itemKey)));

        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                    return hostObj.getString("itemid");
                }
            }
        }
        return null;
    }

    /**
     * retrieve all items of a host
     *
     * @param host
     * @return
     * @throws JSONException
     */
    public String getAllItems(String host) throws JSONException {
        String hostId = getHostId(host);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "item.get");
        getHostReqObj.put("params", (new JSONObject()).put("hostids", hostId)
                .put("output", "extend"));

        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;
                
                if (hostsArray.length() == 1) {
                    JSONObject hostObj = (JSONObject) hostsArray.get(0);
                    return hostObj.getString("key_");
                }
            }
        }
        return null;
    }

    /**
     * get average data of hosts
     *
     * @param host
     * @param itemKeys
     * @param startTime
     * @param endTime
     * @param allAvgDatas
     * @return
     * @throws JSONException
     */
    public Map<String, Map<String, Float>> getAverageMonitoredData(
            List<String> hosts, List<String> itemKeys, String startTime,
            String endTime)
            throws JSONException {

        Map<String, Map<String, Float>> allAvgDatas = new HashMap<String, Map<String, Float>>();
        for (String host : hosts) {
            getAverageMonitoredData(host, itemKeys, startTime, endTime,
                    allAvgDatas);
        }
        return allAvgDatas;
    }

    /**
     * get average data of a host
     *
     * @param host
     * @param itemKeys
     * @param startTime
     * @param endTime
     * @param allAvgDatas
     * @return
     * @throws JSONException
     */
    public Map<String, Map<String, Float>> getAverageMonitoredData(String host,
            List<String> itemKeys, String startTime, String endTime,
            Map<String, Map<String, Float>> allAvgDatas) throws JSONException {

        Map<String, Float> data = null;
        if (allAvgDatas.containsKey(host)) {
            data = allAvgDatas.get(host);
        } else {
            data = new HashMap<String, Float>();
        }

        for (String itemKey : itemKeys) {
            String value = getHistory(host, itemKey, startTime, endTime);
            data.put(itemKey.replace(",", "-"), Float.parseFloat(value));
        }
        allAvgDatas.put(host, data);

        return allAvgDatas;
    }

    /**
     *
     * @param hostIp
     * @param hostName
     * @param startTime
     * @param endTime
     * @return
     * @throws JSONException
     */
    public String getHistory(String host, String itemKey, String startTime,
            String endTime) throws JSONException {

        String lastClock = getLastClock(host, itemKey);
        startTime = Long.parseLong(lastClock)
                - (Long.parseLong(endTime) - Long.parseLong(startTime)) + "";
        endTime = lastClock;

        String itemId = getItem(host, itemKey);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "history.get");
        getHostReqObj.put("params",
                (new JSONObject()).put("history", 0).put("output", "extend")
                .put("itemids", itemId).put("time_from", startTime)
                .put("time_till", endTime));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;

                double avgVal = 0;
                for (int i = 0; i < hostsArray.length(); i++) {
                    JSONObject hostObj = (JSONObject) hostsArray.opt(i);
                    avgVal += hostObj.getDouble("value");
                }
                avgVal = avgVal / hostsArray.length();
                return String.valueOf(avgVal);

            }
        }
        return null;
    }

    /**
     * get lost lock time on zabbix server
     *
     * @param host
     * @param itemKey
     * @return
     */
    private String getLastClock(String host, String itemKey) {
        String itemId = getItem(host, itemKey);

        JSONObject getHostReqObj = new JSONObject();
        getHostReqObj.put("jsonrpc", "2.0");
        getHostReqObj.put("method", "history.get");
        getHostReqObj.put(
                "params",
                (new JSONObject()).put("history", 0).put("output", "extend")
                .put("itemids", itemId).put("sortfield", "clock")
                .put("sortorder", "DESC").put("limit", "1"));
        if (auth == null) {
            login();
        }

        getHostReqObj.put("auth", auth);
        getHostReqObj.put("id", "1");

        JSONObject resObj = post(getHostReqObj);
        Object value = resObj.opt("result");

        if (value != null) {
            if (value instanceof JSONArray) {
                JSONArray hostsArray = (JSONArray) value;
                JSONObject hostObj = (JSONObject) hostsArray.opt(0);
                return hostObj.getString("clock");
            }
        }
        return null;
    }

    /**
     * Login zabbix server
     */
    public void login() {
        try {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("jsonrpc", "2.0");
            jsonObj.put("method", "user.authenticate");
            jsonObj.put("params", (new JSONObject().put("user", this.user).put(
                    "password", this.password)));
            jsonObj.put("id", 0);

            JSONObject reponse = post(jsonObj);
            auth = (String) reponse.get("result");

        } catch (JSONException e) {
            System.out
                    .println("Error occurred in authenticateUser method. Exception: "
                    + e.getMessage());
        }
    }

    /**
     * log off
     */
    public void logout() {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("jsonrpc", "2.0");
        jsonObj.put("method", "user.logout");
        jsonObj.put("params", "");
        jsonObj.put("id", auth);

        post(jsonObj);
    }

    /**
     * HTTP Post
     *
     * @param json
     * @return
     */
    private JSONObject post(JSONObject json) {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(this.url);
        JSONObject response = null;
        try {
            StringEntity s = new StringEntity(json.toString());
            s.setContentEncoding("UTF-8");
            s.setContentType("application/json");
            post.setEntity(s);

            HttpResponse res = client.execute(post);
            if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = res.getEntity();
                InputStreamReader is = new InputStreamReader(
                        entity.getContent());
                response = new JSONObject(new JSONTokener(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    /**
     * for test
     *
     * @param inputStream
     * @return
     */
    private String ConvertToString(InputStream inputStream) {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        StringBuilder result = new StringBuilder();
        String line = null;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStreamReader.close();
                inputStream.close();
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    /**
     * judge if it is IP
     *
     * @param IP
     * @return
     */
    public static boolean isIp(String IP) {
        if (IP == null || "".equals(IP)) {
            return false;
        }

        IP = IP.trim();
        boolean b = false;
        if (IP.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            String s[] = IP.split("\\.");
            if (Integer.parseInt(s[0]) < 255) {
                if (Integer.parseInt(s[1]) < 255) {
                    if (Integer.parseInt(s[2]) < 255) {
                        if (Integer.parseInt(s[3]) < 255) {
                            b = true;
                        }
                    }
                }
            }
        }
        return b;
    }
}