package org.oasis_open.wemi.context.server.rest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;

public class EndPointPublisher {

    @Inject @Any @CXFEndPoint
    Instance<Object> services;

    @Inject Bus bus;

    List<Server> servers = new ArrayList<Server>();

    public EndPointPublisher() {
        System.out.println("Creating end point publisher");
    }

    @PostConstruct
    public void initService() {
        Iterator<Object> it = services.iterator();
        System.out.println("Creating CXF Endpoints for " + services.isUnsatisfied() );
        while (it.hasNext()) {
            Object service = it.next();
            System.out.println("Creating CXF Endpoint for " + service.getClass().getName());
            CXFEndPoint cxfEndpoint = service.getClass().getAnnotation(CXFEndPoint.class);
            Class<?> iface = service.getClass().getInterfaces()[0];
            /*
            JaxWsServerFactoryBean jaxWsServerFactoryBean = new JaxWsServerFactoryBean();
            jaxWsServerFactoryBean.setBus(bus);
            jaxWsServerFactoryBean.setServiceClass(iface);
            jaxWsServerFactoryBean.setAddress(cxfEndpoint.url());
            jaxWsServerFactoryBean.setServiceBean(service);
            servers.add(jaxWsServerFactoryBean.create());
            */
            JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
            jaxrsServerFactoryBean.setBus(bus);
            jaxrsServerFactoryBean.setServiceClass(iface);
            jaxrsServerFactoryBean.setAddress(cxfEndpoint.url());
            jaxrsServerFactoryBean.setServiceBean(service);
            servers.add(jaxrsServerFactoryBean.create());
        }
    }

    @PreDestroy
    public void destroy() {
        for (Server server : servers) {
            server.destroy();
        }
    }

}