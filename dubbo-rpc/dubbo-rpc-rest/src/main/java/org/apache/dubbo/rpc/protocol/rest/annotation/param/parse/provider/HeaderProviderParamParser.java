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
package org.apache.dubbo.rpc.protocol.rest.annotation.param.parse.provider;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.metadata.rest.ArgInfo;
import org.apache.dubbo.metadata.rest.ParamType;
import org.apache.dubbo.rpc.protocol.rest.constans.RestConstant;
import org.apache.dubbo.rpc.protocol.rest.request.RequestFacade;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * header param parse
 */
@Activate(value = RestConstant.PROVIDER_HEADER_PARSE)
public class HeaderProviderParamParser extends ProviderParamParser {
    @Override
    protected void doParse(ProviderParseContext parseContext, ArgInfo argInfo) {

        RequestFacade request = parseContext.getRequestFacade();
        if (Map.class.isAssignableFrom(argInfo.getParamType())) {

            Map<String, String> headerMap = new LinkedHashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headerMap.put(name, request.getHeader(name));
            }
            parseContext.setValueByIndex(argInfo.getIndex(), headerMap);
            return;
        }

        String header = request.getHeader(argInfo.getAnnotationNameAttribute());
        Object headerValue = paramTypeConvert(argInfo.getParamType(), header);

        parseContext.setValueByIndex(argInfo.getIndex(), headerValue);
    }

    @Override
    public ParamType getParamType() {
        return ParamType.HEADER;
    }
}
