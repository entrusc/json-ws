JSON-WS
=======

A simple JAVA library that allows the setup of a JSON WebService over HTTP-POST with
a few lines of code. This WS implementation does not provide a public descriptor file,
so this library is more suited for communication between different applications of an
organisation insted of a public WS API for customers.

license
=======
json-ws is licensed under LGPL 2.1 and can therefore be used in any project, even
for commercial ones.

example
=======
The following example provides a WS on http://localhost:80/json/switch and
uses from the posted json the values "address", "unit" and "on":

    server = new Server(80);

    JsonHandler jsonHandler = new JsonHandler();
    jsonHandler.putMapping("/json/switch", JsonRequestSwitch.class
                , value -> {
                    switchUnit(value.address, value.unit, value.on);
                    return null;
                }
    );    

    HandlerList handlerList = new HandlerList();
    handlerList.setHandlers(new Handler[] { jsonHandler });
    server.setHandler(handlerList);

    server.start();

    



