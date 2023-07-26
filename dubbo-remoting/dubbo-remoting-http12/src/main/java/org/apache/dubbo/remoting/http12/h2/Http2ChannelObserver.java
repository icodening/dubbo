/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.http12.h2;

import org.apache.dubbo.remoting.http12.HttpChannelObserver;
import org.apache.dubbo.remoting.http12.HttpHeaderNames;
import org.apache.dubbo.remoting.http12.HttpHeaders;
import org.apache.dubbo.remoting.http12.HttpOutputMessage;
import org.apache.dubbo.remoting.http12.exception.HttpStatusException;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;

import java.io.IOException;
import java.util.function.Consumer;

public class Http2ChannelObserver implements HttpChannelObserver {

    private final H2StreamChannel h2StreamChannel;

    private HttpMessageCodec httpMessageCodec;

    private Consumer<HttpHeaders> onWriteTrailers = (headers -> {
    });

    private Consumer<HttpHeaders> onWriteHeaders = (headers -> {
    });

    private boolean headerSent;

    public Http2ChannelObserver(H2StreamChannel h2StreamChannel) {
        this.h2StreamChannel = h2StreamChannel;
    }

    public void setHttpMessageCodec(HttpMessageCodec httpMessageCodec) {
        this.httpMessageCodec = httpMessageCodec;
    }

    public void setOnWriteTrailers(Consumer<HttpHeaders> onWriteTrailers) {
        this.onWriteTrailers = onWriteTrailers;
    }

    public void setOnWriteHeaders(Consumer<HttpHeaders> onWriteHeaders) {
        this.onWriteHeaders = onWriteHeaders;
    }

    @Override
    public void onNext(Object data) {
        if (!headerSent) {
            doSendHeaders("200");
        }
        try {
            Http2OutputMessage http2OutputMessage = encodeHttp2OutputMessage(data);
            this.h2StreamChannel.writeMessage(http2OutputMessage);
        } catch (IOException e) {
            onError(e);
        }
    }


    protected Http2OutputMessage encodeHttp2OutputMessage(Object data) throws IOException {
        Http2OutputMessage http2OutputMessage = this.h2StreamChannel.newOutputMessage(false);
        this.httpMessageCodec.encode(http2OutputMessage.getBody(), data);
        return http2OutputMessage;
    }

    protected void sendHeaders(HttpHeaders httpHeaders, String status) {
        httpHeaders.set(Http2Headers.STATUS.getName(), status);
        httpHeaders.set(HttpHeaderNames.CONTENT_TYPE.getName(), httpMessageCodec.contentType().getName());
        httpHeaders.set(HttpHeaderNames.TE.getName(), "trailers");
        this.onWriteHeaders.accept(httpHeaders);
        Http2Header http2Header = new Http2MetadataFrame(httpHeaders, false);
        this.h2StreamChannel.writeHeader(http2Header);
    }

    @Override
    public void onError(Throwable throwable) {
        int statusCode = 500;
        if (throwable instanceof HttpStatusException) {
            statusCode = ((HttpStatusException) throwable).getStatusCode();
        }
        if (!headerSent) {
            doSendHeaders(String.valueOf(statusCode));
        }
        try {
            HttpOutputMessage httpOutputMessage = this.h2StreamChannel.newOutputMessage();
            this.httpMessageCodec.encode(httpOutputMessage.getBody(), throwable.getMessage());
            getHttpChannel().writeMessage(httpOutputMessage);
        } finally {
            onCompleted();
        }
    }

    @Override
    public void onCompleted() {
        //trailer
        HttpHeaders httpHeaders = new HttpHeaders();
        this.onWriteTrailers.accept(httpHeaders);
        Http2Header http2Header = new Http2MetadataFrame(httpHeaders, true);
        this.h2StreamChannel.writeHeader(http2Header);
    }

    @Override
    public H2StreamChannel getHttpChannel() {
        return this.h2StreamChannel;
    }

    private void doSendHeaders(String statusCode) {
        sendHeaders(new HttpHeaders(), statusCode);
        this.headerSent = true;
    }
}
