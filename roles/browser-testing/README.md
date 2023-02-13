# Browser UI testing

This feature installs Google Chrome, Chromedriver and the virtual frame buffer, Xvfb, to allow for headless browser testing on fully featured Chrome.

The install script does the following:
 - Installs Xvfb
 - Installs the version of Google Chrome specified by `chrome_version` (installs latest if blank) and associated packages
   <br/>_see <https://www.ubuntuupdates.org/package/google_chrome/stable/main/base/google-chrome-stable> for available options_
 - Installs  Chromedriver version specified by `chromedriver_version` (installs latest if blank)
   <br/>_see version numbers for entries ending in `/chromedriver_linux64.zip` from <https://chromedriver.storage.googleapis.com/>_
 - Creates and enables an Xvfb service
