package org.jahia.modules.wemi;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;

/**
 * A basic Jahia render filter to push a wrapper associated with a mixin type. This relationship is done in the
 * Spring application context descriptor
 */
public class WemiWrapperFilter extends AbstractFilter {

    private String wrapperName;

    public void setWrapperName(String wrapperName) {
        this.wrapperName = wrapperName;
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        if (!resource.hasWrapper(wrapperName)) {
            resource.pushWrapper(wrapperName);
        }
        return null;
    }

}
