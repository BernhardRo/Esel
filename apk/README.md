# Esel

1. Uninstall the Eversense App
1. Install the patched Eversense app and use it as described by the vendor
  * You need to enable installation of Apps from unknown sources
2. Install ESEL on your phone.
3. Configuration:
  * Allow ESEL to run in the background (it will ask for it)
  * Don't touch the "DB Path" setting unless you changed the default data path when installing the Eversense app and know what you are doing!
  * Upload to Nightscout: Activate "Send to NightScout" in the preferences. It needs a configured AndroidAPS with internal NSClient or NSClient itself installed on the same phone
  * Inter-App-Broadcasts: Activate "Send to AAPS and xDrip". In xDrip activate the input method "640g/Eversense".
4. Install xDrip: https://jamorham.github.io/#xdrip-plus (Download latest apk)
  * Use as Datasource 640G / Eversense
