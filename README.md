Netty HTTP Client
=================

An asynchronous http client in Java, with a clean, callback-based API, using Netty 4.x.

The API here is inspired a bit by [Node.js](http://nodejs.org)
``http`` module; it is designed to (mostly) avoid the 
Future pattern, and do its business via callbacks.  Wherever possible we avoid
introducing complicated abstractions that try to hide the business of HTTP
communication;  rather your code can be involved in as much or as little
of that as necessary.

Features
--------

 * HTTP and HTTPS
 * Simple support for Basic authentication
 * Easy, typed API for setting headers
 * Fluent builder API for assembling requests
 * Non-blocking, asynchronous
 * Small, low-surface-area API

Usage
-----

The first thing you need is an ``HttpClient``:

	```java
	HttpClient client = HttpClient.builder().followRedirects().build();
	```
There are two ways to pay attention to the result of an HTTP call - you can listen
for <code>State</code> objects which exist for every state transition in the process
of making a request and handling the response;  or you can provide a simple which
will be called with the response once it arrives.  First, you can get all the details
by providing a ``Receiver<State<?>>`` when you build a request;  there are states for
things like Connecting, HeadersReceived;  you can even capture every chunk of chunked
encoding individually if you want.  

```java
        ResponseFuture f = client.get()
                .setURL( "http://localhost:9333/foo/bar" )
                .setBody( "This is a test", MediaType.PLAIN_TEXT_UTF_8)
                .onEvent( new Receiver<State<?>>() {

            public void receive( State<?> state ) {
                System.out.println( "STATE " + state + " " + state.name() + " " + state.get() );
                if ( state.stateType() == StateType.Finished ) {
                    DefaultFullHttpRequest d = (DefaultFullHttpRequest) state.get();
		    //do something
                }
            }

        }).execute();```

or much more simply:

```java
	        ResponseFuture h = client.get().setURL("http://localhost:9333/foo/bar")).execute(new ResponseHandler<String>(String.class){
            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, String response) {
                System.out.println("CALLED BACK WITH '" + obj + "'");
            }
        });```

You'll note the ``ResponseHandler`` callback is parameterized on String - you can get your content as a
string, byte array, InputStream or Netty ByteBuf.  You can also pass other types;  Jackson is used to
deserialize JSON, and is the default for unknown types (this may fail if Jackson does not know how to
serialize it).


Status & To-Dos
---------------

This is a young library;  it works, but it will surely need some polish yet;  and Netty 4.x is still
changing, including occasional incompatible changes.  Here are some things that would be useful to add:

 * Support for caching and automatically setting cookies
   * With offline persistence?
 * Caching on disk or in memory with propert use of ``If-Modified-Since`` and ``If-None-Match`` headers
 * Zero copy file streaming
 * Real trust/keystores for HTTPS (currently using the dummy keystore implementation from 
Netty's secure chat example, which works but is not secure)
 * Better tests (actually start a local server, etc)

