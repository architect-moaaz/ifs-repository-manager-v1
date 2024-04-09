package io.intelliflow.repomanager.model.db;

import java.util.Set;

public class DataByUser {

    private String userId;
    private Set<String> workspaces;
    private Set<String> apps;
    private Set<String> files;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Set<String> getWorkspaces() {
        return workspaces;
    }

    public void setWorkspaces(Set<String> workspaces) {
        this.workspaces = workspaces;
    }

    public Set<String> getApps() {
        return apps;
    }

    public void setApps(Set<String> apps) {
        this.apps = apps;
    }

    public Set<String> getFiles() {
        return files;
    }

    public void setFiles(Set<String> files) {
        this.files = files;
    }
}
