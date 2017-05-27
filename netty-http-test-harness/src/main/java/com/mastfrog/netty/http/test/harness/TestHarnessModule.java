package com.mastfrog.netty.http.test.harness;

import com.google.inject.AbstractModule;
import com.mastfrog.acteur.util.ErrorInterceptor;

/**
 * Simply binds ErrorInterceptor so that TestHarness throwIfError() will catch
 * server-side errors (assuming the server requests injection of the
 * ErrorInterceptor and passes any thrown exceptions to it).
 *
 * @author Tim Boudreau
 */
public final class TestHarnessModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ErrorInterceptor.class).to(TestHarness.class);
    }

}
