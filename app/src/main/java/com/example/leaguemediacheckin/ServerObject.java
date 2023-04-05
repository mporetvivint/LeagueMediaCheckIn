package com.example.leaguemediacheckin;

import androidx.annotation.NonNull;

class ServerObject{
    String name;
    String address;
    int port;
    public ServerObject(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getFullAddress(){
        return "http://" + address + ":" + port;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
