package org.jahia.modules.wemi;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.slf4j.Logger;

import javax.jcr.Property;
import javax.jcr.Value;

/**
 * This filter will setup a wrapper on the rendered node to render based on the selected segments
 */
public class WemSegmentFilter extends AbstractFilter {

    protected transient static Logger logger = org.slf4j.LoggerFactory.getLogger(WemSegmentFilter.class);

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        if (resource.getNode().isNodeType("jmix:wemSegmentFilter")) {
            // we have a mixin applied, let's test if we must exclude it from the current channel.
            if (!resource.getNode().hasProperty("j:segments")) {
                logger.warn("Segment filtering activated on resource "+resource.getPath()+" but no segments selected, please select segments for this function to be activated properly.");
                return null;
            }
            Property segmentsProperty = resource.getNode().getProperty("j:segments");
            String includeOrExclude = resource.getNode().hasProperty("j:segmentsIncludeOrExclude") ? resource.getNode().getProperty("j:segmentsIncludeOrExclude").getString() : "exclude";
            Value[] segmentsPropertyValues = segmentsProperty.getValues();
            StringBuilder segmentListBuilder = new StringBuilder();
            for (Value segmentPropertyValue : segmentsPropertyValues) {
                if (segmentPropertyValue.getString() != null) {
                    segmentListBuilder.append(segmentPropertyValue.getString());
                    segmentListBuilder.append(",");
                }
            }
            String segmentList = segmentListBuilder.toString();
            if (segmentList.endsWith(",")) {
                segmentList = segmentList.substring(0, segmentList.length()-1);
            }
            if (includeOrExclude.equals("exclude")) {
                return getExcludedResult(resource, renderContext);
            }
            if (includeOrExclude.equals("include")) {
                return getIncludedResult(resource, renderContext, segmentList);
            }
        }
        return null;
    }

    public String getIncludedResult(Resource resource, RenderContext content, String segmentList) {
        if (!resource.hasWrapper("segmentWrapper")) {
            resource.pushWrapper("segmentWrapper");
        }
        resource.getModuleParams().put("segmentList", segmentList);
        return null;
    }

    public String getExcludedResult(Resource resource, RenderContext context) {
        if (!context.isEditMode() || (context.getChannel() != null && !context.getChannel().getIdentifier().equals("generic"))) {
            return "";
        }
        if (!resource.hasWrapper("unselectedSegment")) {
            resource.pushWrapper("unselectedSegment");
        }
        return null;
    }

}
