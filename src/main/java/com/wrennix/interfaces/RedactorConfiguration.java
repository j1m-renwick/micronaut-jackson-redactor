package com.wrennix.interfaces;

import jakarta.inject.Singleton;

@Singleton
public interface RedactorConfiguration {

    /**
     *
     * @return true if redaction should be applied at application startup.
     */
    boolean shouldRedact();

    /**
     * A package name that encompasses all the POGOs that should be targeted for redaction processing.
     *
     * @return your project package name or a relevant subclass, e.g. "com.example.myproject.mypogos"
     */
    String rootPackage();
}
