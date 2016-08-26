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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * A simple handler that handles HTTP post requests that contain
 * JSON and returns a JSON as response.
 *
 * @author Florian Frankenberger
 */
class JsonHandler extends AbstractHandler {

    private static final Logger LOGGER = Logger.getLogger(JsonHandler.class.getCanonicalName());

    private final Map<String, PathInfo<?>> pathMapping = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public static interface JsonRequestHandler<T> {

        Object call(T value);
    }

    private static class PathInfo<T> {

        final Class<T> requestClass;
        final Function<T, Object> requestHandler;

        public PathInfo(Class<T> requestClass, Function<T, Object> requestHandler) {
            this.requestClass = requestClass;
            this.requestHandler = requestHandler;
        }
    }

    public JsonHandler() {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public <T> void putMapping(String path, Class<T> requestClass, Function<T, Object> requestHandler) {
        this.pathMapping.put(path, new PathInfo<>(requestClass, requestHandler));
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (pathMapping.containsKey(target) && baseRequest.getMethod().equalsIgnoreCase("POST")
                && (request.getContentType().startsWith("application/json")
                    || request.getContentType().startsWith("application/javascript"))) {
            final PathInfo<Object> pathInfo = (PathInfo<Object>) pathMapping.get(target);

            try {
                GZIPInputStream gzIn = new GZIPInputStream(request.getInputStream());
                Object value = mapper.readValue(gzIn, pathInfo.requestClass);
                Object result = pathInfo.requestHandler.apply(value);

                ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                GZIPOutputStream gzOut = new GZIPOutputStream(bOut);
                mapper.writeValue(gzOut, result);
                gzOut.flush();
                byte[] payload = bOut.toByteArray();

                response.setContentType("application/json;charset=utf-8");
                response.setContentLength(payload.length);
                response.getOutputStream().write(payload);

                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
            } catch (JsonParseException e) {
                LOGGER.log(Level.WARNING, "Could not parse incoming JSON as type " + pathInfo.requestClass.getCanonicalName(), e);
            } catch (JsonMappingException e) {
                LOGGER.log(Level.WARNING, "Could not map type to JSON", e);
            }
        }
    }

}
