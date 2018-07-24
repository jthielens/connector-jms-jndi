package com.cleo.labs.connector.jmsjndi;

import static com.cleo.connector.api.property.CommonPropertyGroups.Connect;
import static com.cleo.connector.api.property.CommonPropertyGroups.ConnectSecurity;

import java.io.IOException;

import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.annotations.Connector;
import com.cleo.connector.api.annotations.Info;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.CommonProperties;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.api.property.PropertyBuilder;
import com.cleo.connector.api.interfaces.IConnectorProperty.Attribute;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

@Connector(scheme = "jmsjndi", description = "JMS through JNDI")
@Client(JmsJndiConnectorClient.class)
public class JmsJndiConnectorSchema extends ConnectorConfig {

    @Property
    final IConnectorProperty<String> jndiInitialContextFactory = new PropertyBuilder<>("JNDIInitialContextFactory", "")
            .setDescription("The JNDI Iniital Context Factory class name.")
            .setGroup(Connect)
            .setRequired(true)
            .build();

    @Property
    final IConnectorProperty<String> jndiConnectionURI = new PropertyBuilder<>("JNDIConnectionURI", "")
            .setDescription("The JNDI connection URI.")
            .setGroup(Connect)
            .setRequired(true)
            .build();

    @Property
    final IConnectorProperty<String> queueName = new PropertyBuilder<>("QueueName", "")
            .setDescription("The name of the queue.")
            .setGroup(Connect)
            .setRequired(false)
            .build();

    @Property
    final public IConnectorProperty<String> uriParametersTable = new PropertyBuilder<>("URIParameters", "")
            .setGroup(Connect)
            .setRequired(false)
            .setAllowedInSetCommand(false)
            .setDescription("A list of URI Parameters.")
            .setExtendedClass(ParametersTableProperty.class)
            .build();

    @Property
    final IConnectorProperty<String> username = new PropertyBuilder<>("Username", "")
            .setDescription("The username for authenticated queues.")
            .setGroup(ConnectSecurity)
            .setRequired(false)
            .build();

    @Property
    final IConnectorProperty<String> password = new PropertyBuilder<>("Password", "")
            .setDescription("The password for authenticated queues.")
            .setGroup(ConnectSecurity)
            .setRequired(false)
            .addAttribute(Attribute.Password)
            .build();

    @Property
    final IConnectorProperty<String> filenameProperty = new PropertyBuilder<>("FilenameProperty", "")
            .setDescription("The message property to use for the filename.")
            .setRequired(false)
            .build();

    @Property
    final IConnectorProperty<String> selectorExpression = new PropertyBuilder<>("SelectorExpression", "")
            .setDescription("An optional JMS selector expression to select a subset of items from the queue.")
            .setRequired(false)
            .build();

    @Property
    final public IConnectorProperty<String> propertiesTable = new PropertyBuilder<>("JMSMessageProperties", "")
            .setRequired(false)
            .setAllowedInSetCommand(false)
            .setDescription("A list of JMS Properties to set when writing messages to the queue.")
            .setExtendedClass(PropertiesTableProperty.class)
            .build();

    @Property
    final IConnectorProperty<Integer> maximumMessages = new PropertyBuilder<>("MaximumMessages", 0)
            .setDescription("The maximum number of messages to return in a single DIR (0 for unlimited).")
            .setRequired(false)
            .build();

    @Property
    final IConnectorProperty<Integer> commandRetries = CommonProperties.of(CommonProperty.CommandRetries);

    @Property
    final IConnectorProperty<Integer> commandRetryDelay = CommonProperties.of(CommonProperty.CommandRetryDelay);

    @Property
    final IConnectorProperty<Boolean> doNotSendZeroLengthFiles = CommonProperties.of(CommonProperty.DoNotSendZeroLengthFiles);

    @Property
    final IConnectorProperty<Boolean> deleteReceivedZeroLengthFiles = CommonProperties.of(CommonProperty.DeleteReceivedZeroLengthFiles);

    @Property
    final IConnectorProperty<String> retrieveDirectorySort = CommonProperties.of(CommonProperty.RetrieveDirectorySort);

    @Property
    final IConnectorProperty<Boolean> enableDebug = CommonProperties.of(CommonProperty.EnableDebug);

    @Info
    protected static String info() throws IOException {
        return Resources.toString(JmsJndiConnectorSchema.class.getResource("info.txt"), Charsets.UTF_8);
    }
}