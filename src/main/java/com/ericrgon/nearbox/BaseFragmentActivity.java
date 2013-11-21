package com.ericrgon.nearbox;

import android.support.v4.app.FragmentActivity;

import com.ericrgon.nearbox.model.Session;
import com.ericrgon.nearbox.rest.OutboxMailService;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class BaseFragmentActivity extends FragmentActivity {

    private OutboxMailService mailService;

    private static String sessionID = "";

    {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer(OutboxMailService.URL)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade requestFacade) {
                        requestFacade.addQueryParam("sid",sessionID);
                    }
                })
                .build();

        mailService = restAdapter.create(OutboxMailService.class);
    }


    protected OutboxMailService getMailService() {
        return mailService;
    }

    protected void authenticate(String username,String password, final Callback<Session> sessionCallback){
        getMailService().authenticate(username,password,new Callback<Session>() {
            @Override
            public void success(Session session, Response response) {
                sessionID = session.getSid();
                sessionCallback.success(session,response);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                sessionCallback.failure(retrofitError);
            }
        });
    }

}