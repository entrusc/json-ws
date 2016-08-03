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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Florian Frankenberger
 */
public class WebServiceClientTest {

    public WebServiceClientTest() {
    }

    public static class Payload2 {
        public String info;
    }

    public static class SimpleRequest2 {
        public String name;
        public List<Payload2> payload = new ArrayList<>();
    }

    public static class Payload {
        public String info;
    }

    public static class SimpleRequest {
        public String name;
        public List<Payload> payload = new ArrayList<>();
    }

    public static class SimpleResponse {
        public boolean ok;
        public int num;
        public String retName;
    }

    @WebService(path = "/json")
    public static class ServiceImpl {

        @WebServiceMethod()
        public SimpleResponse remoteCallMe(SimpleRequest request) {
            SimpleResponse res = new SimpleResponse();
            res.num = 7;
            res.ok = true;
            res.retName = request.name;
            return res;
        }

        @WebServiceMethod()
        public void sth() {
            System.out.println("DOING STH!!!! <<<<<<<<<");
        }

        @WebServiceMethod()
        public void sthElse(SimpleRequest req) {
            System.out.println("Got request with " + req.name);
        }
    }

    public static interface Service {

        SimpleResponse remoteCallMe(SimpleRequest request);

        void sth();

        void sthElse(SimpleRequest req);

    }

    @Test
    public void simpleCallTest() throws IOException, MalformedURLException, RemoteInvokationException {
        WebServiceServer server = new WebServiceServer();
        server.setHttpPort(33255);
        server.addJSONMapping("/json/test", SimpleRequest2.class, req -> {
            System.out.println("Received: " + req.payload.toString());

            SimpleResponse res = new SimpleResponse();
            res.ok = true;
            res.num = 5;
            res.retName = req.name;
            return res;
        });
        server.start(false);

        WebServiceClient client = new WebServiceClient();
        SimpleRequest req = new SimpleRequest();
        req.name = "foobar2000";
        final Payload pl = new Payload();
        pl.info = "hallo";
        req.payload.add(pl);
        SimpleResponse response = client.call("http://localhost:33255/json/test", SimpleResponse.class, req);

        assertEquals(5, response.num);
        assertEquals(true, response.ok);
        assertEquals(req.name, response.retName);

        server.stop();

    }

    @Test
    public void proxyCallTest() throws IOException, MalformedURLException, RemoteInvokationException {
        WebServiceServer server = new WebServiceServer();
        server.setHttpPort(33255);
        server.addServiceImplementation(new ServiceImpl());
        server.start(false);

        WebServiceClient client = new WebServiceClient();
        Service service = client.proxyRemoteService("http://localhost:33255/json", Service.class);

        SimpleRequest req = new SimpleRequest();
        req.name = "foobar2001";
        final Payload pl = new Payload();
        pl.info = "hallo";
        req.payload.add(pl);
        SimpleResponse response = service.remoteCallMe(req);

        assertEquals(7, response.num);
        assertEquals(true, response.ok);
        assertEquals(req.name, response.retName);

        service.sth();
        service.sthElse(req);

        server.stop();

    }

}
