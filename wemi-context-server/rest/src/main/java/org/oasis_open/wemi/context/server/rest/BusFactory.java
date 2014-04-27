package org.oasis_open.wemi.context.server.rest;

import javax.enterprise.inject.Produces;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.CXFBusFactory;


public class BusFactory {

    @Produces
    Bus createBus() {
        return new CXFBusFactory().createBus();
    }
}