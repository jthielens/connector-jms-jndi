package com.cleo.labs.connector.jmsjndi;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

import com.cleo.connector.api.helper.Logger;

/**
 * Azure Blob empty file attribute views
 */
public class JmsJndiMessageAttributes implements DosFileAttributes, DosFileAttributeView {
    private Logger logger;
    private Date time;
    private long length;

    public JmsJndiMessageAttributes(Logger logger, BytesMessage message) {
        this.logger = logger;
        try {
            this.time = new Date(message.getJMSTimestamp());
        } catch (JMSException e) {
            logger.debug("exception getting timestamp attribute", e);
            this.time = new Date();
        }
        try {
            this.length = message.getBodyLength();
        } catch (JMSException e) {
            logger.debug("exception getting bodylength attribute", e);
            this.length = 1;
        }
    }

    @Override
    public FileTime lastModifiedTime() {
        logger.debug(String.format("lastModifidedTime()=%s", time));
        return FileTime.fromMillis(time.getTime());
    }

    @Override
    public FileTime lastAccessTime() {
        logger.debug(String.format("lastAccessTime()=%s", time));
        return FileTime.fromMillis(time.getTime());
    }

    @Override
    public FileTime creationTime() {
        logger.debug(String.format("creationTime()=%s", time));
        return FileTime.fromMillis(time.getTime());
    }

    @Override
    public boolean isRegularFile() {
        logger.debug("isRegularFile()=true");
        return true; // messages are files
    }

    @Override
    public boolean isDirectory() {
        logger.debug("isDirectory()=false");
        return false; // messages are files
    }

    @Override
    public boolean isSymbolicLink() {
        logger.debug("isSymbolicLink()=false");
        return false; // messages are files
    }

    @Override
    public boolean isOther() {
        logger.debug("isOther()=false");
        return false; // messages are files
    }

    @Override
    public long size() {
        logger.debug("size()="+length);
        return length;
    }

    @Override
    public Object fileKey() {
        logger.debug("fileKey()=null");
        return null;
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        if (lastModifiedTime != null || lastAccessTime != null || createTime != null) {
            throw new UnsupportedOperationException("setTimes() not supported for JMS Queue Messages");
        }
    }

    @Override
    public String name() {
        return "JMS Queue Message";
    }

    @Override
    public DosFileAttributes readAttributes() throws IOException {
        return this;
    }

    @Override
    public void setReadOnly(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported for JMS Queue Messages");
    }

    @Override
    public void setHidden(boolean value) throws IOException {
        throw new UnsupportedOperationException("setHidden() not supported for JMS Queue Messages");
    }

    @Override
    public void setSystem(boolean value) throws IOException {
        throw new UnsupportedOperationException("setSystem() not supported for JMS Queue Messages");
    }

    @Override
    public void setArchive(boolean value) throws IOException {
        throw new UnsupportedOperationException("setArchive() not supported for JMS Queue Messages");
    }

    @Override
    public boolean isReadOnly() {
        logger.debug("isReadOnly()=false");
        return false;
    }

    @Override
    public boolean isHidden() {
        logger.debug("isHidden()=false");
        return false;
    }

    @Override
    public boolean isArchive() {
        logger.debug("isArchive()=false");
        return false;
    }

    @Override
    public boolean isSystem() {
        logger.debug("isSystem()=false");
        return false;
    }
}
