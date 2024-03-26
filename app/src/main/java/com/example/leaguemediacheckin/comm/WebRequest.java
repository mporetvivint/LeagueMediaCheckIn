package com.example.leaguemediacheckin.comm;
import android.content.Context;
import android.media.MediaDrm;
import android.os.AsyncTask;
import android.util.Log;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.leaguemediacheckin.MainActivity;
import com.example.leaguemediacheckin.Rep;
import com.example.leaguemediacheckin.WebRequestReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebRequest extends AsyncTask<Void,Void,String> {

    String ip_address;
    String badge_uid;
    boolean gateEntrance;
    Rep addedRep;
    boolean isAddRepActivity;


    private OnEventListener<String> callBack;
    private Context context;
    private WebRequestReceiver webRequestReceiver;

    //Constructor for sending rep
    public WebRequest(String ip_address, String badge_uid, OnEventListener callBack, Context context) {
        this.ip_address = ip_address;
        this.badge_uid = badge_uid;
        this.callBack = callBack;
        this.context = context;
        this.webRequestReceiver = (WebRequestReceiver) context;
        isAddRepActivity = false;
    }

    public WebRequest(String request_url, OnEventListener callBack, Context context, WebRequestReceiver requestReceiver){
        this.ip_address = request_url;
        this.badge_uid = null;
        this.callBack = callBack;
        this.context = context;
        this.webRequestReceiver = requestReceiver;
        isAddRepActivity = false;
    }

    public WebRequest(String request_url, String badge_uid, Context context, WebRequestReceiver requestReceiver,boolean gateEntrance){
        this.ip_address = request_url;
        this.badge_uid = badge_uid;
        this.callBack = null;
        this.context = context;
        this.webRequestReceiver = requestReceiver;
        this.gateEntrance = gateEntrance;
        isAddRepActivity = false;
    }


    //Constructor for adding new rep
    public WebRequest(String ip_address,Rep addedRep,OnEventListener callBack,Context context,boolean addrep){
        this.ip_address = ip_address;
        this.addedRep = addedRep;
        this.callBack = callBack;
        this.context = context;
        isAddRepActivity = addrep;
    }

    @Override
    protected void onPostExecute(String s) {
        if(callBack != null && !isAddRepActivity){
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

        String URL;
        if(addedRep != null){
            URL = ip_address + "/addrep?fn="+URLEncoder.encode(addedRep.getName())+"&em="+URLEncoder.encode(addedRep.getEmail())
            +"&uid="+URLEncoder.encode(addedRep.getEmail())+"&entrance=0";

        }else if(badge_uid != null) {
            try {
                //fix strings
                badge_uid = badge_uid.trim();
                badge_uid = URLEncoder.encode(badge_uid, String.valueOf(StandardCharsets.UTF_8));

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            URL = ip_address + "/searchrep?uid=" + badge_uid;
        }else{
            URL = ip_address;
        }

        return new StringRequest(Request.Method.GET, URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if(callBack != null) {
                            callBack.onSuccess(response);
                        }if(webRequestReceiver!=null) {
                            webRequestReceiver.webRequestCallback(response, URL);
//                        RepInfoActivity activity = (RepInfoActivity) context;
//                        activity.makeToast("You're all Checked in!");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if(callBack != null){
                    callBack.onFail();
                }
                if(webRequestReceiver!=null) {
                    webRequestReceiver.webRequestCallback("fail", null);
                }
            }
        });
    }
}
