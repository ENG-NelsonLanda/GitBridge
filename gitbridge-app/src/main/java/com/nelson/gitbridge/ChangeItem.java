package com.nelson.gitbridge;

public class ChangeItem {

    private final String status;
    private final String fileName;

    public ChangeItem(String status, String fileName) {
        this.status = status;
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }
}