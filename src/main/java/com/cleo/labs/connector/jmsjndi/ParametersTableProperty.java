package com.cleo.labs.connector.jmsjndi;

import java.util.HashMap;
import java.util.Map;

import com.cleo.connector.api.annotations.Array;
import com.cleo.connector.api.annotations.Display;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.PropertyBuilder;
import com.google.common.base.Strings;
import com.google.gson.Gson;

@Array
public class ParametersTableProperty {
    private static final Gson GSON = new Gson();

    /**
     * Display value for URI parameters property table
     * @param value the URI Parameters property value (a JSON array)
     * @return "n Parameters" (or "1 Parameter")
     */
    @Display
    public String display(String value) {
        int size = toParameters(value).size();
        return String.format("%d Parameter%s", size, size==1?"":"s");
    }
  
    @Property
    final IConnectorProperty<Boolean> enabled = new PropertyBuilder<>("Enabled", true)
        .setRequired(true)
        .build();

    @Property
    final public IConnectorProperty<String> header = new PropertyBuilder<>("Header", "")
        .setDescription("Header name")
        .build();

    @Property
    final public IConnectorProperty<String> content = new PropertyBuilder<>("Value", "")
        .setDescription("Header value")
        .build();

    public static class ParameterValue {
        private boolean enabled;
        private String parameter;
        private String value;
        public ParameterValue enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        public boolean enabled() {
            return enabled;
        }
        public ParameterValue parameter(String parameter) {
            this.parameter = parameter;
            return this;
        }
        public String parameter() {
            return parameter;
        }
        public ParameterValue value(String value) {
            this.value = value;
            return this;
        }
        public String value() {
            return value;
        }
        public ParameterValue(boolean enabled, String parameter, String value) {
            this.enabled = enabled;
            this.parameter = parameter;
            this.value = value;
        }
        public ParameterValue() {
            this(false, null, null);
        }
    }

    /**
     * Deserialize the JSON array into a Map<String,String>
     * @param value the JSON array (may be {@code null})
     * @return a (possibly empty but not null) Map<String,String>
     */
    public static Map<String,String> toParameters(String value) {
        ParameterValue[] parameters = Strings.isNullOrEmpty(value) ? new ParameterValue[0] : GSON.fromJson(value, ParameterValue[].class);
        Map<String,String> result = new HashMap<>(parameters.length);
        for (ParameterValue parameter : parameters) {
            if (parameter.enabled()) {
                result.put(parameter.parameter(), parameter.value());
            }
        }
        return result;
    }
}
