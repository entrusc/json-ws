JSON-WS
=======

A simple JAVA library that allows the setup of a JSON WebService over HTTP(S)-POST with
a few lines of code. This WS implementation does not provide a public descriptor file,
so this library is more suited for communication between different applications of an
organisation insted of a public WS API for customers.

This library also contains for convenience a simple to use WebService client.

license
=======
json-ws is licensed under LGPL 2.1 and can therefore be used in any project, even
for commercial ones.

build
=====

    mvn clean package

example
=======
The following example provides a WS on http://localhost:8080/json/hi

    @WebService(path = "/json")
    public static class ServiceImpl {

        @WebServiceMethod()
        public void hi() {
            System.out.println("Hello World!");
        }

    }
    
    //[...]

    WebServiceServer server = new WebServiceServer();
    server.setHttpPort(8080);
    server.addServiceImplementation(new ServiceImpl());
    server.start(true);

More examples (also on how to use the client) can be found in the test folder.