/*
 * Copyright (c) 2018 HERE Europe B.V.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.here.account.client;

import com.here.account.http.HttpConstants;
import com.here.account.http.HttpException;
import com.here.account.http.HttpProvider;
import com.here.account.http.HttpProvider.HttpRequest;
import com.here.account.oauth2.ErrorResponse;
import com.here.account.oauth2.RequestExecutionException;
import com.here.account.oauth2.ResponseParsingException;
import com.here.account.util.CloseUtil;
import com.here.account.util.OAuthConstants;
import com.here.account.util.Serializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A Client that talks to a Resource Server, in OAuth2-speak.
 * It is expected that a wrapper class invokes methods on an instance 
 * of this class, so that simple Java create(), read(), update(), 
 * and delete() methods can be written with POJOs.
 * 
 * Suitable for use with JSON-object response APIs.
 * 
 * @author kmccrack
 */
public class Client {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    public static class Builder {
        private HttpProvider httpProvider;
        private Serializer serializer;
        private HttpProvider.HttpRequestAuthorizer clientAuthorizer;

        private Builder() {

        }

        public Builder withHttpProvider(HttpProvider httpProvider) {
            this.httpProvider = httpProvider;
            return this;
        }

        public Builder withSerializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder withClientAuthorizer(HttpProvider.HttpRequestAuthorizer clientAuthorizer) {
            this.clientAuthorizer = clientAuthorizer;
            return this;
        }

