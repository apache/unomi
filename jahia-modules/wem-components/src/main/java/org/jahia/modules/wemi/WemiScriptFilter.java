package org.jahia.modules.wemi;

import net.htmlparser.jericho.*;
import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
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
import java.util.Set;
import java.util.TreeSet;

/**
 * This filter will execute all the rendering scripts that correspond to the mixin types set on the current site node if the
 * WEMI context server URL is setup. For example if the site node has a jmix:wemiContextServer mixin type it will
 * execute a wemiContextServer.groovy script
 */
public class WemiScriptFilter extends AbstractFilter implements ApplicationListener<ApplicationEvent> {

    private static Logger logger = LoggerFactory.getLogger(WemiScriptFilter.class);

    private ScriptEngineUtils scriptEngineUtils;

    private String renderingScriptsLocation;

    Set<String> renderingScriptSourceCodes;

    String scriptExtension = "groovy";

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        String out = previousOut;
        JCRSiteNode siteNode = renderContext.getSite();
        String wemiContextServerURL = siteNode.hasProperty("wemiContextServerURL") ? siteNode.getProperty("wemiContextServerURL").getString() : null;
        if (StringUtils.isNotEmpty(wemiContextServerURL)) {
            ExtendedNodeType[] mixins = siteNode.getMixinNodeTypes();
            Set<String> mixinLocalNames = new TreeSet<String>();
            if (mixins != null) {
                for (ExtendedNodeType mixinType : mixins) {
                    mixinLocalNames.add(mixinType.getLocalName());
                }
            }
            Set<String> scripts = getRenderingScriptCodes(mixinLocalNames);
            if (scripts != null && scripts.size() > 0) {
                Source source = new Source(previousOut);
                OutputDocument outputDocument = new OutputDocument(source);
                List<Element> headElementList = source.getAllElements(HTMLElementName.HEAD);
                for (Element element : headElementList) {
                    final EndTag headEndTag = element.getEndTag();
                    StringBuilder scriptOutputs = new StringBuilder();
                    for (String script : scripts) {
                        ScriptEngine scriptEngine = scriptEngineUtils.scriptEngine(scriptExtension);
                        ScriptContext scriptContext = new WemiScriptContext();
                        final Bindings bindings = scriptEngine.createBindings();
                        bindings.put("wemiContextServerURL", wemiContextServerURL);
                        String url = resource.getNode().getUrl();
                        if (renderContext.getRequest().getAttribute("analytics-path") != null) {
                            url = (String) renderContext.getRequest().getAttribute("analytics-path");
                        }
                        bindings.put("resourceUrl", url);
                        bindings.put("resource", resource);
                        bindings.put("renderContext", resource);
                        bindings.put("gaMap", renderContext.getRequest().getAttribute("gaMap"));
                        scriptContext.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
                        // The following binding is necessary for Javascript, which doesn't offer a console by default.
                        bindings.put("out", new PrintWriter(scriptContext.getWriter()));
                        scriptEngine.eval(script, scriptContext);
                        StringWriter writer = (StringWriter) scriptContext.getWriter();
                        final String scriptOutput = writer.toString();
                        scriptOutputs.append(scriptOutput);
                    }
                    final String allScriptOutputs = scriptOutputs.toString();
                    if (StringUtils.isNotBlank(allScriptOutputs)) {
                        outputDocument.replace(headEndTag.getBegin(), headEndTag.getBegin() + 1,
                                "\n" + AggregateCacheFilter.removeEsiTags(allScriptOutputs) + "\n<");
                    }
                    break; // avoid to loop if for any reasons multiple body in the page
                }
                out = outputDocument.toString().trim();
            }
        }

        return out;
    }

    protected Set<String> getRenderingScriptCodes(Set<String> mixinLocaleNames) throws IOException {
        if (renderingScriptSourceCodes == null) {
            Set<String> renderingScriptSourceCodes = new TreeSet<String>();
            for (String mixinLocaleName : mixinLocaleNames) {
                String renderingScriptSourceCode = WebUtils.getResourceAsString(renderingScriptsLocation + "/" + mixinLocaleName + ".groovy");
                if (renderingScriptSourceCode != null) {
                    renderingScriptSourceCodes.add(renderingScriptSourceCode);
                }
            }
        }
        return renderingScriptSourceCodes;
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof JahiaTemplateManagerService.TemplatePackageRedeployedEvent) {
            renderingScriptSourceCodes = null;
        }
    }

    public void setScriptEngineUtils(ScriptEngineUtils scriptEngineUtils) {
        this.scriptEngineUtils = scriptEngineUtils;
    }

    public void setRenderingScriptsLocation(String renderingScriptsLocation) {
        this.renderingScriptsLocation = renderingScriptsLocation;
    }

    public void setScriptExtension(String scriptExtension) {
        this.scriptExtension = scriptExtension;
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
