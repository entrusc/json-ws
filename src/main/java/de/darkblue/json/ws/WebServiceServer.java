/**
 * Copyright 2016 by moebiusgames.com
 *
 * Be inspired by this source but please don't just copy it ;)
 */
package de.darkblue.json.ws;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * A simple JSON web service implementation that uses embedded jetty.
 *
 * @author Florian Frankenberger
 */
public class WebServiceServer {

    private static final Logger LOGGER = Logger.getLogger(WebServiceServer.class.getName());

    private final Server server = new Server();

    private Integer httpPort = 8080;
    private Integer httpsPort = null;

    private File keyStoreFile = null;
    private String keyStorePassword = null;

    private final JsonHandler jsonHandler = new JsonHandler();

    /**
     * creates a webservice with http port set to 8080
     */
    public WebServiceServer() {
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public void setHttpsPort(Integer httpsPort) {
        this.httpsPort = httpsPort;
    }

    public void setKeystore(File keyStoreFile, String keyStorePassword) {
        this.keyStoreFile = keyStoreFile;
        this.keyStorePassword = keyStorePassword;
    }

    public <T> void addJSONMapping(String path, Class<T> requestClass, Function<T, Object> requestHandler) {
        jsonHandler.putMapping(path, requestClass, requestHandler);
    }

    /**
     * scans the given instance for methods with @WebServiceMethod annotation
     * and makes them public. The instance itself must be annotated with
     * the @WebService annotation to specify a path.
     *
     * @param service
     */
    public void addServiceImplementation(Object service) {
        final Class<? extends Object> clazz = service.getClass();
        final WebService webService = clazz.getAnnotation(WebService.class);
        if (webService != null) {
            final String pathPrefix = webService.path() + (webService.path().endsWith("/") ? "" : "/");
            for (final Method method : clazz.getMethods()) {
                WebServiceMethod webServiceMethod = method.getAnnotation(WebServiceMethod.class);
                if (webServiceMethod != null
                        && method.getParameterCount() <= 1
                        && method.getReturnType() != Void.class) {
                    final String methodName = "__default__".equals(webServiceMethod.name()) ? method.getName() : webServiceMethod.name();
                    final Class<?> parameterType = method.getParameterCount() == 0 ? JsonEmpty.class : method.getParameterTypes()[0];
                    this.addJSONMapping(pathPrefix + methodName, parameterType, req -> {
                        try {
                            if (method.getParameterCount() == 0) {
                                return method.invoke(service);
                            }
                            return method.invoke(service, req);
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                            LOGGER.log(Level.WARNING, "Problem executing service method " + clazz.getCanonicalName() + "." + method.getName() + "()", ex);
                        }
                        return null;
                    });
                }
            }
        } else {
            throw new IllegalArgumentException("Given service is not annotated");
        }
    }

    public void start() {
        try {
            LOGGER.info("Starting webservice ...");

            server.setDumpAfterStart(false);
            server.setDumpBeforeStop(false);
            server.setStopAtShutdown(true);

            HttpConfiguration basicConfiguration = new HttpConfiguration();
            basicConfiguration.setOutputBufferSize(32768);
            basicConfiguration.setRequestHeaderSize(8192);
            basicConfiguration.setResponseHeaderSize(8192);
            basicConfiguration.setSendServerVersion(true);
            basicConfiguration.setSendDateHeader(false);

            // HTTP
            if (this.httpPort != null) {
                final HttpConfiguration httpConfiguration = new HttpConfiguration(basicConfiguration);
                ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
                http.setPort(this.httpPort);
                http.setIdleTimeout(30000);
                server.addConnector(http);

                LOGGER.log(Level.INFO, "\topening port " + this.httpPort + " for http connections");
            }

            // HTTPS
            if (this.httpsPort != null) {
                if (this.keyStoreFile != null && this.keyStorePassword != null) {
                    SslContextFactory sslContextFactory = new SslContextFactory();
                    sslContextFactory.setKeyStorePath(keyStoreFile.getAbsolutePath());
                    sslContextFactory.setKeyStorePassword(keyStorePassword);
                    sslContextFactory.setTrustStorePath(keyStoreFile.getAbsolutePath());
                    sslContextFactory.setTrustStorePassword(keyStorePassword);
                    sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

                    HttpConfiguration https_config = new HttpConfiguration(basicConfiguration);
                    https_config.addCustomizer(new SecureRequestCustomizer());

                    ServerConnector sslConnector = new ServerConnector(server,
                            new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                            new HttpConnectionFactory(https_config));
                    sslConnector.setPort(this.httpsPort);
                    server.addConnector(sslConnector);
                    LOGGER.log(Level.INFO, "\topening port " + this.httpsPort + " for ssl connections");
                } else {
                    LOGGER.log(Level.WARNING, "SSL is enabled by setting its port to " + this.httpsPort
                            + ", but you did not specify a keystore with certificates");
                }
            }

            HandlerList handlerList = new HandlerList();
            handlerList.setHandlers(new Handler[]{ jsonHandler });
            server.setHandler(handlerList);

            server.start();
            LOGGER.log(Level.INFO, "\tserver runnning.");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void stop() {
        try {
            LOGGER.log(Level.INFO, "Shutting down webservice ...");
            this.server.stop();
            LOGGER.log(Level.INFO, "\tserver stopped.");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Problem when shutting down server", ex);
        }
    }

}
