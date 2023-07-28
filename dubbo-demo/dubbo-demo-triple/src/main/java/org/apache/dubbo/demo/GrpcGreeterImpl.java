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
package org.apache.dubbo.demo;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.demo.hello.DubboGrpcGreeterTriple;
import org.apache.dubbo.demo.hello.HelloReply;
import org.apache.dubbo.demo.hello.HelloRequest;

public class GrpcGreeterImpl extends DubboGrpcGreeterTriple.GrpcGreeterImplBase {

    @Override
    public HelloReply exchange(HelloRequest request) {
        String name = request.getName();
        return HelloReply.newBuilder().setMessage("unary reply: " + name).build();
    }

    @Override
    public void serverStream(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        String name = request.getName();
        for (int i = 0; i < 5; i++) {
            HelloReply reply = HelloReply.newBuilder().setMessage("serverStream reply: " + name + " " + i).build();
            responseObserver.onNext(reply);
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<HelloRequest> biStream(StreamObserver<HelloReply> responseObserver) {
        return new StreamObserver<HelloRequest>() {
            @Override
            public void onNext(HelloRequest request) {
                String name = request.getName();
                HelloReply reply = HelloReply.newBuilder().setMessage("biStream reply: " + name).build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
