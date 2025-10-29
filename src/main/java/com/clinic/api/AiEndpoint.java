package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.clinic.application.ai.ChatAgent;
import com.clinic.application.ai.PriorityAgent;

import java.util.UUID;

@HttpEndpoint("ai")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AiEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;
    public AiEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }
    @Put("/ask")
    public String urgency(String issue){
        var session = UUID.randomUUID().toString();
        return componentClient
                .forAgent()
                .inSession(session)
                .method(PriorityAgent::urgency)
                .invoke(issue);
    }

    @Put("/chat")
    public String chat(String issue){
        var session = requestContext().queryParams().getString("session").orElse(UUID.randomUUID().toString());
        return componentClient
                .forAgent()
                .inSession(session)
                .method(ChatAgent::ask)
                .invoke(issue);
    }

}
