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
package org.apache.dubbo.remoting.http12.h1;

import org.apache.dubbo.remoting.http12.ErrorResponse;
import org.apache.dubbo.remoting.http12.HttpChannel;
import org.apache.dubbo.remoting.http12.HttpChannelObserver;
import org.apache.dubbo.remoting.http12.HttpHeaderNames;
import org.apache.dubbo.remoting.http12.HttpHeaders;
import org.apache.dubbo.remoting.http12.HttpMetadata;
import org.apache.dubbo.remoting.http12.HttpOutputMessage;
import org.apache.dubbo.remoting.http12.SimpleHttpMetadata;
import org.apache.dubbo.remoting.http12.exception.HttpStatusException;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;

import java.io.IOException;

public class Http1ChannelObserver implements HttpChannelObserver {

    private final HttpChannel httpChannel;

    private HttpMessageCodec httpMessageCodec;

    private boolean headerSent;

    public Http1ChannelObserver(HttpChannel httpChannel) {
        this.httpChannel = httpChannel;
    }

    public Http1ChannelObserver(HttpChannel httpChannel, HttpMessageCodec httpMessageCodec) {
        this.httpChannel = httpChannel;
        this.httpMessageCodec = httpMessageCodec;
    }

    public void setHttpMessageCodec(HttpMessageCodec httpMessageCodec) {
        this.httpMessageCodec = httpMessageCodec;
    }

    @Override
    public void onNext(Object data) {
        if (!headerSent) {
            HttpMetadata httpMetadata = encodeHttpHeaders(data);
            writeHeaders(httpMetadata);
            headerSent = true;
        }
        try {
            HttpOutputMessage outputMessage = encodeHttpOutputMessage(data);
            prepareWriteMessage(outputMessage);
            this.httpChannel.writeMessage(outputMessage);
            postWriteMessage(outputMessage);
        } catch (IOException e) {
            onError(e);
        }
    }

    protected void writeHeaders(HttpMetadata httpMetadata) {
        this.httpChannel.writeHeader(httpMetadata);
    }

    protected HttpMetadata encodeHttpHeaders(Object data) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaderNames.CONTENT_TYPE.getName(), httpMessageCodec.contentType().getName());
        httpHeaders.set(HttpHeaderNames.TRANSFER_ENCODING.getName(), "chunked");
        return new SimpleHttpMetadata(httpHeaders);
    }

    protected HttpOutputMessage encodeHttpOutputMessage(Object data) throws IOException {
        HttpOutputMessage httpOutputMessage = this.httpChannel.newOutputMessage();
        this.httpMessageCodec.encode(httpOutputMessage.getBody(), data);
        return httpOutputMessage;
    }

    protected void prepareWriteMessage(HttpOutputMessage httpMessage) throws IOException {
    }

    protected void postWriteMessage(HttpOutputMessage httpMessage) throws IOException {
    }

    @Override
    public void onError(Throwable throwable) {
        int statusCode = 500;
        if (throwable instanceof HttpStatusException) {
            statusCode = ((HttpStatusException) throwable).getStatusCode();
        }
        if (!headerSent) {
            doSendHeaders(String.valueOf(statusCode));
            this.headerSent = true;
        }
        try {
            HttpOutputMessage httpOutputMessage = this.httpChannel.newOutputMessage();
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setStatus(String.valueOf(statusCode));
            errorResponse.setMessage(throwable.getMessage());
            this.httpMessageCodec.encode(httpOutputMessage.getBody(), errorResponse);
            this.getHttpChannel().writeMessage(httpOutputMessage);
        } finally {
            onCompleted();
        }
    }

    @Override
    public void onCompleted() {
        this.getHttpChannel().writeMessage(HttpOutputMessage.EMPTY_MESSAGE);
    }

    @Override
    public HttpChannel getHttpChannel() {
        return this.httpChannel;
    }

    private void doSendHeaders(String status) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaderNames.STATUS.getName(), status);
        httpHeaders.set(HttpHeaderNames.CONTENT_TYPE.getName(), httpMessageCodec.contentType().getName());
        httpHeaders.set(HttpHeaderNames.TRANSFER_ENCODING.getName(), "chunked");
        writeHeaders(new SimpleHttpMetadata(httpHeaders));
        this.headerSent = true;
    }
}
