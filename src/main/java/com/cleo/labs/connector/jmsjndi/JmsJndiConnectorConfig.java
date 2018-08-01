package com.cleo.labs.connector.jmsjndi;

import java.util.Map;

import com.cleo.connector.api.property.ConnectorPropertyException;

/**
 * A configuration wrapper around a {@link JmsJndiConnectorClient}
 * instance and its {@link JmsJndiConnectorSchema}, exposing bean-like
 * getters for the schema properties converted to their usable forms:
 * <table border="1">
 *   <tr><th>Property</th><th>Stored As</th><th>Returned as</th></tr>
 *   <tr><td>X</td><td>String</td><td>String</td></tr>
 * </table>
 */
public class JmsJndiConnectorConfig {
    private JmsJndiConnectorClient client;
    private JmsJndiConnectorSchema schema;

    /**
     * Constructs a configuration wrapper around a {@link BlobStorageConnectorClient}
     * instance and its {@link BlobStorageConnectorSchema}, exposing bean-like
     * getters for the schema properties converted to their usable forms.
     * @param client the BlobStorageConnectorClient
     * @param schema its BlobStorageConnectorSchema
     */
    public JmsJndiConnectorConfig(JmsJndiConnectorClient client, JmsJndiConnectorSchema schema) {
        this.client = client;
        this.schema = schema;
    }
 
    /**
     * Gets the JNDI Initial Context Factory property.
     * @return the JNDI Initial Context Factory
     * @throws ConnectorPropertyException
     */
    public String getJNDIInitialContextFactory() throws ConnectorPropertyException {
        return schema.jndiInitialContextFactory.getValue(client);
    }

    /**
     * Gets the JNDI Connection Factory Name property.
     * @return the JNDI Connection Factory Name
     * @throws ConnectorPropertyException
     */
    public String getJNDIConnectionFactoryName() throws ConnectorPropertyException {
        return schema.jndiConnectionFactoryName.getValue(client);
    }

    /**
     * Gets the JNDI Connection URI property.
     * @return the JNDI Connection URI
     * @throws ConnectorPropertyException
     */
    public String getJNDIConnectionURI() throws ConnectorPropertyException {
        return schema.jndiConnectionURI.getValue(client);
    }

    /**
     * Gets the Queue Name property.
     * @return the Queue Name
     * @throws ConnectorPropertyException
     */
    public String getQueueName() throws ConnectorPropertyException {
        return schema.queueName.getValue(client);
    }

    /**
     * Gets the URI Parameters property table.
     * @return a Map of all enabled parameter=value pairs.
     * @throws ConnectorPropertyException
     */
    public Map<String,String> getUriParameters() throws ConnectorPropertyException {
        return ParametersTableProperty.toParameters(schema.uriParametersTable.getValue(client));
    }

    /**
     * Gets the Username property.
     * @return the Username
     * @throws ConnectorPropertyException
     */
    public String getUsername() throws ConnectorPropertyException {
        return schema.username.getValue(client);
    }

    /**
     * Gets the Password property.
     * @return the Password
     * @throws ConnectorPropertyException
     */
    public String getPassword() throws ConnectorPropertyException {
        return schema.password.getValue(client);
    }

    /**
     * Gets a computed Connection Cache ID for use as an index for connection caching.
     * @return a Connection Cache ID
     * @throws ConnectorPropertyException
     */
    public String getConnectionCacheID() throws ConnectorPropertyException {
        return getJNDIInitialContextFactory()+";"+
                getJNDIConnectionURI()+";"+
                schema.uriParametersTable.getValue(client)+";"+
                getUsername()+";"+
                getPassword();
    }

    /**
     * Gets the Filename Property property.
     * @return the Filename Property
     * @throws ConnectorPropertyException
     */
    public String getFilenameProperty() throws ConnectorPropertyException {
        return schema.filenameProperty.getValue(client);
    }

    /**
     * Gets the Selector Expression.
     * @return the Selector Expression
     * @throws ConnectorPropertyException
     */
    public String getSelectorExpression() throws ConnectorPropertyException {
        return schema.selectorExpression.getValue(client);
    }

    /**
     * Gets the JMS Properties property table.
     * @return a Map of all enabled property=value pairs.
     * @throws ConnectorPropertyException
     */
    public Map<String,String> getJMSMessageProperties() throws ConnectorPropertyException {
        return PropertiesTableProperty.toProperties(schema.propertiesTable.getValue(client));
    }

    /**
     * Gets the Maximum Message count.
     * @return the Maximum Message count
     * @throws ConnectorPropertyException
     */
    public int getMaximumMessages() throws ConnectorPropertyException {
        return schema.maximumMessages.getValue(client);
    }

}
