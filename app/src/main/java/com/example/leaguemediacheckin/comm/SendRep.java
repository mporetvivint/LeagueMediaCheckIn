package com.example.leaguemediacheckin.comm;
import android.content.Context;
import android.os.AsyncTask;

import android.util.Log;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.leaguemediacheckin.MainActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static android.content.Context.WIFI_SERVICE;

public class SendRep extends AsyncTask<Void,Void,String> {

    String ip_address;
    String badge_uid;

    private OnEventListener<String> callBack;
    private Context context;

    public SendRep(String ip_address, String badge_uid, OnEventListener callBack, Context context) {
        this.ip_address = ip_address;
        this.badge_uid = badge_uid;
        this.callBack = callBack;
        this.context = context;
    }

    @Override
    protected void onPostExecute(String s) {
        if(callBack != null){
            callBack.onSuccess(s);
        }
    }

    @Override
    protected String doInBackground(Void... addClientActivities) {
        //Set up request
        StringRequest request = createRequest();
        RequestQueue queue = Volley.newRequestQueue(context);
        queue.add(request);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "script has been updated successfully";
    }

    private StringRequest createRequest(){

        //fix strings
        badge_uid = badge_uid.trim();


        try {
            badge_uid = URLEncoder.encode(badge_uid, String.valueOf(StandardCharsets.UTF_8));

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String scriptURL = ip_address + "/searchrep?uid=" + badge_uid;


        return new StringRequest(Request.Method.GET, scriptURL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        callBack.onSuccess(response.toString());
                        MainActivity activity = (MainActivity) context;
                        activity.searchRepCallback(response);
//                        RepInfoActivity activity = (RepInfoActivity) context;
//                        activity.makeToast("You're all Checked in!");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                MainActivity activity = (MainActivity) context;
                activity.searchRepCallback("fail");
            }
        });
    }
}
