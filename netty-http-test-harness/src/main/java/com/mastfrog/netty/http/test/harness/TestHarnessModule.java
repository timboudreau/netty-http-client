package com.mastfrog.netty.http.test.harness;

import com.google.inject.AbstractModule;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.HttpClientBuilder;

/**
 * Simply binds ErrorInterceptor so that TestHarness throwIfError() will catch
 * server-side errors (assuming the server requests injection of the
 * ErrorInterceptor and passes any thrown exceptions to it).
 *
 * @author Tim Boudreau
 */
public class TestHarnessModule extends AbstractModule {

    private final HttpClientBuilder clientBuilder;

    public TestHarnessModule(HttpClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    public TestHarnessModule() {
        this(HttpClient.builder());
    }

    @Override
    protected void configure() {
        bind(ErrorInterceptor.class).to(TestHarness.class);
        bind(HttpClient.class).toInstance(clientBuilder.build());
    }
}
