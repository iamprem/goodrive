package com.iamprem.goodrive.entity;

/**
 * Created by prem on 8/23/15.
 */
public class FilesMeta {

    String id;
    String localName;
    String remoteName;
    String localPath;
    String parentId;
    String remoteStatus;
    String localStatus;
    long localModified;
    String mimeType;

    public FilesMeta(String id, String localName, String remoteName, String localPath, String parentId,
                     String remoteStatus, String localStatus, long localModified, String mimeType) {
        this.id = id;
        this.localName = localName;
        this.remoteName = remoteName;
        this.localPath = localPath;
        this.parentId = parentId;
        this.remoteStatus = remoteStatus;
        this.localStatus = localStatus;
        this.localModified = localModified;
        this.mimeType = mimeType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getRemoteStatus() {
        return remoteStatus;
    }

    public void setRemoteStatus(String remoteStatus) {
        this.remoteStatus = remoteStatus;
    }

    public String getLocalStatus() {
        return localStatus;
    }

    public void setLocalStatus(String localStatus) {
        this.localStatus = localStatus;
    }

    public long getLocalModified() {
        return localModified;
    }

    public void setLocalModified(long localModified) {
        this.localModified = localModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
