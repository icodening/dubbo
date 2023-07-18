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

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.remoting.http12.AbstractServerTransportListener;
import org.apache.dubbo.remoting.http12.HttpChannel;
import org.apache.dubbo.remoting.http12.HttpMessage;
import org.apache.dubbo.remoting.http12.RequestMetadata;
import org.apache.dubbo.remoting.http12.ServerCall;
import org.apache.dubbo.remoting.http12.UnaryServerCallListener;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;

/**
 * @author icodening
 * @date 2023.06.15
 */
public class Http1ServerTransportListener extends AbstractServerTransportListener<RequestMetadata, HttpMessage> {

    private final Http1ChannelObserver responseObserver;

    public Http1ServerTransportListener(HttpChannel httpChannel, FrameworkModel frameworkModel) {
        super(frameworkModel);
        this.responseObserver = new Http1ChannelObserver(httpChannel);
    }

    @Override
    public void onMetadata(RequestMetadata metadata) {
        super.onMetadata(metadata);
        this.responseObserver.setHttpMessageCodec(getCodec());
    }

    @Override
    protected ServerCall.Listener startListener(RpcInvocation invocation,
                                                MethodDescriptor methodDescriptor,
                                                Invoker<?> invoker) {
        //http1 only support unary
        return new AutoCompleteUnaryServerCallListener(invocation, invoker, this.responseObserver);
    }

    private static class AutoCompleteUnaryServerCallListener extends UnaryServerCallListener {

        public AutoCompleteUnaryServerCallListener(RpcInvocation invocation, Invoker<?> invoker, StreamObserver<Object> responseObserver) {
            super(invocation, invoker, responseObserver);
        }

        @Override
        public void onMessage(Object message) {
            super.onMessage(message);
            super.onComplete();
        }
    }
}
