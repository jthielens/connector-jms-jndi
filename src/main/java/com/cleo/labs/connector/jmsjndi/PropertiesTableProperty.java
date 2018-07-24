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
public class PropertiesTableProperty {
    private static final Gson GSON = new Gson();

    /**
     * Display value for JMS properties property table
     * @param value the JMS Properties property value (a JSON array)
     * @return "n Properties" (or "1 Property")
     */
    @Display
    public String display(String value) {
        int size = toProperties(value).size();
        return String.format("%d Propert%s", size, size==1?"y":"ies");
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

    public static class PropertyValue {
        private boolean enabled;
        private String property;
        private String value;
        public PropertyValue enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        public boolean enabled() {
            return enabled;
        }
        public PropertyValue property(String property) {
            this.property = property;
            return this;
        }
        public String property() {
            return property;
        }
        public PropertyValue value(String value) {
            this.value = value;
            return this;
        }
        public String value() {
            return value;
        }
        public PropertyValue(boolean enabled, String property, String value) {
            this.enabled = enabled;
            this.property = property;
            this.value = value;
        }
        public PropertyValue() {
            this(false, null, null);
        }
    }

    /**
     * Deserialize the JSON array into a Map<String,String>
     * @param value the JSON array (may be {@code null})
     * @return a (possibly empty but not null) Map<String,String>
     */
    public static Map<String,String> toProperties(String value) {
        PropertyValue[] parameters = Strings.isNullOrEmpty(value) ? new PropertyValue[0] : GSON.fromJson(value, PropertyValue[].class);
        Map<String,String> result = new HashMap<>(parameters.length);
        for (PropertyValue parameter : parameters) {
            // ignore disabled properties, or properties with empty name or value
            if (parameter.enabled() && !Strings.isNullOrEmpty(parameter.property()) && !Strings.isNullOrEmpty(parameter.value())) {
                result.put(parameter.property(), parameter.value());
            }
        }
        return result;
    }
}
