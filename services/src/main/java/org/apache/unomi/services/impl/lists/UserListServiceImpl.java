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

package org.apache.unomi.services.impl.lists;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.UserListService;
import org.apache.unomi.services.impl.AbstractServiceImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by amidani on 24/03/2017.
 */
public class UserListServiceImpl extends AbstractServiceImpl implements UserListService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(UserListServiceImpl.class.getName());

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");
        bundleContext.addBundleListener(this);
        logger.info("User list service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("User list service shutdown.");
    }

    public List<UserList> getAllUserLists() {
        return persistenceService.getAllItems(UserList.class);
    }

    public PartialList<Metadata> getUserListMetadatas(int offset, int size, String sortBy) {
        return getMetadatas(offset, size, sortBy, UserList.class);
    }


    @Override
    public void bundleChanged(BundleEvent bundleEvent) { }
}
