Netty HTTP Client
=================

An asynchronous http client in Java, with a clean, callback-based API, using Netty 4.x.

The API is inspired a bit by [Node.js](http://nodejs.org)
``http`` module; it is designed to (mostly) avoid the 
Future pattern, and do its business via callbacks.  Wherever possible we avoid
introducing complicated abstractions that try to hide the business of HTTP
communication;  rather your code can be involved in as much or as little
of that as necessary.

Features
--------

 * HTTP and HTTPS
 * Simple support for Basic authentication
 * Optional support for HTTP cookies
 * Easy, typed API for setting headers
 * Fluent builder API for assembling requests
 * Non-blocking, asynchronous
 * Small, low-surface-area API

Read the [javadoc](http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/netty-http-client/netty-http-client/target/apidocs/index.html).  The header writing/parsing classes come from [acteur-util](http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/acteur-modules/acteur-parent/acteur-util/target/apidocs/index.html); some URL-related classes are [documented here](http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/acteur-modules/acteur-parent/url/target/apidocs/index.html).

To use with Maven, add the Maven repo to your project as [described here](http://timboudreau.com/builds/).  Then add groupId ``com.mastfrog`` artifactId ``netty-http-client`` to your POM file.

Usage
-----

The first thing you need is an ``HttpClient``:

```java
	HttpClient client = HttpClient.builder().followRedirects().build();
```

There are two ways to pay attention to the results of an HTTP call - you can listen
for <code>State</code> objects which exist for every state transition in the process
of making a request and handling the response;  or you can provide a simpler callback which
will be called with the response once it arrives.  This looks like


```java
	ResponseFuture h = client
		.get().setURL ( "http://localhost:9333/foo/bar" ))
		.execute ( new ResponseHandler <String> ( String.class ) {

            protected void receive ( HttpResponseStatus status, HttpHeaders headers, String response ) {
                System.out.println ( "Here's the response: '" + response + "'" );
            }
        });
```

You'll note the ``ResponseHandler`` callback is parameterized on String - you can get your content as a
string, byte array, InputStream or Netty ByteBuf.  You can also pass other types;  Jackson is used to
deserialize JSON, and is the default for unknown types (this may fail if Jackson does not know how to
serialize it).

<h4>The Details</h4>

You can get all the details
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
                    DefaultFullHttpResponse d = (DefaultFullHttpResponse) state.get();
		    //do something
                }
            }

        }).execute();
```


Status & To-Dos
---------------

This is a young library;  it works, but it will surely need some polish yet;  and Netty 4.x is still
changing, including occasional incompatible changes.  Here are some things that would be useful to add:

 * Caching on disk or in memory with proper use of ``If-Modified-Since`` and ``If-None-Match`` headers
 * Zero copy file streaming using Netty's FileRegion
 * Real trust/keystores for HTTPS (currently using the dummy keystore implementation from 
Netty's secure chat example, which works but is not secure)
 * Better tests (actually start a local server, etc)

License
-------

MIT license - do what thou wilt, give credit where it's due

Test Harness (netty-http-test-harness)
======================================

Alongside this project is the ``netty-http-test-harness`` project.  It provides
a fluent interface for writing tests of an HTTP server.  The server can be anything - 
the ``Server`` interface has no particular dependencies (but is implemented in
[Acteur](http://github.com/timboudreau/acteur) if you're using that) - it just has
start/stop methods and a port property.

The point is to make it very little code or setup to test something.

Basically you construct a ``TestHarness`` instance - passing it a ``Server``, a
``URL`` for the base URL and a ``ShutdownHookRegistry`` (another simple interface,
from [Giulius](http://github.com/timboudreau/giulius).  Or to do it the easy way, 
and use ``TestHarness.Module`` and [Giulius-Tests](https://github.com/timboudreau/giulius-tests)
as in [this example](https://github.com/timboudreau/acteur/blob/master/acteur-resources/src/test/java/com/mastfrog/acteur/resources/StaticResourcesTest.java).

Here's an example:

```java
        DateTime helloLastModified = har.get("static/hello.txt").go()
                .assertHasContent()
                .assertStatus(OK)
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent(HELLO_CONTENT)
                .getHeader(Headers.LAST_MODIFIED);

        DateTime aLastModified = har.get("static/another.txt").go()
                .assertStatus(OK)
                .assertHasContent()
                .assertHasHeader(Headers.LAST_MODIFIED.name())
                .assertHasHeader(Headers.ETAG.name())
                .assertContent("This is another file.  It has some data in it.\n")
                .getHeader(Headers.LAST_MODIFIED);

        assertNotNull(helloLastModified);
        assertNotNull(aLastModified);

        har.get("static/hello.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, helloLastModified)
                .go()
                .assertStatus(NOT_MODIFIED);

        har.get("static/another.txt")
                .addHeader(Headers.IF_MODIFIED_SINCE, aLastModified)
                .go().assertStatus(NOT_MODIFIED);
```

