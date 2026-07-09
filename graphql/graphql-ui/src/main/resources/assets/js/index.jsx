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

import 'graphiql/setup-workers/webpack';
import 'graphiql/graphiql.css';
import { GraphiQL } from 'graphiql';
import { createGraphiQLFetcher } from '@graphiql/toolkit';
import { createClient } from 'graphql-ws';
import * as React from 'react';
import { createRoot } from 'react-dom/client';

function graphqlHttpUrl() {
    const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
    return protocol + '//' + window.location.host + '/graphql';
}

function graphqlWsUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return protocol + '//' + window.location.host + '/graphql';
}

function createFetcher() {
    return createGraphiQLFetcher({
        url: graphqlHttpUrl(),
        wsClient: createClient({ url: graphqlWsUrl() }),
    });
}

function QueryPlayground() {
    return <GraphiQL fetcher={createFetcher()} />;
}

document.addEventListener('DOMContentLoaded', function () {
    const rootElement = document.getElementById('root');
    if (rootElement) {
        createRoot(rootElement).render(<QueryPlayground />);
    }
}, false);
