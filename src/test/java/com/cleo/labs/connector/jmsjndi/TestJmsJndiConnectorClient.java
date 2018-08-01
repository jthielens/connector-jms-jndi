package com.cleo.labs.connector.jmsjndi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.connector.testing.Commands;
import com.cleo.labs.connector.testing.StringCollector;
import com.cleo.labs.connector.testing.StringSource;
import com.cleo.labs.connector.testing.TestConnector;
import com.cleo.labs.connector.testing.TestConnectorAction;
import com.cleo.labs.connector.testing.TestConnectorHost;

public class TestJmsJndiConnectorClient {

    private static Connection connection;
    private static Session session;
    private static Map<String,Destination> queues = new HashMap<>();

    @BeforeClass
    public static void setup() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
        
        // Create a Connection
        connection = connectionFactory.createConnection();
        connection.start();

        // Create a Session
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public static synchronized Destination createQueue(String name) throws JMSException {
        if (queues.containsKey(name)) {
            return queues.get(name);
        } else {
            Destination queue = session.createQueue(name);
            queues.put(name,  queue);
            return queue;
        }
    }

    @AfterClass
    public static void cleanup() throws JMSException {
        for (Destination queue : queues.values()) {
            ((ActiveMQConnection)connection).destroyDestination((ActiveMQDestination)queue);
        }
        session.close();
        connection.close();
    }

    private static JmsJndiConnectorClient setupClient(String queueName) throws JMSException {
        createQueue(queueName);
        JmsJndiConnectorSchema jmsSchema = new JmsJndiConnectorSchema();
        jmsSchema.setup();
        TestConnector connector = new TestConnector(System.err)
            //  .set("JNDIConnectionURI", "tcp://192.168.50.1:61616")
                .set("JNDIConnectionURI", "vm://localhost")
                .set("JNDIInitialContextFactory", "org.apache.activemq.jndi.ActiveMQInitialContextFactory")
                .set("QueueName", queueName)
                .set("FilenameProperty", "filename")
            //  .set("URIParameters", "[{'enabled':true,'parameter':'queue.qone','value':'qone'}]")
            //  .set("URIParameters", "[{'enabled':true,'parameter':'jndiInitialContextFactory','value':'org.apache.activemq.jndi.ActiveMQInitialContextFactory'},"+
            //                         "{'enabled':true,'parameter':'jndiURL','value':'tcp://192.168.50.1:61616'}]")
                .set(CommonProperty.EnableDebug.name(), Boolean.TRUE.toString());
        JmsJndiConnectorClient client = new JmsJndiConnectorClient(jmsSchema);
        IConnectorHost connectorHost = new TestConnectorHost(client);
        client.setup(connector, jmsSchema, connectorHost);

        return client;
    }

    @Test
    public void testRoundTrip() throws Exception {
        JmsJndiConnectorClient client = setupClient("queue.one");
        ConnectorCommandResult result;
        StringSource source = new StringSource("sample", StringSource.lorem);
        StringCollector destination = new StringCollector().name("sample");

        // do a dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertTrue(result.getDirEntries().get().isEmpty());

        // put a message
        result = Commands.put(source, "sample").go(client);
        assertEquals(Status.Success, result.getStatus());

        // another dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertEquals(1, result.getDirEntries().get().size());
        for (Entry e : result.getDirEntries().get()) {
            System.out.println("queue.dir(): "+e);
            //assertEquals(e.isDir(), Commands.attr(e.getPath()).go(container).readAttributes().isDirectory());
        }
        String messageID = result.getDirEntries().get().get(0).getPath();

        // now get the message
        result = Commands.get(messageID, destination).go(client);
        assertEquals(Status.Success, result.getStatus());
        assertEquals(StringSource.lorem, destination.toString());

        // should still be a single message on the queue
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertEquals(1, result.getDirEntries().get().size());

        // now delete it
        result = Commands.delete(messageID).go(client);
        assertEquals(Status.Success, result.getStatus());

        // now queue should be empty again
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertTrue(result.getDirEntries().get().isEmpty());
    }

    @Test
    public void testSelector() throws Exception {
        JmsJndiConnectorClient client = setupClient("queue.two");
        ConnectorCommandResult result;
        StringSource source = new StringSource("sample", StringSource.lorem);

        ((TestConnectorAction)client.getAction())
            //.set("SelectorExpression", "color = 'blue'")
            .set("JMSMessageProperties", "[{'enabled':true,'property':'color','value':'blue'}]");

        // do a put
        result = Commands.put(source, "sample").go(client);
        assertEquals(Status.Success, result.getStatus());

        // now do a dir and the message should be there
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertEquals(1, result.getDirEntries().get().size());

        // switch to filter for red messages
        ((TestConnectorAction)client.getAction())
            .set("SelectorExpression", "color = 'red'");

        // now do a dir and the list should be empty
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        assertTrue(result.getDirEntries().isPresent());
        assertTrue(result.getDirEntries().get().isEmpty());
    }
}
