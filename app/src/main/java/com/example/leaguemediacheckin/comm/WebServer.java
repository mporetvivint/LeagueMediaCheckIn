package com.example.leaguemediacheckin.comm;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.leaguemediacheckin.MainActivity;

import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

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
            return newFixedLengthResponse(Response.Status.OK,MIME_PLAINTEXT,"SessionEnded");
        }
        if (uri.equals("/beginSession")){
            try {
                String name = session.getParameters().get("name").get(0);
                main.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        main.searchRepCallback(name);
                    }
                });
            }catch (NullPointerException e){
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.BAD_REQUEST,MIME_PLAINTEXT,"No Name Found");
            }
            return newFixedLengthResponse(Response.Status.OK,MIME_PLAINTEXT,"SessionStarted");
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST,MIME_PLAINTEXT,"Watchu takin' bout??");
    }
}

