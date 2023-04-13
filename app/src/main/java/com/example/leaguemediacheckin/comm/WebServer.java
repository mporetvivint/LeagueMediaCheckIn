package com.example.leaguemediacheckin.comm;

import static org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;


public class WebServer extends NanoHTTPD {

    private WebServerReceiver receiver;

    public WebServer(int port, WebServerReceiver receiver) {
        super(port);
        this.receiver = receiver;
    }

    public WebServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        return receiver.serverResponse(uri,session.getParameters());
    }
}

