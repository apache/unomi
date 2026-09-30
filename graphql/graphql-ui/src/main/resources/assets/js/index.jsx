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

import {GraphiQL} from 'graphiql';
import {createGraphiQLFetcher} from '@graphiql/toolkit';
import * as React from 'react';
import * as ReactDOM from 'react-dom';
import {createClient} from 'graphql-ws';

// The browser WebSocket API cannot set request headers on the handshake, so the server authenticates a
// subscription from the connection_init payload instead. GraphiQL hands the live "Headers" tab content
// to the fetcher on every request, so capture it here and reuse its Authorization as the WebSocket
// connection parameters: HTTP and WebSocket then use the same credential, and nothing is persisted.
let latestHeaders = null;

function authorizationHeader() {
    if (!latestHeaders) {
        return null;
    }
    const key = Object.keys(latestHeaders).find((name) => name.toLowerCase() === 'authorization');
    return key && latestHeaders[key] ? latestHeaders[key] : null;
}

function createFetcher() {
    const fetcher = createGraphiQLFetcher({
        url: `http://localhost:8181/graphql`,
        wsClient: createClient(
            {
                url: `ws://localhost:8181/graphql`,
                // Evaluated on each (re)connect, and sent as the connection_init payload.
                connectionParams: () => {
                    const authorization = authorizationHeader();
                    return authorization ? { Authorization: authorization } : {};
                },
            }),
    });

    return (graphQLParams, opts) => {
        latestHeaders = (opts && opts.headers) || null;
        return fetcher(graphQLParams, opts);
    };
}

function QueryPlayground() {
    return (
        <GraphiQL fetcher={createFetcher()}></GraphiQL>
    );
}

document.addEventListener('DOMContentLoaded', function () {
    ReactDOM.render(<QueryPlayground/>, document.getElementById('root'));
}, false);
