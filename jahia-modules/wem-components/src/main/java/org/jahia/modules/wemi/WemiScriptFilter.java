package org.jahia.modules.wemi;

import net.htmlparser.jericho.*;
import org.apache.commons.lang.StringUtils;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.cache.AggregateCacheFilter;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.jahia.utils.ScriptEngineUtils;
import org.jahia.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleScriptContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

/**
 * Created by loom on 31.05.14.
 */
public class WemiScriptFilter extends AbstractFilter implements ApplicationListener<ApplicationEvent> {

    private static Logger logger = LoggerFactory.getLogger(WemiScriptFilter.class);

    private ScriptEngineUtils scriptEngineUtils;

    private String renderingScriptLocation;

    private String renderingScriptCode;

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        String out = previousOut;
        String wemiContextServerURL = renderContext.getSite().hasProperty("wemiContextServerURL") ? renderContext.getSite().getProperty("wemiContextServerURL").getString() : null;
        if (StringUtils.isNotEmpty(wemiContextServerURL)) {
            String script = getRenderingScriptCode();
            if (script != null) {
                Source source = new Source(previousOut);
                OutputDocument outputDocument = new OutputDocument(source);
                List<Element> headElementList = source.getAllElements(HTMLElementName.HEAD);
                for (Element element : headElementList) {
                    final EndTag headEndTag = element.getEndTag();
                    String extension = StringUtils.substringAfterLast(renderingScriptLocation, ".");
                    ScriptEngine scriptEngine = scriptEngineUtils.scriptEngine(extension);
                    ScriptContext scriptContext = new WemiScriptContext();
                    final Bindings bindings = scriptEngine.createBindings();
                    bindings.put("wemiContextServerURL", wemiContextServerURL);
                    String url = resource.getNode().getUrl();
                    if (renderContext.getRequest().getAttribute("analytics-path") != null) {
                        url = (String) renderContext.getRequest().getAttribute("analytics-path");
                    }
                    bindings.put("resourceUrl", url);
                    bindings.put("resource", resource);
                    bindings.put("gaMap",renderContext.getRequest().getAttribute("gaMap"));
                    scriptContext.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
                    // The following binding is necessary for Javascript, which doesn't offer a console by default.
                    bindings.put("out", new PrintWriter(scriptContext.getWriter()));
                    scriptEngine.eval(script, scriptContext);
                    StringWriter writer = (StringWriter) scriptContext.getWriter();
                    final String googleAnalyticsScript = writer.toString();
                    if (StringUtils.isNotBlank(googleAnalyticsScript)) {
                        outputDocument.replace(headEndTag.getBegin(), headEndTag.getBegin() + 1,
                                "\n" + AggregateCacheFilter.removeEsiTags(googleAnalyticsScript) + "\n<");
                    }
                    break; // avoid to loop if for any reasons multiple body in the page
                }
                out = outputDocument.toString().trim();
            }
        }

        return out;
    }

    protected String getRenderingScriptCode() throws IOException {
        if (renderingScriptCode == null) {
            renderingScriptCode = WebUtils.getResourceAsString(renderingScriptLocation);
            if (renderingScriptCode == null) {
                logger.warn("Unable to lookup template at {}", renderingScriptLocation);
            }
        }
        return renderingScriptCode;
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof JahiaTemplateManagerService.TemplatePackageRedeployedEvent) {
            renderingScriptCode = null;
        }
    }

    public void setScriptEngineUtils(ScriptEngineUtils scriptEngineUtils) {
        this.scriptEngineUtils = scriptEngineUtils;
    }
    public void setRenderingScriptLocation(String renderingScriptLocation) {
        this.renderingScriptLocation = renderingScriptLocation;
    }

    class WemiScriptContext extends SimpleScriptContext {
        private Writer writer = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public Writer getWriter() {
            if (writer == null) {
                writer = new StringWriter();
            }
            return writer;
        }
    }
}
