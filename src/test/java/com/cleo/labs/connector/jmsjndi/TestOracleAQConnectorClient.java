package com.cleo.labs.connector.jmsjndi;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.api.directory.Entry;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.connector.jmsjndi.JmsJndiConnectorClient;
import com.cleo.labs.connector.jmsjndi.JmsJndiConnectorSchema;
import com.cleo.labs.connector.testing.Commands;
import com.cleo.labs.connector.testing.StringCollector;
import com.cleo.labs.connector.testing.StringSource;
import com.cleo.labs.connector.testing.TestConnector;
import com.cleo.labs.connector.testing.TestConnectorHost;

public class TestOracleAQConnectorClient {

    private static JmsJndiConnectorClient setupClient() {
        JmsJndiConnectorSchema aqSchema = new JmsJndiConnectorSchema();
        aqSchema.setup();
        TestConnector connector = new TestConnector(System.err)
                .set("JNDIConnectionURI", "tcp://192.168.50.1:61616")
                .set("JNDIInitialContextFactory", "org.apache.activemq.jndi.ActiveMQInitialContextFactory")
                .set("QueueName", "qone")
                .set("FilenameProperty", "filename")
            //  .set("URIParameters", "[{'enabled':true,'parameter':'queue.qone','value':'qone'}]")
            //  .set("URIParameters", "[{'enabled':true,'parameter':'jndiInitialContextFactory','value':'org.apache.activemq.jndi.ActiveMQInitialContextFactory'},"+
            //                         "{'enabled':true,'parameter':'jndiURL','value':'tcp://192.168.50.1:61616'}]")
                .set(CommonProperty.EnableDebug.name(), Boolean.TRUE.toString());
        JmsJndiConnectorClient client = new JmsJndiConnectorClient(aqSchema);
        IConnectorHost connectorHost = new TestConnectorHost(client);
        client.setup(connector, aqSchema, connectorHost);

        return client;
    }

    @Test
    public void testDir() throws Exception {
        JmsJndiConnectorClient client = setupClient();
        ConnectorCommandResult result;

        // do a dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        for (Entry e : result.getDirEntries().orElse(Collections.emptyList())) {
            System.out.println("queue.dir(): "+e);
            //assertEquals(e.isDir(), Commands.attr(e.getPath()).go(container).readAttributes().isDirectory());
        }
    }

    @Test
    public void testPut() throws Exception {
        JmsJndiConnectorClient client = setupClient();
        ConnectorCommandResult result;

        // do a put
        StringSource source = new StringSource("sample", StringSource.lorem);
        result = Commands.put(source, "sample").go(client);
        assertEquals(Status.Success, result.getStatus());
        // now do a dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        for (Entry e : result.getDirEntries().orElse(Collections.emptyList())) {
            System.out.println("queue.dir(): "+e);
        }
    }

    @Test
    public void testGet() throws Exception {
        JmsJndiConnectorClient client = setupClient();
        StringCollector destination = new StringCollector().name("sample");
        ConnectorCommandResult result;

        // do a dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        List<Entry> entries = result.getDirEntries().orElse(Collections.emptyList());
        assertFalse(entries.isEmpty());
        Entry first = entries.get(0);
        int n = entries.size();
        // now go get the first one
        result = Commands.get(first.getPath(), destination).go(client);
        assertEquals(Status.Success, result.getStatus());
        System.out.println("content found was "+destination.toString());
        // now do a dir again
        result = Commands.dir("").go(client);
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(Status.Success, result.getStatus());
        assertEquals(n, entries.size());
        for (Entry e : entries) {
            System.out.println("after get: "+e);
        }
    }

    @Test
    public void testDelete() throws Exception {
        JmsJndiConnectorClient client = setupClient();
        ConnectorCommandResult result;

        // do a dir
        result = Commands.dir("").go(client);
        assertEquals(Status.Success, result.getStatus());
        List<Entry> entries = result.getDirEntries().orElse(Collections.emptyList());
        assertFalse(entries.isEmpty());
        Entry first = entries.get(0);
        int n = entries.size();
        // now go delete the first one
        result = Commands.delete(first.getPath()).go(client);
        assertEquals(Status.Success, result.getStatus());
        // now do a dir again
        result = Commands.dir("").go(client);
        entries = result.getDirEntries().orElse(Collections.emptyList());
        assertEquals(Status.Success, result.getStatus());
        assertEquals(n-1, entries.size());
        for (Entry e : entries) {
            System.out.println("after get: "+e);
        }
    }
}
