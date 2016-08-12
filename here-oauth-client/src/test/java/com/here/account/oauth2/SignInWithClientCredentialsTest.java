/*
 * Copyright (c) 2016 HERE Europe B.V.
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
package com.here.account.oauth2;


import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.here.account.auth.OAuth1ClientCredentialsProvider;
import com.here.account.http.HttpConstants;
import com.here.account.http.HttpProvider;
import com.here.account.http.apache.ApacheHttpClientProvider;
import static org.junit.Assert.assertTrue;

public class SignInWithClientCredentialsTest extends AbstractCredentialTezt {

    HttpProvider httpProvider;
    TokenEndpoint signIn;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        httpProvider = ApacheHttpClientProvider.builder()
        .setConnectionTimeoutInMs(HttpConstants.DEFAULT_CONNECTION_TIMEOUT_IN_MS)
        .setRequestTimeoutInMs(HttpConstants.DEFAULT_REQUEST_TIMEOUT_IN_MS)
        .build();
        
        this.signIn = HereAccount.getTokenEndpoint(
                httpProvider,
                new OAuth1ClientCredentialsProvider(url, accessKeyId, accessKeySecret)
        );
    }
    
    @After
    public void tearDown() throws IOException {
        if (null != httpProvider) {
            httpProvider.close();
        }
    }

    @Test
    public void test_signIn() throws Exception {
        String hereAccessToken = signIn.requestToken(new ClientCredentialsGrantRequest()).getAccessToken();
        assertTrue("hereAccessToken was null or blank", null != hereAccessToken && hereAccessToken.length() > 0);
    }
    
    @Test
    public void test_signIn_fatFinger() throws Exception {
        this.signIn = HereAccount.getTokenEndpoint(
                httpProvider,
                new OAuth1ClientCredentialsProvider(url, accessKeyId, "fat" + accessKeySecret)
        );

        try{
            signIn.requestToken(new ClientCredentialsGrantRequest()).getAccessToken();
        } catch (AccessTokenException e) {
            ErrorResponse errorResponse = e.getErrorResponse();
            assertTrue("errorResponse was null", null != errorResponse);
            Integer errorCode = errorResponse.getErrorCode();
            Integer expectedErrorCode = 401300;
            assertTrue("errorCode was expected "+expectedErrorCode+", actual "+errorCode, expectedErrorCode.equals(errorCode));
        }

    }
}
