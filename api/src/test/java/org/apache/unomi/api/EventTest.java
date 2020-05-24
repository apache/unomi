package org.apache.unomi.api;


import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

class EventTest extends TestCase {
    @Test
    public void testSendAt() {
        Date now = new Date();
        Event e = new Event();
        Date nextOneHour = new Date();
        nextOneHour.setTime(now.getTime() + 60 * 60);
        e.setTimeStamp(nextOneHour);
        Date nextTwoHour = new Date();
        nextOneHour.setTime(now.getTime() + 2 * 60 * 60);
        e.setSendAt(nextTwoHour);
        assert e.getSendAt().getTime() == now.getTime();
        Assert.assertEquals(e.getTimeStamp().getTime(), (now.getTime() - 60 * 60));
    }
}