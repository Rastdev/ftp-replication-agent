package com.mycompany.myproject.ftpreplication.content;

import com.day.cq.replication.*;
import com.mycompany.myproject.ftpreplication.content.mock.MockHttpRequest;
import com.mycompany.myproject.ftpreplication.content.mock.MockHttpResponse;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

@Component(name="com.mycompany.myproject.ftpreplication.content.HTMLContentBuilder",
        metatype=true, label="%html.builder.name", description="Transforms replicated data to HTML output")
@Property(name = "name", value = "html")
@Service({ContentBuilder.class})
public class HTMLContentBuilder implements ContentBuilder {

    private static final String CONTENT_TYPE = "application/html";
    private static final String HTML_EXTENTION = ".infinity.html";
    private static final String CONTENT_BUILDER_NAME = "html";
    private static final String CONTENT_BUILDER_TITLE = "HTML Content Builder";
    private final Logger logger = LoggerFactory.getLogger(HTMLContentBuilder.class);

    @Reference
    private SlingRequestProcessor sling;

    @Reference
    private ResourceResolverFactory resolverFactory;

    public ReplicationContent create(Session session, ReplicationAction action, ReplicationContentFactory contentFactory)
            throws ReplicationException {
        return create(session, action, contentFactory, null);
    }

    public ReplicationContent create(Session session, ReplicationAction action, ReplicationContentFactory contentFactory, Map<String, Object> parameters)
            throws ReplicationException {
        if (ReplicationActionType.ACTIVATE.equals(action.getType())
                || ReplicationActionType.TEST.equals(action.getType())) {
            try {
                // get HTML view of node data
                Map<String,Object> prop = new HashMap<String,Object>();
                prop.put("user.jcr.session", session);
                ResourceResolver resolver = resolverFactory.getResourceResolver(prop);
                MockHttpResponse response = new MockHttpResponse();
                sling.processRequest(
                        new MockHttpRequest(action.getPath() + HTML_EXTENTION),
                        response, resolver);
                response.getWriter().close();
                return contentFactory.create(CONTENT_TYPE, response.getOutputFile(), true);
            } catch (Exception e) {
                logger.error("Process of content building failed : " + e.getMessage());
                throw new ReplicationException(e);
            }
        }
        return ReplicationContent.VOID;
    }

    public String getName(){
        return CONTENT_BUILDER_NAME;
    }

    public String getTitle(){
        return CONTENT_BUILDER_TITLE;
    }
}

