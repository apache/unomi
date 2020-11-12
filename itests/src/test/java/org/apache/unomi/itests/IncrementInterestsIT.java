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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.TopicService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class IncrementInterestsIT
    extends BaseIT
{

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject
    @Filter(timeout = 600000)
    protected TopicService topicService;

    @Test
    @SuppressWarnings("unchecked")
    public void test()
        throws InterruptedException
    {
        final Topic topic = createTopic( "topicId" );
        final Profile profile = createProfile();

        final Map<String, Double> interestsAsMap = new HashMap<>();
        interestsAsMap.put( topic.getTopicId(), 50.0 );
        interestsAsMap.put( "unknown", 10.0 );

        final Event event = createEvent( profile, interestsAsMap );

        try
        {
            int eventCode = eventService.send( event );

            if ( eventCode == EventService.PROFILE_UPDATED )
            {
                Profile updatedProfile = profileService.save( event.getProfile() );

                refreshPersistence();

                Map<String, Double> interests = (Map<String, Double>) updatedProfile.getProperty( "interests" );

                Assert.assertEquals( 0.5, interests.get( topic.getTopicId() ), 0.0 );
                Assert.assertFalse( interests.containsKey( "unknown" ) );
            }
        }
        finally
        {
            topicService.delete( topic.getItemId() );
            profileService.delete( profile.getItemId(), false );
        }
    }

    private Event createEvent( Profile profile, Map<String, Double> interestsAsMap )
    {
        final Event event = new Event( "incrementInterest", null, profile, null, null, profile, new Date() );

        event.setPersistent( false );
        event.setProperty( "interests", interestsAsMap );

        return event;
    }

    private Topic createTopic( final String topicId )
        throws InterruptedException
    {
        final Topic topic = new Topic();

        topic.setTopicId( topicId );
        topic.setItemId( topicId );
        topic.setName( "topicName" );
        topic.setScope( "scope" );

        topicService.save( topic );

        keepTrying( "Failed waiting for the creation of the topic for the IncrementInterestsIT test",
                    () -> topicService.load( topic.getItemId() ), Objects::nonNull, 1000, 100 );

        return topic;
    }

    private Profile createProfile()
        throws InterruptedException
    {
        final Profile profile = new Profile( UUID.randomUUID().toString() );

        profile.setProperty( "firstName", "FirstName" );
        profile.setProperty( "lastName", "LastName" );

        profileService.save( profile );

        keepTrying( "Failed waiting for the creation of the profile for the IncrementInterestsIT test",
                    () -> profileService.load( profile.getItemId() ), Objects::nonNull, 1000, 100 );

        return profile;
    }

}
