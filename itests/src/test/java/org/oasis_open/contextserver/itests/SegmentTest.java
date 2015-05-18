package org.oasis_open.contextserver.itests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SegmentTest extends BaseTest{
    private final static Logger LOGGER = LoggerFactory.getLogger(SegmentTest.class);
    @Inject
    protected SegmentService segmentService;

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        Set<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, 50, null);
        Assert.assertNotEquals("Segment metadata list should not be empty", 0, segmentMetadatas.size());
        LOGGER.info("Retrieved " + segmentMetadatas.size() + " segment metadata entries");
    }
}
