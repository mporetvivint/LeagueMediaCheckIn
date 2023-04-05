package com.example.leaguemediacheckin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.leaguemediacheckin.comm.WebRequest;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

class MDNSListener extends AsyncTask<Void,Void,String> implements ServiceListener, WebRequestReceiver{

    private BarcodeScanningActivity activity;

    public MDNSListener(BarcodeScanningActivity activity) {
        Log.d("MDNS","MDNS Started");
        this.activity = activity;
        this.execute();
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.d("MDNS","Service added: " + event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        Log.d("MDNS","Service removed: " + event.getInfo());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        Log.d("MDNS","Service resolved: " + event.getInfo());
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(event.getInfo().getName().equals("lmarecorder")) {
            WebRequest request = new WebRequest("http://" + event.getInfo().getHostAddress() + ":" +
                    event.getInfo().getPort() + "/name",null,(Context) activity, this);
            request.execute();
        }
    }

    @Override
    public void webRequestCallback(String response, String address) {
        if(!response.equals("fail")) {
            try {
                URL url = new URL(address);
                ServerObject serverObject = new ServerObject(response, url.getHost(), url.getPort());
                activity.mdnsCallback(serverObject);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPostExecute(String s) {

    }

    @Override
    protected String doInBackground(Void... addClientActivities) {
        try {
            // Create a JmDNS instance
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

            // Add a service listener
            jmdns.addServiceListener("_http._tcp.local.", this);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "script has been updated successfully";
    }

}
