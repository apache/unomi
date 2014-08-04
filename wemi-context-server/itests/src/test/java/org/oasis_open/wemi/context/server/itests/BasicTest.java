package org.oasis_open.wemi.context.server.itests;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

import java.io.File;
import java.util.Set;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by loom on 04.08.14.
 */
@RunWith(PaxExam.class)
public class BasicTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(BasicTest.class);

    @Inject
    protected SegmentService segmentService;

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
            .groupId("org.apache.karaf")
            .artifactId("apache-karaf")
            .version("3.0.1")
            .type("tar.gz");

        MavenUrlReference karafStandardRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("standard")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafPaxWebRepo = maven()
            .groupId("org.ops4j.pax.web")
            .artifactId("pax-web-features")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafSpringRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("spring")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafCxfRepo = maven()
            .groupId("org.apache.cxf.karaf")
            .artifactId("apache-cxf")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference karafEnterpriseRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("enterprise")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        MavenUrlReference wemiServerRepo = maven()
            .groupId("org.oasis-open.wemi")
            .artifactId("wemi-context-server-kar")
            .classifier("features")
            .type("xml")
            .versionAsInProject();
        return new Option[] {
            // KarafDistributionOption.debugConfiguration("5005", true),
            karafDistributionConfiguration()
                .frameworkUrl(karafUrl)
                .unpackDirectory(new File("target/exam"))
                .useDeployFolder(false),
            keepRuntimeFolder(),
            KarafDistributionOption.features(karafPaxWebRepo, "war"),
            KarafDistributionOption.features(karafCxfRepo, "cxf"),
            KarafDistributionOption.features(karafStandardRepo, "openwebbeans"),
            KarafDistributionOption.features(karafStandardRepo , "pax-cdi-web-openwebbeans"),
            KarafDistributionOption.features(wemiServerRepo , "wemi-context-server-kar"),
                /*
            mavenBundle()
                .groupId("org.oasis-open.wemi")
                .artifactId("wemi-context-server-wab")
                .versionAsInProject().start(),
                */
       };
    }

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        Set<SegmentID> segmentIDs = segmentService.getSegmentIDs();
        Assert.assertNotEquals("Segment ID list should not be empty", 0, segmentIDs.size());
        LOGGER.info("Retrieved " + segmentIDs.size() + " segment IDs");
    }

}
