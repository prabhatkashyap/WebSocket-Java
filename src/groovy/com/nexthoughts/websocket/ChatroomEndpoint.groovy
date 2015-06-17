package com.nexthoughts.websocket

import grails.util.Environment
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.springframework.context.ApplicationContext

import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.annotation.WebListener
import javax.websocket.*
import javax.websocket.server.ServerContainer
import javax.websocket.server.ServerEndpoint

/**
 * Created by hitenpratap on 12/5/15.
 */
@ServerEndpoint("/chatroom")
@WebListener
class ChatroomEndpoint implements ServletContextListener {

    private static final Logger log = Logger.getLogger(ChatroomEndpoint.class)
    private static final Set<Session> users = ([] as Set).asSynchronized()

    @Override
    void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext servletContext = servletContextEvent.servletContext
        ServerContainer serverContainer = servletContext.getAttribute("javax.websocket.server.ServerContainer")

        try {
            // This is necessary for Grails to add the endpoint in development.
            // In production, the endpoint will be added by the @ServerEndpoint
            // annotation.
            if (Environment.current == Environment.DEVELOPMENT) {
                serverContainer.addEndpoint(ChatroomEndpoint)
            }

            // This is mainly for demonstration of retrieving the ApplicationContext,
            // the GrailsApplication instance, and application configuration.
            ApplicationContext ctx = (ApplicationContext) servletContext.getAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT)
            GrailsApplication grailsApplication = ctx.grailsApplication
            serverContainer.defaultMaxSessionIdleTimeout = grailsApplication.config.servlet.defaultMaxSessionIdleTimeout ?: 0
        } catch (IOException e) {
            log.error(e.message, e)
        }
    }

    @Override
    void contextDestroyed(ServletContextEvent servletContextEvent) {
    }

    @OnOpen
    public void onOpen(Session userSession) {
        users.add(userSession)
    }

    @OnMessage
    public void onMessage(String message, Session userSession) {
        String username = userSession.userProperties.get("username")

        if (!username) {
            userSession.userProperties.put("username", message)
            sendMessage(String.format("%s has joined the chatroom.", message))
            return
        }

        // Send the message to all users in the chatroom.
        sendMessage(message, userSession)
    }

    @OnClose
    public void onClose(Session userSession, CloseReason closeReason) {
        String username = userSession.userProperties.get("username")
        users.remove(userSession)
        userSession.close()

        if (username) {
            sendMessage(String.format("%s has left the chatroom.", username))
        }
    }

    @OnError
    public void onError(Throwable t) {
        log.error(t.message, t)
    }

    private void sendMessage(String message, Session userSession = null) {
        if (userSession) {
            message = String.format(
                    "%s: %s", userSession.userProperties.get("username"), message)
        }
        Iterator<Session> iterator = users.iterator()
        while (iterator.hasNext()) {
            iterator.next().basicRemote.sendText(message)
        }
    }

}
