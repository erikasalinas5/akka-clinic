package com.clinic.application.ai;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id ="chat-agent")
public class ChatAgent extends Agent {
    private static final String SYSTEM_MESSAGE = """
            You are a chat bot""".stripIndent();

    public Effect<String> ask(String issue){
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(issue)
                .thenReply();
    }

}
