package toys.eve.tournmgmt.common.util;

import io.vertx.core.http.HttpServerRequest;

public class Page {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private final int current;
    private final int pageSize;
    private int onePageSize;
    private int total;

    public Page(int current) {
        this(current, DEFAULT_PAGE_SIZE);
    }

    public Page(int current, int pageSize) {
        this.current = current;
        this.pageSize = pageSize;
    }

    public static Page fromRequest(HttpServerRequest request) {
        int current = Integer.parseInt(request.getParam("page") == null ? "1" : request.getParam("page"));
        return new Page(current);
    }

    public static Page fromRequest(HttpServerRequest request, int pageSize) {
        int current = Integer.parseInt(request.getParam("page") == null ? "1" : request.getParam("page"));
        return new Page(current, pageSize);
    }

    public int prev() {
        return current - 1;
    }

    public int next() {
        return current + 1;
    }

    public int start() {
        return offset() + 1;
    }

    public int offset() {
        return (current - 1) * pageSize;
    }

    public int end() {
        return offset() + onePageSize;
    }

    public int total() {
        return total;
    }

    public int pageSize() {
        return pageSize;
    }

    public void setOnePage(int onePageSize) {
        this.onePageSize = onePageSize;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
