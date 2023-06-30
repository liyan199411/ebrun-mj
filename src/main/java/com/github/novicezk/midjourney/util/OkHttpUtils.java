package com.github.novicezk.midjourney.util;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author ：xuyangyang
 * @date ：Created in 2020/1/14 9:08
 * @description：封装 OkHttp 工具类
 * @modified By：
 * @version:
 */
@Slf4j
public class OkHttpUtils {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final byte[] LOCKER = new byte[0];
    private static OkHttpUtils instance;
    private OkHttpClient okHttpClient;

    private OkHttpUtils() {
        okHttpClient = buildOKHttpClient()
                //10秒连接超时
                .connectTimeout(90, TimeUnit.SECONDS)
                //10m秒写入超时
                .writeTimeout(90, TimeUnit.SECONDS)
                //10秒读取超时
                .readTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    public static OkHttpUtils getInstance() {
        if (instance == null) {
            synchronized (LOCKER) {
                if (instance == null) {
                    instance = new OkHttpUtils();
                }
            }
        }
        return instance;
    }

    public String doGet(String url){
        if (isBlankUrl(url)){
            return null;
        }
        Request request = getRequestForGet(url);
        return commonRequest(request);
    }

    public String doGet(String url, HashMap<String, Object> params){
        if (isBlankUrl(url)){
            return null;
        }
        Request request = getRequestForGet(url, params,null);
        return commonRequest(request);
    }
    public String doGet(String url, HashMap<String, Object> params,HashMap<String, Object> header){
        if (isBlankUrl(url)){
            return null;
        }
        Request request = getRequestForGet(url, params,header);
        return commonRequest(request);
    }

    public String doPostJson(String url, String json,HashMap<String, String> header){
        if (isBlankUrl(url)){
            return null;
        }
        Request request = getRequestForPostJson(url, json,header);
        return commonRequest(request);
    }

    public String doPostForm(String url, Map<String, Object> params,HashMap<String, Object> header){
        if (isBlankUrl(url)) {
            return null;
        }
        Request request = getRequestForPostForm(url, params,header);
        return commonRequest(request);
    }

    private Boolean isBlankUrl(String url){
        if (StringUtils.isBlank(url)){
			log.info("url is not blank");
            return true;
        }else{
            return false;
        }
    }

    private String commonRequest(Request request){
        String re = "";
        Response response = null;
        try {
            Call call = okHttpClient.newCall(request);
            response = call.execute();
            if (response != null){
                if (response.isSuccessful()){
                    re = response.body().string();
					log.info("request url:{};response:{}", request.url().toString(), re);
                }else {
					log.warn("request failure url:{};code:{};message:{}", request.url().toString(),response.code(),response.message());
                }
            }
        }catch (Exception e){
			log.error("request execute failure url:{}", request.url().toString());
			e.printStackTrace();
        }finally {
            if (response != null){
                response.close();
            }
        }
        return re;
    }

    private Request getRequestForPostJson(String url, String json,HashMap<String, String> header){
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, json);
        Request.Builder request = new Request.Builder()
                .url(url)
                .post(body);

        if (header != null && header.size()>0){
            for (Map.Entry<String,String> entry:header.entrySet()){
                request.addHeader(entry.getKey(),entry.getValue());
            }
        }
        return request.build();
    }


    private Request getRequestForPostForm(String url, Map<String, Object> params,HashMap<String, Object> header){
        if (params == null) {
            params = new HashMap<>();
        }
        FormBody.Builder builder = new FormBody.Builder();
        if (params != null && params.size() > 0) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                builder.addEncoded(entry.getKey(), entry.getValue()==null?"":entry.getValue().toString());
            }
        }
        RequestBody requestBody = builder.build();
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody);

        if (header != null && header.size()>0){
            for (Map.Entry<String,Object> entry:header.entrySet()){
                requestBuilder.addHeader(entry.getKey(),entry.getValue()==null?"":entry.getValue().toString());
            }
        }
        return requestBuilder.build();
    }

    private Request getRequestForGet(String url, HashMap<String, Object> params,HashMap<String, Object> header){
        Request.Builder requestBuilder = new Request.Builder()
                .url(getUrlStringForGet(url, params));
        if (header != null && header.size()>0){
            for (Map.Entry<String,Object> entry:header.entrySet()){
                requestBuilder.addHeader(entry.getKey(),entry.getValue()==null?"":entry.getValue().toString());
            }
        }
        return requestBuilder.build();
    }

    private Request getRequestForGet(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return request;
    }

    private String getUrlStringForGet(String url, HashMap<String, Object> params) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(url);
        urlBuilder.append("?");
        if (params != null && params.size() > 0) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                try {
                    urlBuilder.append("&").append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue()==null?"":entry.getValue().toString(), "UTF-8"));
                } catch (Exception e) {
                    urlBuilder.append("&").append(entry.getKey()).append("=").append(entry.getValue()==null?"":entry.getValue().toString());
                }
            }
        }
        return urlBuilder.toString();
    }

    private  OkHttpClient.Builder buildOKHttpClient() {
        try {
            TrustManager[] trustAllCerts = buildTrustManagers();
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            return builder;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
            return new OkHttpClient.Builder();
        }
    }

    private TrustManager[] buildTrustManagers() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
    }
}
