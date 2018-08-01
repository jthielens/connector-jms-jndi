package com.cleo.labs.connector.jmsjndi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.cleo.connector.api.helper.Logger;
import com.cleo.connector.api.property.ConnectorPropertyException;
import com.google.common.base.Strings;

public class JMSQueue {
    private Logger logger;
    private JmsJndiConnectorConfig config;
    private String queueName;
    private String connectionCacheID;
    private String destinationCacheID;
    private Connection connection;
    private Destination destination;

    /*------------------------------------------------------------------------------
     * Constructor.
     *----------------------------------------------------------------------------*/
    public JMSQueue(JmsJndiConnectorConfig config, Logger logger) throws JMSException, ConnectorPropertyException, NamingException {
        this.config = config;
        this.queueName = config.getQueueName();
        this.logger = logger;
        this.connectionCacheID = config.getConnectionCacheID()+";"+queueName;  // with the queue-name-in-the-environment hack...
        this.destinationCacheID = connectionCacheID; // +";"+queueName;        // ...need a new connection (and environment) per queue

        logger.debug(String.format("new JMSQueue(%s)", queueName));

        try {
            connect();
        } catch (JMSException e) {
            logger.debug(String.format("new JMSQueue(%s) exception", queueName), e);
            throw e;
        }
    }

    /*------------------------------------------------------------------------------
     * Opens a JMS queue
     *----------------------------------------------------------------------------*/
    private static final Map<String, Connection> connectionCache = new HashMap<>();
    private static final Map<String, Destination> destinationCache = new HashMap<>();
    private final static Object jmsSyncObj = new Object();

    private static final int CONNECT_RETRY_LIMIT = 2;

