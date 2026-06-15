package com.garage.model;

public class QueueStatusResponse {
    private String requestId;
    private String status;
    private int queuePosition;
    private int queueSize;
    private boolean locked;
    private String currentHolder;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getQueuePosition() { return queuePosition; }
    public void setQueuePosition(int queuePosition) { this.queuePosition = queuePosition; }

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getCurrentHolder() { return currentHolder; }
    public void setCurrentHolder(String currentHolder) { this.currentHolder = currentHolder; }
}
