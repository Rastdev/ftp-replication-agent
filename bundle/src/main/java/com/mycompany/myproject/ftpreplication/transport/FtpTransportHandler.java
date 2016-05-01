package com.mycompany.myproject.ftpreplication.transport;

import com.day.cq.replication.*;
import com.day.jcr.vault.util.Text;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Component(metatype=false)
@Service({TransportHandler.class})
public class FtpTransportHandler implements TransportHandler {

    private static final String TRANSPORT_SCHEME = "ftp";
    private final Logger logger = LoggerFactory.getLogger(FtpTransportHandler.class);

    @Activate
    private void activate(ComponentContext context){
        this.logger.info("Ftp Transport Handler started.");
    }

    @Deactivate
    private void deactivate(ComponentContext context){
        this.logger.info("Ftp Transport Handler stopped.");
    }

    public boolean canHandle(AgentConfig config){
        if (config != null){
            String uri = config.getTransportURI();
            return StringUtils.isNotEmpty(uri) && uri.startsWith(TRANSPORT_SCHEME);
        }
        return false;
    }

    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx)
            throws ReplicationException {

        if ((tx.getAction().getType() == ReplicationActionType.DELETE) || (tx.getAction().getType() == ReplicationActionType.DEACTIVATE)) {
            throw new ReplicationException("Delete and deactivate operations aren't supported by this transport handler.");
        }

        if ((tx.getAction().getType() == ReplicationActionType.INTERNAL_POLL) || (tx.getAction().getType() == ReplicationActionType.REVERSE)) {
            throw new ReplicationException("Reverse replication not supported by this transport handler.");
        }

        ReplicationLog log = tx.getLog();

        AgentConfig config = ctx.getConfig();
        String transportUri = config.getTransportURI();
        String user = config.getTransportUser();
        String pass = config.getTransportPassword();

        URI uri;
        try {
            uri = new URI(transportUri);
        } catch (URISyntaxException e){
            String msg = String.format("Invalid Transport URI %s: %s", transportUri, e.getMessage());
            log.error(msg);
            return new ReplicationResult(false, 0, msg);
        }

        if (!TRANSPORT_SCHEME.equals(uri.getScheme())){
            String msg = String.format("Unexpected scheme in Transport URI %s: should be %s", transportUri, TRANSPORT_SCHEME);
            log.error(msg);
            return new ReplicationResult(false, 0, msg);
        }

        String host = uri.getHost();
        int port = uri.getPort();

        ReplicationContent content = tx.getContent();

        if (content == ReplicationContent.VOID) {
            logger.info("Nothing to replicate for " + tx.getAction().getPath());
            return new ReplicationResult(true, 0, "Done");
        }

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, port);
            if (StringUtils.isNotEmpty(user)){
                pass = pass != null ? pass : StringUtils.EMPTY;
                boolean isAuth = ftpClient.login(user, pass);
                if (!isAuth)
                    throw new ReplicationException("Failed to authentificate to FTP server");
            }
            ftpClient.enterLocalPassiveMode();
            InputStream inputStream = content.getInputStream();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            String filePath = tx.getAction().getPath();
            String fileName = Text.getName(filePath);
            String remoteFile = fileName + "_" + TimeStamp.getCurrentTime().getTime() + ".html";
            boolean done = ftpClient.storeFile(remoteFile, inputStream);
            inputStream.close();
            if (!done)
                throw new ReplicationException("Failed to store replicated content to choosen FTP server");
        } catch (IOException ex) {
            log.error("Error during ftp replicating of content: " + ex.getMessage());
            throw new ReplicationException("Failed to replicate content to choosen FTP server");
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                logger.error("Error during ftp client disconnect action: " + ex.getMessage());
            }
        }
        return ReplicationResult.OK;
    }

    public ReplicationResult poll(TransportContext ctx, ReplicationTransaction tx, List<ReplicationContent> result, ReplicationContentFactory factory)
            throws ReplicationException {
        throw new ReplicationException("Not implemented");
    }
}
