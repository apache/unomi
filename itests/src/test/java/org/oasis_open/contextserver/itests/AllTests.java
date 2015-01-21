package org.oasis_open.contextserver.itests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Defines suite of test classes to run.
 * 
 * @author Sergiy Shyrkov
 */
@RunWith(Suite.class)
@SuiteClasses({ 
    //BasicTest.class, 
    ConditionEvaluatorTest.class,
    SegmentTest.class })
public class AllTests {
}
