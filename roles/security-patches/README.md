Security patches
=================
A role for applying universal security patches to Amigo baked AMIs.

### Log4J HotPatch
The Log4J HotPatch is intended to automatically disable Log4J2 vulnerability CVE-2021-44228 and CVE-2021-45046. It utilises the Log4jHotPatch tool from https://github.com/corretto/hotpatch-for-apache-log4j2, which injects a Java agent into any running JVM process. Please note that:
- The JAR for the agent is included in this project directly and is currently built from source, as the binary is not published at the time of writing
- It is applied to any running Java process using the `JAVA_TOOL_OPTIONS` environment variable, which can be overwritten, thereby disabling this patch
- Tested only on Ubuntu 18.04 and 20.04. Outside those, the setting of global environment variables should be checked again

### CVE-2026-98432 nftables rules
Installs `cve-2026-98432.nft` to `/etc/nftables.d/`, validates it with `nft -c -f`, appends an `include` for it to `/etc/nftables.conf`, and ensures `nftables.service` is enabled. Requires the `nftables` package (and so `/etc/nftables.conf`) to already be present on the image.
