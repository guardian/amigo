# Java 25 Corretto

Corretto is an openjdk-based Java distribution, published by Amazon, with
various performance and other changes to better suit AWS.

It is the recommended Java distribution to use at the Guardian.

This role installs the JDK and also modifies the default DNS cache control to
cache for 60s instead of forever.
