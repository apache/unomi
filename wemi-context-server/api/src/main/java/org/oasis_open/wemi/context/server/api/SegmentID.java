package org.oasis_open.wemi.context.server.api;

/**
 * Created by loom on 24.04.14.
 */
public class SegmentID implements Comparable<SegmentID> {

    private String id;

    public SegmentID() {

    }

    public SegmentID(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public int compareTo(SegmentID o) {
        if (id != null) {
            return id.compareTo(o.id);
        } else {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SegmentID segmentID = (SegmentID) o;

        if (id != null ? !id.equals(segmentID.id) : segmentID.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
