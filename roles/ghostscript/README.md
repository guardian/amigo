# Ghostscript

Downloads the Ghostscript source from https://github.com/ArtifexSoftware/ghostpdl-downloads/releases

By default it downloads gs10012/ghostscript-10.01.2.tar.gz version but this can be configured to any other existing version.

Then, it unzips the package and installs it on the machine. 

Be aware that due to the installation stage being very slow (could take up to half an hour), this role could slow down the bake duration.

## Why
The current ubuntu versions in amigo, do not provide the latest version of ghostscript. So using this role we can install any available version of the ghostscript.