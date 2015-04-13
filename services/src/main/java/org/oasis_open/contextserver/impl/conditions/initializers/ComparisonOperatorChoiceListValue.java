/**
 * ==========================================================================================
 * =                        DIGITAL FACTORY v7.0 - Community Distribution                   =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia's Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to "the Tunnel effect", the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 *
 * JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION
 * ============================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, and it is also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.oasis_open.contextserver.impl.conditions.initializers;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;

/**
 * Choice list value for the comparison operator, which also includes the information about applicable value types for the operator.
 * 
 * @author Sergiy Shyrkov
 */
public class ComparisonOperatorChoiceListValue extends ChoiceListValue implements PluginType {

    private Set<String> appliesTo = Collections.emptySet();

    private long pluginId;

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     */
    public ComparisonOperatorChoiceListValue(String id, String name) {
        super(id, name);
    }

    /**
     * Initializes an instance of this class.
     * 
     * @param id
     *            the ID of the property
     * @param name
     *            the display name
     * @param appliesTo
     *            array of value types this operator applies to; if not specified the operator is applicable for all value types
     */
    public ComparisonOperatorChoiceListValue(String id, String name, String... appliesTo) {
        this(id, name);
        if (appliesTo != null && appliesTo.length > 0) {
            this.appliesTo = new HashSet<>(appliesTo.length);
            for (String at : appliesTo) {
                this.appliesTo.add(at);
            }
        }
    }

    /**
     * Returns a set of value types this comparison operator is applicable to. Returns an empty set in case there are no type restrictions,
     * i.e. operator can be applied to any value type.
     * 
     * @return a set of value types this comparison operator is applicable to. Returns an empty set in case there are no type restrictions,
     *         i.e. operator can be applied to any value type
     */
    public Set<String> getAppliesTo() {
        return appliesTo;
    }

    @Override
    public long getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }
}
