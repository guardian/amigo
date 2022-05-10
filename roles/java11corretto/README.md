# Java 11 Corretto

Corretto is an openjdk-based Java distribution, published by Amazon, with
various performance and other changes to better suit AWS.

It is the recommended Java distribution to use at the Guardian.

This role installs the JDK and also modifies the default DNS cache control to
cache for 60s instead of forever.

A guide to migrate from Java 8 can be found
[here](https://docs.google.com/document/d/1ZR-YnaXCT5_gLVmTCeGs0mWd3KPaAozPjQK8uUzHZ9w/edit?usp=sharing).