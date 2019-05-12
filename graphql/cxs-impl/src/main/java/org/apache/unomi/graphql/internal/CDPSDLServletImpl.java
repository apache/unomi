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
package org.apache.unomi.graphql.internal;

import com.google.common.base.Charsets;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

@Component(
        service={javax.servlet.http.HttpServlet.class,javax.servlet.Servlet.class},
        property = {"alias=/sdlgraphql", "jmx.objectname=graphql.servlet:type=graphql"}
)
public class CDPSDLServletImpl extends HttpServlet {

    private BundleContext bundleContext;

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    RuntimeWiring buildRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring()
                // .scalar(CustomScalar)
                // this uses builder function lambda syntax
                /*
                .type("QueryType", typeWiring -> typeWiring
                        .dataFetcher("hero", new StaticDataFetcher(StarWarsData.getArtoo()))
                        .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                        .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                )
                .type("Human", typeWiring -> typeWiring
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // you can use builder syntax if you don't like the lambda syntax
                .type("Droid", typeWiring -> typeWiring
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // or full builder syntax if that takes your fancy
                .type(
                        newTypeWiring("Character")
                                .typeResolver(StarWarsData.getCharacterTypeResolver())
                                .build()
                )
                */
                .build();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        Reader schemaReader = getSchemaReader("cdp-schema.graphqls");
        //File schemaFile2 = loadSchema("cdp-schema.graphqls");
        //File schemaFile3 = loadSchema("cdp-schema.graphqls");

        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();

        // each registry is merged into the main registry
        typeRegistry.merge(schemaParser.parse(schemaReader));
        //typeRegistry.merge(schemaParser.parse(schemaFile2));
        //typeRegistry.merge(schemaParser.parse(schemaFile3));

        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
    }

    private Reader getSchemaReader(String resourceUrl) {
        try {
            return new InputStreamReader(bundleContext.getBundle().getResource(resourceUrl).openConnection().getInputStream(), Charsets.UTF_8.name());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
