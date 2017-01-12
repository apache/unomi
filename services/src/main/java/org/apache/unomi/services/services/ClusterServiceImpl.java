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

package org.apache.unomi.services.services;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.cellar.config.ClusterConfigurationEvent;
import org.apache.karaf.cellar.config.Constants;
import org.apache.karaf.cellar.core.*;
import org.apache.karaf.cellar.core.control.SwitchStatus;
import org.apache.karaf.cellar.core.event.EventProducer;
import org.apache.karaf.cellar.core.event.EventType;
import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.util.*;

/**
 * Implementation of the persistence service interface
 */
public class ClusterServiceImpl implements ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterServiceImpl.class.getName());

    public static final String CONTEXTSERVER_ADDRESS = "contextserver.address";
    public static final String CONTEXTSERVER_PORT = "contextserver.port";
    public static final String CONTEXTSERVER_SECURE_ADDRESS = "contextserver.secureAddress";
    public static final String CONTEXTSERVER_SECURE_PORT = "contextserver.securePort";
    public static final String KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION = "org.apache.unomi.nodes";
    public static final String KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS = "publicEndpoints";
    public static final String KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS = "secureEndpoints";

    private ClusterManager karafCellarClusterManager;
    private EventProducer karafCellarEventProducer;
    private GroupManager karafCellarGroupManager;
    private String karafCellarGroupName = Configurations.DEFAULT_GROUP_NAME;
    private ConfigurationAdmin osgiConfigurationAdmin;
    private String karafJMXUsername = "karaf";
    private String karafJMXPassword = "karaf";
    private int karafJMXPort = 1099;
    private String address;
    private String port;
    private String secureAddress;
    private String securePort;

    private Map<String,JMXConnector> jmxConnectors = new LinkedHashMap<>();

    PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setKarafCellarClusterManager(ClusterManager karafCellarClusterManager) {
        this.karafCellarClusterManager = karafCellarClusterManager;
    }

    public void setKarafCellarEventProducer(EventProducer karafCellarEventProducer) {
        this.karafCellarEventProducer = karafCellarEventProducer;
    }

    public void setKarafCellarGroupManager(GroupManager karafCellarGroupManager) {
        this.karafCellarGroupManager = karafCellarGroupManager;
    }

    public void setKarafCellarGroupName(String karafCellarGroupName) {
        this.karafCellarGroupName = karafCellarGroupName;
    }

    public void setOsgiConfigurationAdmin(ConfigurationAdmin osgiConfigurationAdmin) {
        this.osgiConfigurationAdmin = osgiConfigurationAdmin;
    }

    public void setKarafJMXUsername(String karafJMXUsername) {
        this.karafJMXUsername = karafJMXUsername;
    }

    public void setKarafJMXPassword(String karafJMXPassword) {
        this.karafJMXPassword = karafJMXPassword;
    }

    public void setKarafJMXPort(int karafJMXPort) {
        this.karafJMXPort = karafJMXPort;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setSecureAddress(String secureAddress) {
        this.secureAddress = secureAddress;
    }

    public void setSecurePort(String securePort) {
        this.securePort = securePort;
    }

    public void init() {
        logger.debug("init cluster service");
        if (karafCellarEventProducer != null && karafCellarClusterManager != null) {

            address = System.getProperty(CONTEXTSERVER_ADDRESS, address);
            port = System.getProperty(CONTEXTSERVER_PORT, port);
            secureAddress = System.getProperty(CONTEXTSERVER_SECURE_ADDRESS, secureAddress);
            securePort = System.getProperty(CONTEXTSERVER_SECURE_PORT, securePort);

            boolean setupConfigOk = true;
            Group group = karafCellarGroupManager.findGroupByName(karafCellarGroupName);
            if (setupConfigOk && group == null) {
                logger.error("Cluster group " + karafCellarGroupName + " doesn't exist");
                setupConfigOk = false;
            }

            // check if the producer is ON
            if (setupConfigOk && karafCellarEventProducer.getSwitch().getStatus().equals(SwitchStatus.OFF)) {
                logger.error("Cluster event producer is OFF");
                setupConfigOk = false;
            }

            // check if the config pid is allowed
            if (setupConfigOk && !isClusterConfigPIDAllowed(group, Constants.CATEGORY, KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION, EventType.OUTBOUND)) {
                logger.error("Configuration PID " + KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION + " is blocked outbound for cluster group " + karafCellarGroupName);
                setupConfigOk = false;
            }

            if (setupConfigOk) {
                Map<String, Properties> configurations = karafCellarClusterManager.getMap(Constants.CONFIGURATION_MAP + Configurations.SEPARATOR + karafCellarGroupName);
                org.apache.karaf.cellar.core.Node thisKarafNode = karafCellarClusterManager.getNode();
                Properties karafCellarClusterNodeConfiguration = configurations.get(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                if (karafCellarClusterNodeConfiguration == null) {
                    karafCellarClusterNodeConfiguration = new Properties();
                }
                String publicEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
                String secureEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
                String[] publicEndpointsArray = publicEndpointsPropValue.split(",");
                Set<String> publicEndpoints = new TreeSet<String>(Arrays.asList(publicEndpointsArray));
                String[] secureEndpointsArray = secureEndpointsPropValue.split(",");
                Set<String> secureEndpoints = new TreeSet<String>(Arrays.asList(secureEndpointsArray));
                publicEndpoints.add(thisKarafNode.getId() + "=" + address + ":" + port);
                secureEndpoints.add(thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
                karafCellarClusterNodeConfiguration.setProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, StringUtils.join(publicEndpoints, ","));
                karafCellarClusterNodeConfiguration.setProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, StringUtils.join(secureEndpoints, ","));
                configurations.put(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION, karafCellarClusterNodeConfiguration);
                ClusterConfigurationEvent clusterConfigurationEvent = new ClusterConfigurationEvent(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                clusterConfigurationEvent.setSourceGroup(group);
                karafCellarEventProducer.produce(clusterConfigurationEvent);
            }
        }
    }

    public void destroy() {
        for (Map.Entry<String,JMXConnector> jmxConnectorEntry : jmxConnectors.entrySet()) {
            String url = jmxConnectorEntry.getKey();
            JMXConnector jmxConnector = jmxConnectorEntry.getValue();
            try {
                jmxConnector.close();
            } catch (IOException e) {
                logger.error("Error closing JMX connector for url {}", url, e);
            }
        }
    }

    @Override
    public List<ClusterNode> getClusterNodes() {
        Map<String, ClusterNode> clusterNodes = new LinkedHashMap<String, ClusterNode>();

        Set<org.apache.karaf.cellar.core.Node> karafCellarNodes = karafCellarClusterManager.listNodes();
        org.apache.karaf.cellar.core.Node thisKarafNode = karafCellarClusterManager.getNode();
        Map<String, Properties> clusterConfigurations = karafCellarClusterManager.getMap(Constants.CONFIGURATION_MAP + Configurations.SEPARATOR + karafCellarGroupName);
        Properties karafCellarClusterNodeConfiguration = clusterConfigurations.get(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
        Map<String, String> publicNodeEndpoints = new TreeMap<>();
        Map<String, String> secureNodeEndpoints = new TreeMap<>();
        if (karafCellarClusterNodeConfiguration != null) {
            String publicEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
            String secureEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
            String[] publicEndpointsArray = publicEndpointsPropValue.split(",");
            Set<String> publicEndpoints = new TreeSet<String>(Arrays.asList(publicEndpointsArray));
            for (String endpoint : publicEndpoints) {
                String[] endpointParts = endpoint.split("=");
                publicNodeEndpoints.put(endpointParts[0], endpointParts[1]);
            }
            String[] secureEndpointsArray = secureEndpointsPropValue.split(",");
            Set<String> secureEndpoints = new TreeSet<String>(Arrays.asList(secureEndpointsArray));
            for (String endpoint : secureEndpoints) {
                String[] endpointParts = endpoint.split("=");
                secureNodeEndpoints.put(endpointParts[0], endpointParts[1]);
            }
        }
        for (org.apache.karaf.cellar.core.Node karafCellarNode : karafCellarNodes) {
            ClusterNode clusterNode = new ClusterNode();
            clusterNode.setHostName(karafCellarNode.getHost());
            String publicEndpoint = publicNodeEndpoints.get(karafCellarNode.getId());
            if (publicEndpoint != null) {
                String[] publicEndpointParts = publicEndpoint.split(":");
                clusterNode.setHostAddress(publicEndpointParts[0]);
                clusterNode.setPublicPort(Integer.parseInt(publicEndpointParts[1]));
            }
            String secureEndpoint = secureNodeEndpoints.get(karafCellarNode.getId());
            if (secureEndpoint != null) {
                String[] secureEndpointParts = secureEndpoint.split(":");
                clusterNode.setSecureHostAddress(secureEndpointParts[0]);
                clusterNode.setSecurePort(Integer.parseInt(secureEndpointParts[1]));
                clusterNode.setMaster(false);
                clusterNode.setData(false);
            }
            try {
                String serviceUrl = "service:jmx:rmi:///jndi/rmi://"+karafCellarNode.getHost() + ":"+karafJMXPort+"/karaf-root";
                JMXConnector jmxConnector = getJMXConnector(serviceUrl);
                MBeanServerConnection mbsc = jmxConnector.getMBeanServerConnection();
                final RuntimeMXBean remoteRuntime = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
                clusterNode.setUptime(remoteRuntime.getUptime());
                ObjectName operatingSystemMXBeanName = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
                Double systemCpuLoad = null;
                try {
                    systemCpuLoad = (Double) mbsc.getAttribute(operatingSystemMXBeanName, "SystemCpuLoad");
                } catch (MBeanException e) {
                    logger.error("Error retrieving system CPU load", e);
                } catch (AttributeNotFoundException e) {
                    logger.error("Error retrieving system CPU load", e);
                }
                final OperatingSystemMXBean remoteOperatingSystemMXBean = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
                clusterNode.setLoadAverage(new double[] { remoteOperatingSystemMXBean.getSystemLoadAverage()});
                if (systemCpuLoad != null) {
                    clusterNode.setCpuLoad(systemCpuLoad);
                }

            } catch (MalformedURLException e) {
                logger.error("Error connecting to remote JMX server", e);
            } catch (IOException e) {
                logger.error("Error retrieving remote JMX data", e);
            } catch (MalformedObjectNameException e) {
                logger.error("Error retrieving remote JMX data", e);
            } catch (InstanceNotFoundException e) {
                logger.error("Error retrieving remote JMX data", e);
            } catch (ReflectionException e) {
                logger.error("Error retrieving remote JMX data", e);
            }
            clusterNodes.put(karafCellarNode.getId(), clusterNode);
        }

        return new ArrayList<ClusterNode>(clusterNodes.values());
    }

    @Override
    public void purge(Date date) {
        persistenceService.purge(date);
    }

    @Override
    public void purge(String scope) {
        persistenceService.purge(scope);
    }

    /**
     * Check if a configuration is allowed.
     *
     * @param group the cluster group.
     * @param category the configuration category constant.
     * @param pid the configuration PID.
     * @param type the cluster event type.
     * @return true if the cluster event type is allowed, false else.
     */
    public boolean isClusterConfigPIDAllowed(Group group, String category, String pid, EventType type) {
        CellarSupport support = new CellarSupport();
        support.setClusterManager(this.karafCellarClusterManager);
        support.setGroupManager(this.karafCellarGroupManager);
        support.setConfigurationAdmin(this.osgiConfigurationAdmin);
        return support.isAllowed(group, category, pid, type);
    }

    private JMXConnector getJMXConnector(String url) throws IOException {
        if (jmxConnectors.containsKey(url)) {
            JMXConnector jmxConnector = jmxConnectors.get(url);
            try {
                jmxConnector.getMBeanServerConnection();
                return jmxConnector;
            } catch (IOException e) {
                jmxConnectors.remove(url);
                try {
                    jmxConnector.close();
                } catch (IOException e1) {
                    logger.error("Error closing invalid JMX connection", e1);
                }
                logger.error("Error using the JMX connection to url {}, closed and will reconnect", url, e);
            }
        }
        // if we reach this point either we didn't have a connector or it didn't validate
        // now let's connect to remote JMX service to retrieve information from the runtime and operating system MX beans
        JMXServiceURL jmxServiceURL = new JMXServiceURL(url);
        Map<String,Object> environment=new HashMap<String,Object>();
        if (karafJMXUsername != null && karafJMXPassword != null) {
            environment.put(JMXConnector.CREDENTIALS,new String[]{karafJMXUsername,karafJMXPassword});
        }
        JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxServiceURL, environment);
        jmxConnectors.put(url, jmxConnector);
        return jmxConnector;
    }

}
