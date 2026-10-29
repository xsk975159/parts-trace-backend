package com.kewei.parts.infrastructure.confirmation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

@Component
public class RelayVoteClient {
    private final ObjectMapper json;
    public RelayVoteClient(ObjectMapper json) { this.json=json; }
    public RelayCertificate.Vote request(String endpoint, RelayCertificate.Request request, int timeoutMs) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setConnectTimeout(timeoutMs);connection.setReadTimeout(timeoutMs);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
        byte[] body=json.writeValueAsBytes(request);connection.setFixedLengthStreamingMode(body.length);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2L*timeoutMs);
        try {
            try(OutputStream out=connection.getOutputStream()) {out.write(body);}
            if(connection.getResponseCode()!=200) throw new IOException("验证节点拒绝请求");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[2048];int count;
                while((count=in.read(buffer))!=-1) {
                    if(System.nanoTime()>deadline || Thread.currentThread().isInterrupted())throw new IOException("核验响应超时");
                    if(out.size()+count>16384)throw new IOException("核验响应过大");out.write(buffer,0,count);
                }
                return json.readValue(out.toByteArray(),RelayCertificate.Vote.class);
            }
        } finally {connection.disconnect();}
    }
}
