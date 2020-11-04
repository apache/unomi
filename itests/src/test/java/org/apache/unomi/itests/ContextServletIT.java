/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.apache.unomi.itests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.*;


/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
	private final static String CONTEXT_URL = "/context.json";
	private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
	private final static String SEGMENT_EVENT_TYPE = "test-event-type";
	private final static String SEGMENT_ID = "test-segment-id";
	private final static int SEGMENT_NUMBER_OF_DAYS = 30;

	private ObjectMapper objectMapper = new ObjectMapper();

	@Inject
	@Filter(timeout = 600000)
	protected EventService eventService;

	@Inject
	@Filter(timeout = 600000)
	protected PersistenceService persistenceService;

	@Inject
	@Filter(timeout = 600000)
	protected ProfileService profileService;

	@Inject
	@Filter(timeout = 600000)
	protected DefinitionsService definitionsService;

	@Inject
	@Filter(timeout = 600000)
	protected SegmentService segmentService;

	private Profile profile;

	@Before
	public void setUp() throws InterruptedException {
		//Create a past-event segment
		Metadata segmentMetadata = new Metadata(SEGMENT_ID);
		Segment segment = new Segment(segmentMetadata);
		Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
		segmentCondition.setParameter("minimumEventCount",2);
		segmentCondition.setParameter("numberOfDays",SEGMENT_NUMBER_OF_DAYS);
		Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
		pastEventEventCondition.setParameter("eventTypeId",SEGMENT_EVENT_TYPE);
		segmentCondition.setParameter("eventCondition",pastEventEventCondition);
		segment.setCondition(segmentCondition);
		segmentService.setSegmentDefinition(segment);

		String profileId = "test-profile-id";
		profile = new Profile(profileId);
		profileService.save(profile);

		refreshPersistence();
	}

	@After
	public void tearDown() {
		TestUtils.removeAllEvents(definitionsService, persistenceService);
		TestUtils.removeAllSessions(definitionsService, persistenceService);
		TestUtils.removeAllProfiles(definitionsService, persistenceService);
		profileService.delete(profile.getItemId(), false);
		segmentService.removeSegmentDefinition(SEGMENT_ID,false);
	}


	@Test
	public void testOGNLVulnerability() throws IOException, InterruptedException {

		File vulnFile = new File("target/vuln-file.txt");
		if (vulnFile.exists()) {
			vulnFile.delete();
		}
		String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
		vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

		Map<String,String> parameters = new HashMap<>();
		parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("security/ognl-payload-1.json", parameters), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());

	}

	@Test
	public void testMVELVulnerability() throws IOException, InterruptedException {

		File vulnFile = new File("target/vuln-file.txt");
		if (vulnFile.exists()) {
			vulnFile.delete();
		}
		String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
		vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

		Map<String,String> parameters = new HashMap<>();
		parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("security/mvel-payload-1.json", parameters), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());

	}
}
