package com.wire.bots.broadcast.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.broadcast.Executor;
import com.wire.bots.broadcast.model.Config;
import com.wire.bots.broadcast.model.GitHubPullRequest;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.WireClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Path("/github")
public class GitHubResource {
    private final Config conf;
    private final Executor exec;
    private ClientRepo repo;

    public GitHubResource(ClientRepo repo, Config conf) {
        this.repo = repo;
        this.conf = conf;
        this.exec = new Executor(repo, conf);
    }

    @POST
    @Path("/{botId}")
    public Response broadcast(@PathParam("botId") String botId,
                              @HeaderParam("X-GitHub-Event") String event,
                              @HeaderParam("X-Hub-Signature") String signature,
                              @HeaderParam("X-GitHub-Delivery") String delivery,
                              String payload) throws Exception {

        Logger.info("Event: %s, Signature: %s, Delivery: %s", event, signature, delivery);

        String challenge = getSha(payload);
        if (!challenge.equals(signature)) {
            Logger.warning("Invalid sha");
            return Response.
                    status(403).
                    build();
        }

        ObjectMapper mapper = new ObjectMapper();

        switch (event) {
            case "pull_request": {
                GitHubPullRequest gitHubPullRequest = mapper.readValue(payload, GitHubPullRequest.class);
                exec.broadcastUrl(gitHubPullRequest.pr.url, botId);
                break;
            }
        }

        return Response.
                ok().
                build();
    }

    private String getSha(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec(conf.getAppSecret().getBytes(Charset.forName("UTF-8")), "HmacSHA1"));
        byte[] bytes = hmac.doFinal(payload.getBytes(Charset.forName("UTF-8")));
        return String.format("sha1=%040x", new BigInteger(1, bytes));
    }
}
