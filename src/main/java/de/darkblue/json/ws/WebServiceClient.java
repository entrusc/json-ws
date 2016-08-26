/*
 * Copyright (C) 2016 Florian Frankenberger.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package de.darkblue.json.ws;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServiceClient {

    private static final Logger LOGGER = Logger.getLogger(WebServiceClient.class.getName());

    public static final HostnameVerifier VERIFIER_IGNORE_COMMON_NAME = new HostnameVerifier() {
        @Override
        public boolean verify(String string, SSLSession ssls) {
            return true; //we just accept any host name as long as the certificate is valid
        }
    };

    private final ObjectMapper mapper = new ObjectMapper();

    private SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    private HostnameVerifier hostnameVerifier = null;

    /**
     * constructs a ws client with default system certificates and default hostname verifier.
     * If your WS-Server has a valid SSL certificate by a major CA then this is the constructor
     * to use.
     *
     * @throws IOException
     */
    public WebServiceClient() throws IOException {
        this((InputStream) null, null);
    }

    /**
     * constructs a ws client that uses the given keystore's certificates to validate the server's
     * certificate. The common name of the server will be checked according to the default
     * hostname verifier.
     *
     * @param trustStoreFile
     * @param trustStorePassword
     * @throws IOException
     */
    public WebServiceClient(File trustStoreFile, String trustStorePassword) throws IOException {
        this(new FileInputStream(trustStoreFile), trustStorePassword, null);
    }

    /**
     * constructs a ws client that uses the given keystore's certificates to validate the server's
     * certificate. The common name of the server will be checked according to the default
     * hostname verifier.
     *
     * @param trustStoreIn
     * @param trustStorePassword
     * @throws IOException
     */
    public WebServiceClient(InputStream trustStoreIn, String trustStorePassword) throws IOException {
        this(trustStoreIn, trustStorePassword, null);
    }

    public WebServiceClient(File trustStoreFile, String trustStorePassword, HostnameVerifier hostnameVerifier) throws IOException {
        this(new FileInputStream(trustStoreFile), trustStorePassword, hostnameVerifier);
    }

    /**
     * constructs a ws client that uses the given keystore's certificates to validate the server's
     * certificate. The common name of the server will be checked according to the provided hostname
     * verifier.
     *
     * @param trustStoreIn
     * @param trustStorePassword
     * @param hostnameVerifier
     * @throws IOException
     */
    public WebServiceClient(InputStream trustStoreIn, String trustStorePassword, HostnameVerifier hostnameVerifier) throws IOException {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        if (trustStoreIn != null && trustStorePassword != null) {
            KeyStore trustStore = prepareTrustStore(trustStoreIn, trustStorePassword);
            this.socketFactory = prepareSocketFactory(trustStore);
        }
        this.hostnameVerifier = hostnameVerifier;
    }

    /**
     * calls a remote service at the given URL
     *
     * @param <R> the return type that the JSON gets wrapped to
     * @param <P> the parameter type that is converted into JSON
     * @param url the url of the remote web service
     * @param responseClass the class of the response object
     * @param parameter the parameter to send
     * @return
     * @throws MalformedURLException
     * @throws IOException
     * @throws RemoteInvokationException
     */
    public <R, P> R call(String url, Class<R> responseClass, P parameter) throws MalformedURLException, IOException, RemoteInvokationException {
        URL properURL = new URL(url);
        URLConnection connection = properURL.openConnection();
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;

            if (this.hostnameVerifier != null) {
                httpsConnection.setHostnameVerifier(this.hostnameVerifier);
            }

            httpsConnection.setSSLSocketFactory(socketFactory);
        } else
            if (!(connection instanceof HttpURLConnection)) {
                throw new IllegalArgumentException("The URL's protocol is not supported by this web service client");
            }

        //here it is safe to assume that we have a HttpURLConnection
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        httpConnection.setRequestMethod("POST");
        httpConnection.setDoOutput(true);
        httpConnection.setDoInput(true);
        httpConnection.setConnectTimeout(5000);

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        GZIPOutputStream gzOut = new GZIPOutputStream(bOut);
        mapper.writeValue(gzOut, parameter);
        gzOut.flush();
        byte[] payload = bOut.toByteArray();

        httpConnection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
        httpConnection.setRequestProperty("Conent-Length", String.valueOf(payload.length));
        httpConnection.connect();
        try {
            try (OutputStream out = httpConnection.getOutputStream()) {
                out.write(payload);

                try (InputStream in = new GZIPInputStream(httpConnection.getInputStream())) {
                    R result = null;
                    if (responseClass != void.class && responseClass != Void.class) {
                        result = mapper.readValue(in, responseClass);
                    }
                    if (httpConnection.getResponseCode() / 100 != 2) {
                        throw new RemoteInvokationException("Response code was not 2xx but " + httpConnection.getResponseCode());
                    }
                    return result;
                } catch (JsonParseException | JsonMappingException e) {
                    throw new RemoteInvokationException("Result was illegal formated or could not be mapped to given result class", e);
                }
            }
        } finally {
            httpConnection.disconnect();
        }
    }

    /**
     * creates a proxy object where all calls
     * are redirected to the remote webservice
     *
     * @param <T>
     * @param iface
     * @return
     */
    public <T> T proxyRemoteService(String urlPrefix, Class<T> iface) {
        final String fullPrefix = urlPrefix + (urlPrefix.endsWith("/") ? "" : "/");
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface },
                (Object proxy, Method method, Object[] args) -> {
                    if (args == null) {
                        args = new Object[1];
                    }
                    if (args.length == 0) {
                        args = new Object[1];
                    }
                    if (args[0] == null) {
                        args[0] = new JsonEmpty();
                    }
                    if (args.length > 1) {
                        throw new IllegalStateException("This webservice implementation allows only one parameter");
                    }
//                    System.out.println("EXECUTING call(" + fullPrefix + method.getName() + ", " + method.getReturnType() + ", " + Arrays.toString(args) + ")");
                    return call(fullPrefix + method.getName(), method.getReturnType(), args[0]);
                }
        );
    }

    private SSLSocketFactory prepareSocketFactory(KeyStore trustStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx.getSocketFactory();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("TLS Algorithm unknown", ex);
        } catch (KeyStoreException ex) {
            throw new IllegalStateException("Keystore problem", ex);
        } catch (KeyManagementException ex) {
            throw new IllegalStateException("Keymanagement problem", ex);
        }
    }

    private KeyStore prepareTrustStore(InputStream trustStoreIn, String trustStorePassword) throws IOException {
        try {
            KeyStore trustStore;
            try {
                trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(trustStoreIn, trustStorePassword.toCharArray());

                return trustStore;
            } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException ex) {
                throw new IllegalStateException("Problem with Algorithm or Keystore", ex);
            }
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException) {
                throw new IllegalArgumentException("Password for store is wrong");
            }
            throw e;
        } finally {
            try {
                trustStoreIn.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Problem closing trust store input stream", e);
            }
        }
    }

}
