package com.example.leaguemediacheckin.comm;

public interface OnEventListener<T> {
    public void onSuccess(T object);
    public void onFail();
}
