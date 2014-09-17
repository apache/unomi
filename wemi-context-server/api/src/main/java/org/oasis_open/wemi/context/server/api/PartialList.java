package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list that is actually a sub-set of a larger list.
 */
@XmlRootElement
public class PartialList<T> implements Serializable {

    private List<T> list;
    private long offset;
    private long pageSize;
    private long totalSize;

    public PartialList() {
        list = new ArrayList<T>();
        offset = 0;
        pageSize = 0;
        totalSize = 0;
    }

    public PartialList(List<T> list, long offset, long pageSize, long totalSize) {
        this.list = list;
        this.offset = offset;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    @XmlTransient
    public int size() {
        return list.size();
    }

    @XmlTransient
    public T get(int index) {
        return list.get(index);
    }

}
