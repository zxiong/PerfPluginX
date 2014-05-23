/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.performance.monitoring;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author zxiong
 */
public class MapParameterValue extends ParameterValue{
    private Map data = null;

    public MapParameterValue(String name) {
        super(name);
    }
    
    public Map getData(){
        if (data == null){
            data = new HashMap();
        }
        return data;
    }
    
    public void clear(){
        data.clear();
        data = null;
    }
}
