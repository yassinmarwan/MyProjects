package com.team22.eventticketing.user.dto;

import java.util.List;

public class ActivityFeedResponse {

    private List<AuthEventDTO> content;
    private int page;
    private int size;
    private long totalElements;

    public ActivityFeedResponse() {}

    public ActivityFeedResponse(List<AuthEventDTO> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<AuthEventDTO> getContent() { return content; }
    public void setContent(List<AuthEventDTO> content) { this.content = content; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
}