    private void connect() throws JMSException, ConnectorPropertyException, NamingException {
        Connection connection = null;
        Destination destination = null;

        synchronized (jmsSyncObj) {
            connection = connectionCache.get(connectionCacheID);
            destination = destinationCache.get(destinationCacheID);
            int attempt = 0;
            while (connection == null) {
                attempt++;
                try {
                    // Build InitialContext
                    Hashtable<String, String> environment = new Hashtable<>();
                    environment.putAll(config.getUriParameters());
                    environment.put(Context.INITIAL_CONTEXT_FACTORY, config.getJNDIInitialContextFactory());
                    environment.put(Context.PROVIDER_URL, config.getJNDIConnectionURI());
                    environment.put("queue."+config.getQueueName(), config.getQueueName());

                    Context ctx;
                    if (environment.isEmpty()) {
                        logger.debug("JMSQueue.connect - InitialContext()");
                        ctx = new InitialContext();
                    } else {
                        logger.debug("JMSQueue.connect - InitialContext(");
                        for (Enumeration<String> e = environment.keys(); e.hasMoreElements();) {
                            String key = e.nextElement();
                            logger.debug(String.format("JMSQueue.connect -   environment[%s] = %s",
                                    key,
                                    key.equalsIgnoreCase(Context.SECURITY_CREDENTIALS) ? "******" : environment.get(key)));
                        }
                        logger.debug("JMSQueue.connect - )");
                        ctx = new InitialContext(environment);
                    }

                    // Look up ConnectionFactory
                    String connectionFactoryName = config.getJNDIConnectionFactoryName();
                    logger.debug(String.format("JMSQueue.connect - ctx.lookup(%s)", connectionFactoryName));
                    ConnectionFactory factory = (ConnectionFactory) ctx.lookup(connectionFactoryName);

                    // Create a connection
                    String username = config.getUsername();
                    String password = config.getPassword();
                    if (!Strings.isNullOrEmpty(username) || !Strings.isNullOrEmpty(password)) {
                        logger.debug(String.format("JMSQueue.connect - jmsConnectionFactory.createConnection(%s, ******)",
                                username));
                        connection = factory.createConnection(username, password);
                    } else {
                        logger.debug("JMSQueue.connect - jmsConnectionFactory.createConnection()");
                        connection = factory.createConnection();
                    }

                    // Start the connection so consumers can read
                    logger.debug("JMSQueue.connect - connection.start()");
                    connection.start();
                    logger.debug("JMSQueue.connect - connection.start() succeeded");

                    // Find the queue
                    logger.debug(String.format("JMSQueue.connect - ctx.lookup(%s)", queueName));
                    destination = (Destination) ctx.lookup(queueName);

                    // Save values so we can reuse the same connection
                    connectionCache.put(connectionCacheID, connection);
                    destinationCache.put(destinationCacheID, destination);

                    connection.setExceptionListener(new MyConnectionExceptionListener());
                } catch (JMSException e) {
                    logger.debug("JMSQueue.connect - exception", e);
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (JMSException ignore) {
                            logger.debug("JMSQueue.connect - connection.close() Exception" + ignore);
                        } finally {
                            connection = null;
                        }
                    }
                    connectionCache.remove(connectionCacheID);
                    destinationCache.remove(destinationCacheID);

                    if (attempt >= CONNECT_RETRY_LIMIT) {
                        throw e;
                    }
                }
            }
        } // end synchronized(jmsSyncObj)
        this.connection = connection;
        this.destination = destination;
    }

    protected class JMSOutputStream extends OutputStream {
        private Session session = null;
        private BytesMessage message = null;
        public JMSOutputStream(String filename) throws JMSException {
            super();
            try {
                this.session = getJMSSession();
                this.message = session.createBytesMessage();
                for (Entry<String,String> property : config.getJMSMessageProperties().entrySet()) {
                    message.setStringProperty(property.getKey(), property.getValue());
                }
                String filenameProperty = config.getFilenameProperty();
                if (!Strings.isNullOrEmpty(filenameProperty)) {
                    message.setStringProperty(filenameProperty, filename);
                }
            } catch (ConnectorPropertyException e) {
                logger.debug("could not read message property configuration", e);
                // ignore it
            } catch (JMSException e) {
                closeSessionQuietly(session);
                throw e;
            }
        }
        @Override
        public void write(int b) throws IOException {
            try {
                message.writeByte((byte)(b & 0xFF));
            } catch (JMSException e) {
                throw new IOException("JMS exception", e);
            }
        }
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                message.writeBytes(b, off, len);
            } catch (JMSException e) {
                throw new IOException("JMS exception", e);
            }
        }
        @Override
        public void write(byte[] b) throws IOException {
            try {
                message.writeBytes(b);
            } catch (JMSException e) {
                throw new IOException("JMS exception", e);
            }
        }
        @Override
        public void close() throws IOException {
            MessageProducer producer = null;
            try {
                producer = session.createProducer(destination);
                producer.send(message);
            } catch (JMSException e) {
                throw new IOException("JMS exception", e);
            } finally {
                closeMessageProducerQuietly(producer);
                closeSessionQuietly(session);
            }
        }
    }        

    protected class JMSInputStream extends InputStream {
        private Session session = null;
        private BytesMessage message = null;
        private long offset = 0L;

        public JMSInputStream(String messageID) throws JMSException, FileNotFoundException {
            super();
            message = peek(messageID);
            if (message == null) {
                throw new FileNotFoundException();
            }
        }
        @Override
        public int read() throws IOException {
            try {
                offset++;
                return message.readByte();
            } catch (JMSException e) {
                throw new IOException(e);
            }
        }
        @Override
        public int read(byte[] b) throws IOException {
            try {
                int n = message.readBytes(b);
                if (n > 0) {
                    offset += n;
                }
                return n;
            } catch (JMSException e) {
                throw new IOException(e);
            }
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                int n;
                if (off == 0) {
                    n = message.readBytes(b, len);
                } else if (len < 0 || len > b.length) {
                    throw new IndexOutOfBoundsException();
                } else {
                    byte[] temp = new byte[len];
                    n = message.readBytes(temp, len);
                    System.arraycopy(temp, 0, b, off, n);
                }
                if (n > 0) {
                    offset += n;
                }
                return n;
            } catch (JMSException e) {
                throw new IOException(e);
            }
        }
        @Override
        public int available() throws IOException {
            try {
                return (int) Math.min(message.getBodyLength()-offset, Integer.MAX_VALUE);
            } catch (JMSException e) {
                throw new IOException(e);
            }
        }
        @Override
        public long skip(long n) throws IOException {
            throw new IOException("seek not supported");
        }
        @Override
        public void close() throws IOException {
            super.close();
            closeSessionQuietly(session);
        }
    }

    /*------------------------------------------------------------------------------
     * Sends a JMSMessage
     *----------------------------------------------------------------------------*/
    protected OutputStream send(String filename) throws JMSException {
        return new JMSOutputStream(filename);
    }

    /*------------------------------------------------------------------------------
     * Receive a JMSMessage leave it on the queue, opening a closing a new session
     *----------------------------------------------------------------------------*/
    private final static int PEEK_RETRY_LIMIT = 4;

    protected BytesMessage peek(String messageID) throws JMSException {
        logger.debug(String.format("JMSQueue.peek(messageID=%s)", messageID));
        Session session = null;
        try {
            session = getJMSSession();
            return peek(session, messageID);
        } finally {
            closeSessionQuietly(session);
        }
    }

    /*------------------------------------------------------------------------------
     * Receive a JMSMessage leave it on the queue, using an existing session
     *----------------------------------------------------------------------------*/
    protected BytesMessage peek(Session session, String messageID) throws JMSException {
        logger.debug(String.format("JMSQueue.peek(session, messageID=%s)", messageID));
        BytesMessage result = null;

        String selector = null;
        if (!Strings.isNullOrEmpty(messageID)) {
            selector = String.format("JMSMessageID = '%s'", messageID);
        }

        int n = 0;
        int attempt = 0;
        while (true) {
            attempt++;
            QueueBrowser browser = null;
            try {
                browser = getQueueBrowser(session, (Queue)destination, selector);
                for (Enumeration<?> e = browser.getEnumeration(); result==null && e.hasMoreElements();) {
                    Object test = e.nextElement();
                    if (test instanceof BytesMessage) {
                        BytesMessage message = (BytesMessage)test;
                        String testID = message.getJMSMessageID();
                        if (messageID == null || messageID.equals(testID)) {
                            result = message;
                        }
                    }
                    n++;
                }
                // we have checked n messages -- done
                if (result == null) {
                    logger.debug(String.format("JMSQueue.peek(%s) - %d messages inspected, not found", messageID, n));
                }
                break;
            } catch (JMSException e) {
                logger.debug(String.format("JMSQueue.peek(%s) - exception", messageID), e);
                if (attempt >= PEEK_RETRY_LIMIT) {
                    throw e;
                }
            } finally {
                closeQueueBrowserQuietly(browser);
            }
        }
        return result;
    }

    /*------------------------------------------------------------------------------
     * Receive a JMSMessage leave it on the queue, using an existing session
     *----------------------------------------------------------------------------*/
    protected List<BytesMessage> list() throws JMSException {
        logger.debug(String.format("JMSQueue.list()"));
        Session session = null;
        try {
            session = getJMSSession();
        } catch (JMSException e) {
            throw e;
        }
        String selector = null;
        try {
            selector = config.getSelectorExpression();
        } catch (ConnectorPropertyException ignore) {
            logger.debug("could not read selector expression configuration", ignore);
        }
        int max = 10;
        try {
            max = config.getMaximumMessages();
        } catch (ConnectorPropertyException ignore) {
            logger.debug("could not read maximum messages configuration", ignore);
        }

        List<BytesMessage> result = new ArrayList<>();
        int attempt = 0;
        while (true) {
            attempt++;
            QueueBrowser browser = null;
            try {
                browser = getQueueBrowser(session, (Queue)destination, selector);
                for (Enumeration<?> e = browser.getEnumeration(); (max==0 || result.size() < max) && e.hasMoreElements();) {
                    Object test = e.nextElement();
                    if (test instanceof BytesMessage) {
                        BytesMessage message = (BytesMessage)test;
                        result.add(message);
                    }
                }
                logger.debug(String.format("JMSQueue.list() - %d messages found", result.size()));
                break;
            } catch (JMSException e) {
                logger.debug(String.format("JMSQueue.list() - exception"), e);
                if (attempt >= PEEK_RETRY_LIMIT) {
                    throw e;
                }
            } finally {
                closeQueueBrowserQuietly(browser);
            }
        }
        return result;
    }

    /*------------------------------------------------------------------------------
     * Delete a message from the queue
     *----------------------------------------------------------------------------*/
    private static final int[] DELETE_WAIT_TIMES = {1000, 2000, 3000};
    protected void delete(String messageID) throws JMSException, IOException {
        logger.debug(String.format("JMSQueue.delete(%s)", messageID));
        if (Strings.isNullOrEmpty(messageID)) {
            throw new IOException("JMSQueue.delete - messageID is required");
        }
        String selector = String.format("JMSMessageID = '%s'", messageID);
        Session session = getJMSSession();
        IOException exception = null;
        JMSException jmsException = null;
        for (int wait : DELETE_WAIT_TIMES) {
            try {
                BytesMessage message = peek(session, messageID);
                if (message == null) {
                    logger.debug(String.format("JMSQueue.delete(%s) - not found", messageID));
                    exception = new IOException(String.format("JMS Message ID %s not found", messageID));
                    continue;
                }
            } catch (JMSException e) {
                logger.debug(String.format("JMSQueue.delete(%s) - exception", e));
                jmsException = e;
                continue;
            }

            // Attempt to 'consume' (delete) the message from the queue
            MessageConsumer consumer = null;
            try {
                consumer = session.createConsumer(destination, selector);
                Message msg = consumer.receive(wait);
                if (msg == null) {
                    logger.debug(String.format("JMSQueue.delete(%s) - timed out", messageID));
                    exception = new IOException(String.format("JMS Message ID %s timed out on delete", messageID));
                } else {
                    logger.debug(String.format("JMSQueue.delete(%s) - succeeded", messageID));
                    break;
                }
            } catch (JMSException e) {
                logger.debug(String.format("JMSQueue.delete(%s) - exception", messageID), e);
                jmsException = e;
            } finally {
                closeMessageConsumerQuietly(consumer);
            }
        }
        closeSessionQuietly(session);
        if (exception != null) {
            throw exception;
        }
        if (jmsException != null) {
            throw jmsException;
        }
    }

    /*------------------------------------------------------------------------------
     * Return a list of items on the JMS queue matching the search criteria
     *----------------------------------------------------------------------------*/
    /*
    protected JMSItemData[] peekQueueItems(String srchFilename, String srchID, String jmsSelector, boolean firstOnly,
            boolean origOpenAttempted) throws Exception {
        logger.debug("JMSQueue.peekQueueItems(srchFilename=" + srchFilename + " srchID=" + srchID + " jmsSelector="
                + jmsSelector + " firstOnly=" + firstOnly + ") maxMessages=" + maxMessagesLong);

        // (Re)build JMS Selector
        boolean hasSelector = ((jmsSelector != null) && (jmsSelector.trim().length() > 0));
        boolean hasSrchFilename = ((srchFilename != null) && (srchFilename.length() > 0));
        boolean hasSrchID = ((srchID != null) && (srchID.length() > 0));
        StringBuilder sb = new StringBuilder();
        if (hasSelector) {
            if (hasSrchID || (hasSrchFilename && (this.jmsFilenameProp != null))) {
                if (jmsSelector.startsWith("(") && jmsSelector.endsWith(")"))
                    sb.append(jmsSelector);
                else {
                    sb.append("(");
                    sb.append(jmsSelector);
                    sb.append(")");
                }
            } else
                sb.append(jmsSelector);
        }
        if (hasSrchID) {
            if (sb.length() > 0)
                sb.append(" AND ");
            sb.append("(JMSMessageID = '");
            sb.append(srchID);
            sb.append("')");
        }
        if (hasSrchFilename && (this.jmsFilenameProp != null)) {
            if (sb.length() > 0)
                sb.append(" AND ");
            sb.append("(");
            sb.append(this.jmsFilenameProp);
            sb.append(" = '");
            sb.append(srchFilename);
            sb.append("')");
        }
        String newJmsSelector = null;
        if (sb.length() > 0) {
            newJmsSelector = sb.toString();
            logger.debug("JMSQueue.peekQueueItems> updated JmsSelector: " + newJmsSelector);
        }

        ArrayList<JMSItemData> itemDataArray = new ArrayList<JMSItemData>();

        Session jmsSession = null;
        QueueBrowser qBrowser = null;
        try {
            jmsSession = getJMSSession();
            qBrowser = getJMSQueueBrowser(jmsSession, (Queue) destination, newJmsSelector);

            Enumeration qEnumeration = qBrowser.getEnumeration();
            long numItemsOnQueue = 0;
            long numNonByteMsgItems = 0;
            boolean moreElements = true;

            while (moreElements) {
                Message qMessage;
                try {
                    qMessage = nextQEnumerationMessage(qEnumeration);
                    numItemsOnQueue++;
                } catch (NoSuchElementException noSuchElementException) {
                    moreElements = false;
                    continue;
                }

                String jmsMsgID = qMessage.getJMSMessageID();
                if (qMessage instanceof BytesMessage) {
                    String jmsFilename = null;
                    if (this.jmsFilenameProp != null)
                        jmsFilename = qMessage.getStringProperty(this.jmsFilenameProp);
                    if ((jmsFilename == null) || (jmsFilename.trim().length() == 0))
                        jmsFilename = LexURIFile.sanitizeFilename(jmsMsgID);
                    boolean addItem;
                    if ((srchFilename == null) && (srchID == null))
                        addItem = true;
                    else if ((srchID != null) && (!srchID.equals(jmsMsgID)))
                        addItem = false;
                    else if ((srchFilename != null) && (!srchFilename.equals(jmsFilename)))
                        addItem = false;
                    else
                        addItem = true;
                    if (addItem) {
                        BytesMessage bytesMsg = (BytesMessage) qMessage;
                        JMSItemData itemData = new JMSItemData(jmsMsgID, jmsFilename, bytesMsg.getBodyLength(),
                                bytesMsg.getJMSTimestamp());
                        itemDataArray.add(itemData);
                        if (firstOnly)
                            moreElements = false;
                        else if ((this.maxMessagesLong > 0)
                                && ((numItemsOnQueue - numNonByteMsgItems) >= this.maxMessagesLong)) {
                            moreElements = false;
                            logger.debug("JMSQueue.peekQueueItems> Skipping remaining items because we have reached "
                                    + this.maxMessagesLong + " (maxMessages) items");
                        }
                    }
                } else {
                    numNonByteMsgItems++;
                    logger.debug("JMSQueue.peekQueueItems> Skipping non-ByteMessage. Message ID=" + jmsMsgID);
                }
            } // while (moreElements)

            if ((LexHostBean.options != null) && (LexHostBean.options.isDebugURI())) {
                if (!firstOnly) {
                    logger.debug("JMSQueue.peekQueueItems> # of items on queue=" + numItemsOnQueue);
                    if (numNonByteMsgItems > 0)
                        logger.debug("JMSQueue.peekQueueItems> # of non-BytesMessage Items=" + numNonByteMsgItems);
                }
                logger.debug("JMSQueue.peekQueueItems> # of matching items=" + itemDataArray.size());
                if (itemDataArray.size() > 0) {
                    int ctr = 0;
                    for (JMSItemData item : itemDataArray)
                        logger.debug(" " + (++ctr) + ". " + item.toString());
                }
            }
        } catch (Exception ex) {
            logger.debug(true, "JMSQueue.peekQueueItems> Exception", ex);
            throw ex;
        } finally {
            closeJMSQueueBrowserQuietly(qBrowser);
            closeJMSSessionQuietly(jmsSession);
        }
        return itemDataArray.toArray(new JMSItemData[-1]);
    }
    */

    /*------------------------------------------------------------------------------
     * Close the queue handle
     *----------------------------------------------------------------------------*/
    protected void close() {
        logger.debug(String.format("JMSQueue.close - closing session for queue %s", queueName));
    }

    /*------------------------------------------------------------------------------
     * Create a JMS session
     *----------------------------------------------------------------------------*/
    private static final int SESSION_RETRY_LIMIT = 4;

    private Session getJMSSession() throws JMSException {
        Session result = null;

        Connection jmsConnection = connectionCache.get(connectionCacheID);
        int attempt = 0;
        while(result == null) {
            attempt++;
            try {
                logger.debug("JMSQueue.getJMSSession> Calling jmsConnection.createSession(F,AA)...");
                synchronized (jmsSyncObj) {
                    result = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                }
                logger.debug("JMSQueue.getJMSSession> Successfully created Session");
                if (attempt > 1) {
                    logger.debug("JMSQueue.getJMSSession> attempt=" + attempt + " retry SUCCEEDED");
                }
            } catch (JMSException e) {
                logger.debug("JMSQueue.getJMSSession> attempt=" + attempt + " failed: ", e);
                if (attempt >= SESSION_RETRY_LIMIT) {
                    throw e;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignore) {
                    // ignore
                }
            }
        }
        return result;
    }

    /*------------------------------------------------------------------------------
     * Create a JMS queue browser
     *----------------------------------------------------------------------------*/
    private static final int BROWSER_RETRY_LIMIT = 4;

    private QueueBrowser getQueueBrowser(Session session, Queue queue, String selector) throws JMSException {
        QueueBrowser result = null;
        int attempt = 0;
        while (result == null) {
            try {
                attempt++;
                if (!Strings.isNullOrEmpty(selector)) {
                    result = session.createBrowser(queue, selector);
                } else {
                    result = session.createBrowser(queue);
                }

                if (attempt > 1) {
                    logger.debug("JMSQueue.getQueueBrowser> attempt=" + attempt + " retry SUCCEEDED");
                }
            } catch (JMSException e) {
                logger.debug("JMSQueue.getQueueBrowser> attempt=" + attempt + " failed", e);
                if (attempt >= BROWSER_RETRY_LIMIT) {
                    throw e;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignore) {
                    // ignore
                }
            }
        }
        return result;
    }

    /*------------------------------------------------------------------------------
     * Close a JMS session
     *----------------------------------------------------------------------------*/
    private void closeSessionQuietly(Session jmsSession) {
        try {
            if (jmsSession != null) {
                jmsSession.close();
            }
        } catch (JMSException ignore) {
            logger.debug("JMSQueue.closeSessionQuietly - exception", ignore);
        }
    }

    /*------------------------------------------------------------------------------
     * Close a JMS queue browser
     *----------------------------------------------------------------------------*/
    private void closeQueueBrowserQuietly(QueueBrowser qBrowser) {
        try {
            if (qBrowser != null) {
                qBrowser.close();
            }
        } catch (JMSException ignore) {
            logger.debug("JMSQueue.closeQueueBrowserQuietly - exception", ignore);
        }
    }

    /*------------------------------------------------------------------------------
     * Close a JMS MessageConsumer
     *----------------------------------------------------------------------------*/
    private void closeMessageConsumerQuietly(MessageConsumer consumer) {
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (JMSException ignore) {
            logger.debug("JMSQueue.closeMessageConsumerQuietly - exception", ignore);
        }
    }

    /*------------------------------------------------------------------------------
     * Close a JMS MessageProducer
     *----------------------------------------------------------------------------*/
    private void closeMessageProducerQuietly(MessageProducer producer) {
        try {
            if (producer != null) {
                producer.close();
            }
        } catch (JMSException ignore) {
            logger.debug("JMSQueue.closeMessageProducerQuietly - exception", ignore);
        }
    }

   /*------------------------------------------------------------------------------
     * Close a JMS connection
     *----------------------------------------------------------------------------*/
    private void closeConnection() {
        logger.debug(String.format("JMSQueue.closeConnection - cacheID=%s", connectionCacheID));
        synchronized (jmsSyncObj) {
            // Get the current Connection/Destination
            Connection currentConnection = connectionCache.get(connectionCacheID);
            Destination currentDestination = destinationCache.get(destinationCacheID);

            if (((destination != null) && (destination == currentDestination))
                    || ((connection != null) && (connection == currentConnection))) {
                // Remove from HashMap so the connection will be re-established
                connectionCache.remove(connectionCacheID);
                destinationCache.remove(destinationCacheID);

                // Close the Connection
                if ((connection != null) && (connection == currentConnection)) {
                    try {
                        currentConnection.close();
                    } catch (JMSException ignore) {
                        logger.debug("JMSQueue.closeConnection - exception", ignore);
                    }
                }
            }
        }
        logger.debug("JMSQueue.closeConnection> Completed");
    }

    /*------------------------------------------------------------------------------
     * Connection ExceptionListener
     *----------------------------------------------------------------------------*/
    public class MyConnectionExceptionListener implements ExceptionListener {

        public MyConnectionExceptionListener() {
        }

        public void onException(JMSException jmsEx) {
            logger.debug("JMSQueue.onException - connection exception", jmsEx);
            JMSQueue.this.closeConnection();
        }
    }

}
