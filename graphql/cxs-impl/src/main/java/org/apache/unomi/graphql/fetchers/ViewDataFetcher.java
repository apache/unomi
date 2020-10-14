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
 * limitations under the License.
 */
package org.apache.unomi.graphql.fetchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.unomi.api.Topic;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPView;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class ViewDataFetcher
    implements DataFetcher<List<CDPView>>
{

    @Override
    public List<CDPView> get( final DataFetchingEnvironment environment )
        throws Exception
    {
        final ServiceManager serviceManager = environment.getContext();

        final PersistenceService persistenceService = serviceManager.getService( PersistenceService.class );

        final List<Map<String, Long>> scopes = new ArrayList<>();

        scopes.add( persistenceService.aggregateWithOptimizedQuery( null, new TermsAggregate( "scope" ), Topic.ITEM_TYPE ) );
        scopes.add( persistenceService.aggregateWithOptimizedQuery( null, new TermsAggregate( "metadata.scope" ), Segment.ITEM_TYPE ) );
        scopes.add( persistenceService.aggregateWithOptimizedQuery( null, new TermsAggregate( "metadata.scope" ), UserList.ITEM_TYPE ) );

        return scopes.stream().
            filter( Objects::nonNull ).
            map( Map::keySet ).
            flatMap( scopeAsKeys -> scopeAsKeys.stream().
                filter( scope -> !"_filtered".equals( scope ) && !"_missing".equals( scope ) && !"_all".equals( scope ) ).
                map( CDPView::new ) ).collect( Collectors.toList() );
    }

}
