package org.oasis_open.contextserver.itests;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 * Integration tests for various condition query builder types (elasticsearch).
 *
 * @author Sergiy Shyrkov
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ConditionESQueryBuilderTest extends ConditionEvaluatorTest {

    @Inject
    private SegmentService segmentService;

    @Inject
    private DefinitionsService definitionsService;

    @Override
    protected boolean eval(Condition c) {
        List<Item> list = persistenceService.query(c,null,(Class<Item>) item.getClass());
        return list.contains(item);
    }

    @Before
    public void setUp() {
        super.setUp();
        persistenceService.save(item);
        persistenceService.refresh();
    }

    @After
    public void tearDown() {
        persistenceService.remove(item.getItemId(), item.getClass());
    }

}
