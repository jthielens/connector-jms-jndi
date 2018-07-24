package com.cleo.labs.connector.jmsjndi;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.DELETE;
import static com.cleo.connector.api.command.ConnectorCommandName.DIR;
import static com.cleo.connector.api.command.ConnectorCommandName.GET;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.naming.NamingException;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.command.DirCommand;
import com.cleo.connector.api.command.GetCommand;
import com.cleo.connector.api.command.OtherCommand;
import com.cleo.connector.api.command.PutCommand;
import com.cleo.connector.api.directory.Directory.Type;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.helper.Attributes;
import com.cleo.connector.api.interfaces.IConnectorIncoming;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.google.common.base.Strings;

public class JmsJndiConnectorClient extends ConnectorClient {
    private JmsJndiConnectorConfig config;
    private JMSQueue queue = null;

    /**
     * Constructs a new {@code OracleAQConnectorClient} for the schema using
     * the default config wrapper and LexFileFactory.
     * 
     * @param schema the {@code OracleAQConnectorSchema}
     */
    public JmsJndiConnectorClient(JmsJndiConnectorSchema schema) {
        this.config = new JmsJndiConnectorConfig(this, schema);
    }

    /**
     * Opens the queue based on the configuration
     * @throws NamingException 
     * @throws JMSException 
     * @throws ConnectorException 
     * 
     */
    private synchronized void setup() throws JMSException, NamingException, ConnectorException {
        if (queue == null) {
            if (Strings.isNullOrEmpty(config.getQueueName())) {
                throw new ConnectorException("queueName must be specified");
            }
            queue = new JMSQueue(config, logger);
        }
    }

    @Command(name = DIR)
    public ConnectorCommandResult dir(DirCommand dir) throws JMSException, NamingException, ConnectorException {
        String source = dir.getSource().getPath();

        logger.debug(String.format("DIR '%s'", source));
        setup();

        List<Entry> list = new ArrayList<>();
        List<BytesMessage> messages = queue.list();
        for (BytesMessage message : messages) {
            Entry e = new Entry(Type.file);
            e.setDate(Attributes.toLocalDateTime(message.getJMSTimestamp()));
            e.setSize(message.getBodyLength());
            e.setPath(message.getJMSMessageID());
            list.add(e);
        }
        return new ConnectorCommandResult(Status.Success, Optional.empty(), list);
    }

    @Command(name = GET, options = { Delete })
    public ConnectorCommandResult get(GetCommand get) throws JMSException, NamingException, ConnectorException {
        String source = get.getSource().getPath();
        IConnectorIncoming destination = get.getDestination();

        logger.debug(String.format("GET remote '%s' to local '%s'", source, destination.getPath()));
        setup();

        try {
            transfer(queue.new JMSInputStream(source), destination.getStream(), true);
        } catch (IOException | JMSException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                      ConnectorException.Category.fileNonExistentOrNoAccess);
        }
        return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
    }

    @Command(name = PUT, options = { Delete })
    public ConnectorCommandResult put(PutCommand put) throws JMSException, NamingException, IOException, ConnectorException {
        setup();
        String destination = put.getDestination().getPath();
        IConnectorOutgoing source = put.getSource();

        logger.debug(String.format("PUT local '%s' to remote '%s'", source.getPath(), destination));

        try {
            transfer(put.getSource().getStream(), queue.new JMSOutputStream(destination), false);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (JMSException e) {
            throw new IOException(e);
        }
    }

    /**
     * Gets the attribute view for a messageID
     * @param source the messageID
     * @return the attribute view
     * @throws ConnectorException
     * @throws JMSException
     * @throws NamingException
     */
    @Command(name = ATTR)
    public BasicFileAttributeView getAttributes(String source) throws ConnectorException, JMSException, NamingException {
        logger.debug(String.format("ATTR '%s'", source));
        setup();

        try {
            BytesMessage message = queue.peek(source);
            if (message != null) {
                return new JmsJndiMessageAttributes(logger, queue.peek(source));
            }
        } catch (JMSException e) {
            throw new ConnectorException(String.format("error getting attributes for '%s'", source), e);
        }
        throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                ConnectorException.Category.fileNonExistentOrNoAccess);
   }

    /**
     * Deletes a message from the queue
     * @param delete contains the message ID as the source
     * @return the connector command result
     * @throws JMSException
     * @throws NamingException
     * @throws IOException
     * @throws ConnectorException
     */
    @Command(name = DELETE)
    public ConnectorCommandResult delete(OtherCommand delete) throws JMSException, NamingException, IOException, ConnectorException {
        String source = delete.getSource();
        logger.debug(String.format("DELETE '%s'", source));
        setup();

        try {
            queue.delete(source);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        } catch (JMSException e) {
            throw new ConnectorException(String.format("'%s' does not exist or is not accessible", source),
                    ConnectorException.Category.fileNonExistentOrNoAccess);
        }
    }

}
