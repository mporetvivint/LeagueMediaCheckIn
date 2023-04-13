package com.example.leaguemediacheckin.comm;

import org.nanohttpd.protocols.http.response.Response;

import java.util.List;
import java.util.Map;

public interface WebServerReceiver {
    public Response serverResponse(String uri, Map<String, List<String>> params);
}