        public Client build() {
            return new Client(httpProvider, serializer, clientAuthorizer);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final Pattern START_PATTERN = Pattern.compile("\\A");
    private static final String LOWERCASE_CONTENT_TYPE_JSON = HttpConstants.CONTENT_TYPE_JSON.toLowerCase();
    private final HttpProvider httpProvider;
    private final Serializer serializer;
    private final HttpProvider.HttpRequestAuthorizer clientAuthorizer;

    private Client(HttpProvider httpProvider, Serializer serializer,
                    HttpProvider.HttpRequestAuthorizer clientAuthorizer) {
        this.httpProvider = httpProvider;
        this.serializer = serializer;
        this.clientAuthorizer = clientAuthorizer;
    }

    public HttpProvider.HttpRequestAuthorizer getClientAuthorizer() {
        return this.clientAuthorizer;
    }

    /**
     * Sends the requested HTTP Message to the Server.
     * This method is useful if you want to interact with your 
     * Resource Server using serializable Java objects as inputs and outputs.
     * It covers the case where your Resource Server API uses 
     * Content-Type: application/json for request and response 
     * documents.
     *
     * @param method the HTTP method
     * @param url the HTTP request URL
     * @param request the request object of type R, or null if no request object
     * @param responseClass the response object class, for deserialization
     * @param errorResponseClass the response error object class, for deserialization
     * @param newExceptionFunction the function for getting a new RuntimeException based
     *      on the statusCode and error response object
     * @param <R> the Request parameterized type
     * @param <T> the Response parameterized type
     * @param <U> the Response Error parameterized type
     * @return the Response of type T
     * @throws RequestExecutionException if trouble executing the request
     * @throws ResponseParsingException if trouble serializing the request, 
     *      or deserializing the response
     */
    public <R, T, U> T sendMessage(
            String method,
            String url,
            R request,
            Class<T> responseClass,
            Class<U> errorResponseClass,
            BiFunction<Integer, U, RuntimeException> newExceptionFunction)
            throws RequestExecutionException, ResponseParsingException {
        return sendMessage(method, url, request,
                null,
                responseClass, errorResponseClass, newExceptionFunction);
    }

    /**
     * Sends the requested HTTP Message to the Server, with additional headers.
     * This method is useful if you want to interact with your
     * Resource Server using serializable Java objects as inputs and outputs.
     * It covers the case where your Resource Server API uses
     * Content-Type: application/json for request and response
     * documents.
     * This method provides the ability to specify HTTP Headers beyond that (those)
     * possibly added by your HttpRequestAuthorizer.
     *
     * @param method the HTTP method
     * @param url the HTTP request URL
     * @param request the request object of type R, or null if no request object
     * @param additionalHeaders additional headers to add to the request,
     *        beyond that (those) possibly added by your HttpRequestAuthorizer.
     * @param responseClass the response object class, for deserialization
     * @param errorResponseClass the response error object class, for deserialization
     * @param newExceptionFunction the function for getting a new RuntimeException based
     *      on the statusCode and error response object
     * @param <R> the Request parameterized type
     * @param <T> the Response parameterized type
     * @param <U> the Response Error parameterized type
     * @return the Response of type T
     * @throws RequestExecutionException if trouble executing the request
     * @throws ResponseParsingException if trouble serializing the request,
     *      or deserializing the response
     */
    public <R, T, U> T sendMessage(
            String method,
            String url,
            R request,
            Map<String, String> additionalHeaders,
            Class<T> responseClass,
            Class<U> errorResponseClass,
            BiFunction<Integer, U, RuntimeException> newExceptionFunction)
            throws RequestExecutionException, ResponseParsingException {


        HttpProvider.HttpRequest httpRequest;
        if (null == request) {
            httpRequest = httpProvider.getRequest(
                    clientAuthorizer, method, url, (String) null);
        } else {
            // HttpConstants.ContentTypes.JSON == requestContentType
            String jsonBody = serializer.objectToJson(request);
            httpRequest = httpProvider.getRequest(
                        clientAuthorizer, method, url, jsonBody);
        }

        // If there's additional headers, add them to the request
        HttpRequest httpRequestWithAdditonalHeaders = addAdditionalHeaders(httpRequest, additionalHeaders);

        return sendMessage(httpRequestWithAdditonalHeaders, responseClass,
                errorResponseClass, newExceptionFunction);
    }
    
    /**
     * Sends the requested HTTP Message to the Server.
     * This method is useful if you have already constructed your 
     * HttpRequest, and your API supports 
     * Content-Type: application/json response documents.
     *
     * @param httpRequest the HTTP Request
     * @param responseClass the Response class
     * @param errorResponseClass the class for Error Responses
     * @param newExceptionFunction the new RuntimeException-creating function 
     *     that takes a statusCode and an Error Response object.
     * @param <T> the Response parameterized type
     * @param <U> the Response Error parameterized type
     * @return the Response of type T
     * @throws RequestExecutionException if trouble executing the request
     * @throws ResponseParsingException if trouble serializing the request, 
     *      or deserializing the response
     */
    public <T, U> T sendMessage(HttpRequest httpRequest, Class<T> responseClass,
            Class<U> errorResponseClass,
            BiFunction<Integer, U, RuntimeException> newExceptionFunction) 
            throws RequestExecutionException, ResponseParsingException {
        // blocking
        HttpProvider.HttpResponse httpResponse;
        InputStream jsonInputStream;

        try {
            httpResponse = httpProvider.execute(httpRequest);
            jsonInputStream = httpResponse.getResponseBody();
        } catch (IOException | HttpException e) {
            throw new RequestExecutionException(e);
        }

        int statusCode = httpResponse.getStatusCode();
        try {
            if (200 == statusCode || 201 == statusCode || 204 == statusCode) {
                if (204 == statusCode && responseClass.equals(Void.class)) {
                    return null;
                }
                try {
                    return serializer.jsonToPojo(jsonInputStream, responseClass);
                } catch (Exception e) {
                    throw new ResponseParsingException(e);
                }
            } else {
                U errorResponse;
                try {
                    if (isResponseTypeJson(httpResponse)) {
                        errorResponse = serializer.jsonToPojo(jsonInputStream, errorResponseClass);
                    } else {
                        errorResponse = instantiateErrorResponseClass(errorResponseClass, jsonInputStream, statusCode);
                    }
                } catch (Exception e) {
                    throw new ResponseParsingException(e);
                }
                throw newExceptionFunction.apply(statusCode, errorResponse);
            }
        } finally {
            CloseUtil.nullSafeCloseThrowingUnchecked(jsonInputStream);
        }
    }

    /**
     * Return whether the response-type is JSON
     *
     * @param response   the HTTP response
     * @return  true-the response-type is JSON, false-the response-type is not JSON
     */
    private boolean isResponseTypeJson(HttpProvider.HttpResponse response) {
        Map<String, List<String>> headers = response.getHeaders();
        List<String> responseTypes = headers.get(HttpConstants.CONTENT_TYPE);

        for (String aResponseType: responseTypes) {
            if (aResponseType.toLowerCase().trim().startsWith(LOWERCASE_CONTENT_TYPE_JSON)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create an instance of the specified errorResponseClass
     *
     * @param errorResponseClass    the Response Error class
     * @param responseBody          the response body
     * @param statusCode            HTTP status code
     * @param <U>                   the Response Error parameterized type
     * @return                      an instance of the Response Error class
     */
    private <U> U instantiateErrorResponseClass(Class<U> errorResponseClass, InputStream responseBody, int statusCode) {
        try {
            U errorResponse;

            if (errorResponseClass.isAssignableFrom(ErrorResponse.class)) {
                Constructor<U> ctor = errorResponseClass.getConstructor(String.class, String.class, String.class,
                        Integer.class, Integer.class, String.class);
                errorResponse = ctor.newInstance(null, null, null, statusCode, null, convertStreamToString(responseBody));
            } else {
                Constructor<U> ctor = errorResponseClass.getConstructor();
                errorResponse = ctor.newInstance();
            }

            return errorResponse;
        } catch (NoSuchMethodException nsme) {
            throw new RequestExecutionException("Internal Error: "+errorResponseClass.getName()+" has no default constructor");
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new RequestExecutionException("Internal Error: "+errorResponseClass.getName()+" cannot be constructed", ex);
        }
    }

    /**
     * Convert an inputStream to a String
     *
     * @param is    input stream to convert
     * @return      String equivilant of the inputStream
     */
    private String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is, OAuthConstants.UTF_8_STRING).useDelimiter(START_PATTERN);
        // regex \A, matches the beginning of input. This tells Scanner to read the entire stream,
        // from beginning to the next beginning (and there isn't one) so the entire stream is read.
        String str = s.hasNext() ? s.next() : "";
        return str.substring(0, Math.min(1024, str.length()));
    }

    /**
     * Add additional headers to the request
     *
     * @param httpRequest           the request
     * @param additionalHeaders     additional headers
     * @return  httpRequest with additional headers
     */
    private HttpProvider.HttpRequest addAdditionalHeaders(HttpProvider.HttpRequest httpRequest,
                                                          Map<String, String> additionalHeaders) {
        if (null != additionalHeaders) {
            for (Map.Entry<String, String> additionalHeader : additionalHeaders.entrySet()) {
                String name = additionalHeader.getKey();
                String value = additionalHeader.getValue();
                httpRequest.addHeader(name, value);
            }
        }
        return httpRequest;
    }
}
