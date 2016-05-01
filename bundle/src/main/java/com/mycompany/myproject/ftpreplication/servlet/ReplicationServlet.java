package com.mycompany.myproject.ftpreplication.servlet;

import com.day.cq.replication.*;
import com.mycompany.myproject.ftpreplication.IdAgentFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Component(metatype=false)
@Service({Servlet.class})
@Property(name="sling.servlet.paths", value={"/bin/ftpReplicate"})
public class ReplicationServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 5341302512950271559L;
    private static final String PATH_PARAMETER = "path";
    private static final String ACTION_PARAMETER = "cmd";
    private static final String VERSION_PARAMETER = "version";
    private static final String AGENT_ID_PARAMETER = "agentId";

    private final transient Logger logger = LoggerFactory.getLogger(ReplicationServlet.class);

    @Reference
    private transient Replicator replicator;
    @Reference
    private transient EventAdmin eventAdmin;

    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String[] paths = request.getParameterValues(PATH_PARAMETER);
        if ((paths == null) || (paths.length == 0)) {
            writeStatus(response, "Error: path parameter is missing", 400);
            return;
        }

        String actionParam = request.getParameter(ACTION_PARAMETER);
        ReplicationActionType action = ReplicationActionType.fromName(actionParam);
        if (action == null){
            writeStatus(response, "Error: cmd contains unknown value: " + actionParam, 400);
            return;
        }

        Replicator localReplicator = this.replicator;
        if (localReplicator == null){
            writeStatus(response, "Error: Replicator service is not available", 400);
            return;
        }

        if (isJson(request)) {
            response.setContentType("application/json");
        } else {
            response.setContentType("text/plain");
        }

        String agentId = request.getParameter(AGENT_ID_PARAMETER);
        if (StringUtils.isEmpty(agentId)){
            writeStatus(response, "Error: Replication agent isn't choosen", 400);
            return;
        }
        AgentFilter filter = new IdAgentFilter(agentId);
        ReplicationOptions opts = new ReplicationOptions();
        opts.setRevision(request.getParameter(VERSION_PARAMETER));
        opts.setFilter(filter);

        String[] msgs = new String[paths.length];
        Session session = request.getResourceResolver().adaptTo(Session.class);
        int status = 200;
        int index = 0;
        for (String path : paths){
            try{
                localReplicator.replicate(session, action, path, opts);
                msgs[index] = ("Replication started for " + path);
            } catch (PathNotFoundException e){
                msgs[index] = ("Error: Path not found: " + path);
                if (status == 200) {
                    status = 404;
                }
            } catch (AccessDeniedException e){
                this.logger.debug(request.getRemoteUser() + " is not allowed to replicate " + "this resource " + path + ". Issuing request for 'replication");
                msgs[index] = ("Error: No rights to replicate " + path);
                if (status == 200) {
                    status = 403;
                }
            } catch (ReplicationException e){
                this.logger.error("Error during replication: " + e.getMessage(), e);
                msgs[index] = ("Error: " + e.getLocalizedMessage() + " for path " + path);
                if (status == 200) {
                    status = 400;
                }
            } catch (Throwable e){
                this.logger.error("Error during replication: " + e.getMessage(), e);
                msgs[index] = ("Error: " + e.getLocalizedMessage() + " for path " + path);
                if (status == 200) {
                    status = 400;
                }
            }
            index++;
        }
        StringBuilder msg = new StringBuilder();
        for (String m : msgs) {
            msg.append(m).append("\n");
        }
        writeStatus(response, msg.toString(), status);
    }

    private boolean isJson(SlingHttpServletRequest request)
    {
        return "json".equals(request.getRequestPathInfo().getExtension());
    }

    private void writeStatus(HttpServletResponse response, String message, int status)
            throws IOException {
        response.setStatus(status);
        PrintWriter writer = response.getWriter();
        writer.print(message);
    }
}

