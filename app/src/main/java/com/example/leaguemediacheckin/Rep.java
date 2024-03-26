package com.example.leaguemediacheckin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

public class Rep {

    private String name;
    private String badgeID;
    private String email;
    private String type;
    private ArrayList<String> additions; //List of additions this rep has on their pass
    private int entrance; //how many times rep has entered venue

    public Rep(String firstName, String badgeID, String email, String type, int entrance, ArrayList<String> additions) {
        this.name = firstName;
        this.badgeID = badgeID;
        this.email = email;
        this.type = type;
        this.entrance = entrance;
        this.additions = Objects.requireNonNullElseGet(additions, ArrayList::new);
    }

    public Rep() {
        this.name = "";
        this.badgeID = "";
        this.email = "";
        this.entrance = 0;
        additions = new ArrayList<>();
    }

    public Rep(String json){//constructor to parse jason with info
        JSONObject jsonObject = JSON.parseObject(json);
        this.name = jsonObject.getString("name");
        this.badgeID = jsonObject.getString("badgeID");;
        this.email = jsonObject.getString("email");
        this.type = jsonObject.getString("type");
        this.entrance = jsonObject.getInteger("entrance");
        this.additions = (ArrayList<String>) deserialize(jsonObject.getBytes("additions"));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBadgeID() {
        return badgeID;
    }

    public void setBadgeID(String badgeID) {
        this.badgeID = badgeID;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getEntrance() {
        return entrance;
    }

    public void setEntrance(int entrance) {
        this.entrance = entrance;
    }

    public void incrementEntrance() {
        entrance++;
    }

    public ArrayList<String> getAdditions() {
        return additions;
    }

    public void addAddition(String addition){
        additions.add(addition);
    }

    public void removeAddition(String addition){
        additions.remove(addition);
    }

    public String getvCard(){
        return "BEGIN:VCARD\n" +
                "VERSION:2.1\n" +
                "FN:"+ name +"\n"+
                "EMAIL;WORK:"+email+"\n"+
                "END:VCARD";
    }

    @Override
    public String toString(){
        return name +" - " + email;
    }

    public String toJSON(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", name);
        jsonObject.put("email", email);
        jsonObject.put("badgeID", badgeID);
        jsonObject.put("entrance", entrance);
        jsonObject.put("type",type);
        jsonObject.put("additions",serialize(additions));
        return jsonObject.toString();

    }

    private static byte[] serialize(Object object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                bos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Helper method to deserialize a byte array to an object
    private static Object deserialize(byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(bis);
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                bis.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}

