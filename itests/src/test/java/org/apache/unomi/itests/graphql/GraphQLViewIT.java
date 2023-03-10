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
package org.apache.unomi.itests.graphql;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;
import org.junit.Assert;
import org.junit.Test;
import org.ops4j.pax.exam.util.Filter;

public class GraphQLViewIT
    extends BaseGraphQLIT
{

    @Test
    public void test()
        throws Exception
    {
        // prepare
        final UserList userList = createList();
        final Topic topic = createTopic();
        final Segment segment = createSegment();

        persistenceService.refreshIndex(Segment.class);

        // test
        try (CloseableHttpResponse response = post( "graphql/views/get-views.json" ))
        {
            final ResponseContext context = ResponseContext.parse( response.getEntity() );

            Assert.assertNotNull( context.getValue( "data.cdp.getViews" ) );

            List<Map> views = context.getValue( "data.cdp.getViews" );

            Assert.assertTrue( views.size() >= 3 );
        }
        finally
        {
            topicService.delete( topic.getTopicId() );
            userListService.delete( userList.getItemId() );
            segmentService.removeSegmentDefinition( segment.getItemId(), false );
        }
    }

    private Topic createTopic()
        throws InterruptedException
    {
        final Topic topic = new Topic();

        topic.setTopicId( "topicId_GraphQLViewIT" );
        topic.setItemId( "topicId_GraphQLViewIT" );
        topic.setName( "topicName_GraphQLViewIT" );
        topic.setScope( "scope_GraphQLViewIT" );

        topicService.save( topic );

        keepTrying( "Failed waiting for the creation of the topic for the GraphQLViewIT test", () -> topicService.load( topic.getItemId() ),
                    Objects::nonNull, 1000, 100 );

        return topic;
    }

    private UserList createList()
        throws InterruptedException
    {
        final Metadata metadata = new Metadata();

        metadata.setId( "userListId_GraphQLViewIT" );
        metadata.setName( "userListName_GraphQLViewIT" );
        metadata.setScope( "userListScope_GraphQLViewIT" );

        final UserList userList = new UserList();

        userList.setItemType( UserList.ITEM_TYPE );
        userList.setMetadata( metadata );

        userListService.save( userList );

        keepTrying( "Failed waiting for the creation of the userList for the GraphQLViewIT test",
                    () -> userListService.load( userList.getItemId() ), Objects::nonNull, 1000, 100 );

        return userList;
    }

    private Segment createSegment()
        throws InterruptedException
    {
        final Segment segment = new Segment();

        segment.setItemType( Segment.ITEM_TYPE );
        segment.setMetadata( new Metadata() );

        segment.setItemId( "segmentId_GraphQLViewIT" );
        segment.getMetadata().setId( "segmentId_GraphQLViewIT" );
        segment.getMetadata().setName( "segmentName_GraphQLViewIT" );
        segment.getMetadata().setScope( "segmentScope_GraphQLViewIT" );

        final Condition condition = new Condition( definitionsService.getConditionType( "matchAllCondition" ) );

        segment.setCondition( condition );

        segmentService.setSegmentDefinition( segment );

        keepTrying( "Failed waiting for the creation of the segment for the GraphQLViewIT test",
                    () -> segmentService.getSegmentDefinition( segment.getItemId() ), Objects::nonNull, 1000, 100 );

        return segment;
    }

}
