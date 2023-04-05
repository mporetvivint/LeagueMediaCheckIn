package com.example.leaguemediacheckin.comm;

import static org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse;

import com.example.leaguemediacheckin.MainActivity;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;


public class WebServer extends NanoHTTPD {

    private MainActivity main;

    public WebServer(int port, MainActivity main) {
        super(port);
        this.main = main;
    }

    public WebServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if (uri.equals("/endSession")) {
            main.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    main.setBusy(false);

                }
            });
            return newFixedLengthResponse(Status.OK,MIME_PLAINTEXT,"SessionEnded");
        }
        if (uri.equals("/beginSession")){
            try {
                String name = session.getParameters().get("name").get(0);
                main.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        main.webRequestCallback(name,null);
                    }
                });
            }catch (NullPointerException e){
                e.printStackTrace();
                return newFixedLengthResponse(Status.BAD_REQUEST,MIME_PLAINTEXT,"No Name Found");
            }
            return newFixedLengthResponse(Status.OK,MIME_PLAINTEXT,"SessionStarted");
        }
        return newFixedLengthResponse(Status.BAD_REQUEST,MIME_PLAINTEXT,"Watchu takin' bout??");
    }
}

