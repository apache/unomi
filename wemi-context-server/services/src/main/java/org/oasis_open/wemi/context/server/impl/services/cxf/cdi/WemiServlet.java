package org.oasis_open.wemi.context.server.impl.services.cxf.cdi;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.inject.Inject;

/**
 * Created by loom on 26.04.14.
 */
//@WebServlet(urlPatterns="/wemi")
@OsgiServiceProvider
@Properties({@Property(name="alias", value="/wemi")})
public class WemiServlet extends CXFNonSpringJaxrsServlet {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public WemiServlet() {
        super();
    }

    @Inject
    public void setBus(Bus bus) {
        super.setBus(bus);
    }

}


