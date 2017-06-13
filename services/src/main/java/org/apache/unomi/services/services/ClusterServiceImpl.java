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
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * Implementation of the persistence service interface
 */
public class ClusterServiceImpl implements ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterServiceImpl.class.getName());

    public static final String KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION = "org.apache.unomi.nodes";
    public static final String KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS = "publicEndpoints";
    public static final String KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS = "secureEndpoints";
    public static final String KARAF_CLUSTER_CONFIGURATION_INTERNAL_ENDPOINTS = "internalEndpoints";

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
    private String internalAddress;
    private String internalPort;

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

    public void setInternalAddress(String internalAddress) {
        this.internalAddress = internalAddress;
    }

    public void setInternalPort(String internalPort) {
        this.internalPort = internalPort;
    }

    public void init() {
        if (karafCellarEventProducer != null && karafCellarClusterManager != null) {

            boolean setupConfigOk = true;
            Group group = karafCellarGroupManager.findGroupByName(karafCellarGroupName);
            if (setupConfigOk && group == null) {
                logger.error("Cluster group " + karafCellarGroupName + " doesn't exist, creating it...");
                group = karafCellarGroupManager.createGroup(karafCellarGroupName);
                if (group != null) {
                    setupConfigOk = true;
                } else {
                    setupConfigOk = false;
                }
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
                Map<String,String> publicEndpoints = getMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
                Map<String,String> secureEndpoints = getMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
                publicEndpoints.put(thisKarafNode.getId(), address + ":" + port);
                secureEndpoints.put(thisKarafNode.getId(), secureAddress + ":" + securePort);
                setMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, publicEndpoints);
                setMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, secureEndpoints);
                configurations.put(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION, karafCellarClusterNodeConfiguration);
                ClusterConfigurationEvent clusterConfigurationEvent = new ClusterConfigurationEvent(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                clusterConfigurationEvent.setSourceGroup(group);
                karafCellarEventProducer.produce(clusterConfigurationEvent);
            }
        }
        logger.info("Cluster service initialized.");
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
        logger.info("Cluster service shutdown.");
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
        Map<String, String> internalNodeEndpoints = new TreeMap<>();
        if (karafCellarClusterNodeConfiguration != null) {
            publicNodeEndpoints = getMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
            secureNodeEndpoints = getMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
            internalNodeEndpoints = getMapProperty(karafCellarClusterNodeConfiguration, KARAF_CLUSTER_CONFIGURATION_INTERNAL_ENDPOINTS, thisKarafNode.getId() + "=" + internalAddress + ":" + internalPort);
        }
        for (org.apache.karaf.cellar.core.Node karafCellarNode : karafCellarNodes) {
            ClusterNode clusterNode = new ClusterNode();
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
            String internalEndpoint = internalNodeEndpoints.get(karafCellarNode.getId());
            if (internalEndpoint != null) {
                String[] internalEndpointParts = internalEndpoint.split(":");
                clusterNode.setInternalHostAddress(internalEndpointParts[0]);
                clusterNode.setInternalPort(Integer.parseInt(internalEndpointParts[1]));
            }
            String serviceUrl = "service:jmx:rmi:///jndi/rmi://"+karafCellarNode.getHost() + ":"+karafJMXPort+"/karaf-root";
            try {
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
            } catch (ConnectException ce) {
                handleTimeouts(serviceUrl, ce);
            } catch (java.rmi.ConnectException ce) {
                handleTimeouts(serviceUrl, ce);
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

    private void handleTimeouts(String serviceUrl, Throwable throwable) {
        if (throwable.getMessage() != null &&
                (throwable.getMessage().contains("timed out") || throwable.getMessage().contains("Network is unreachable"))) {
            logger.warn("RMI Connection timed out or network is unreachable, will reconnect on next request.");
            JMXConnector jmxConnector = jmxConnectors.remove(serviceUrl);
            try {
                if (jmxConnector != null) {
                    jmxConnector.close();
                }
            } catch (Throwable t) {
                // ignore any exception when closing a timed out connection.
            }
        } else {
            logger.error("Error retrieving remote JMX data", throwable);
        }
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
                    logger.warn("Closing invalid JMX connection resulted in :" + e1.getMessage() + ", this is probably ok.");
                    logger.debug("Error closing invalid JMX connection", e1);
                }
                if (e.getMessage() != null && e.getMessage().contains("Connection closed")) {
                    logger.warn("JMX connection to url {} was closed (Cause:{}). Reconnecting...", url, e.getMessage());
                } else {
                    logger.error("Error using the JMX connection to url {}, closed and will reconnect", url, e);
                }
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

    private Map<String,String> getMapProperty(Properties properties, String propertyName, String defaultValue) {
        String propertyValue = properties.getProperty(propertyName, defaultValue);
        return getMapProperty(propertyValue);
    }

    private Map<String, String> getMapProperty(String propertyValue) {
        String[] propertyValueArray = propertyValue.split(",");
        Map<String,String> propertyMapValue = new LinkedHashMap<>();
        for (String propertyValueElement : propertyValueArray) {
            String[] propertyValueElementPrats = propertyValueElement.split("=");
            propertyMapValue.put(propertyValueElementPrats[0], propertyValueElementPrats[1]);
        }
        return propertyMapValue;
    }

    private Map<String,String> setMapProperty(Properties properties, String propertyName, Map<String,String> propertyMapValue) {
        StringBuilder propertyValueBuilder = new StringBuilder();
        int entryCount = 0;
        for (Map.Entry<String,String> propertyMapValueEntry : propertyMapValue.entrySet()) {
            propertyValueBuilder.append(propertyMapValueEntry.getKey());
            propertyValueBuilder.append("=");
            propertyValueBuilder.append(propertyMapValueEntry.getValue());
            if (entryCount < propertyMapValue.size() - 1) {
                propertyValueBuilder.append(",");
            }
        }
        String oldPropertyValue = (String) properties.setProperty(propertyName, propertyValueBuilder.toString());
        if (oldPropertyValue == null) {
            return null;
        }
        return getMapProperty(oldPropertyValue);
    }

}
