# Privacy boundaries

The repository contains application code, bundled build resources and example title metadata. The title catalogue contains public streaming destinations, not viewing history or account-specific links.

The launcher has no INTERNET permission or analytics. Continue watching reads entries that installed apps publish to Android TV and opens them locally. The companion weather app sends a city search and the selected place's coordinates to Open-Meteo when weather is requested. No place is preset in the public source. Location choices and weather caches stay in app preferences on the device.

The weather provider accepts only callers signed with the same signing identity. Public package identifiers are generic software identifiers, not device identifiers or credentials.

Do not commit credentials, signing/debugging keys, device backups, account or settings dumps, shared preferences, viewing-history exports, traces, heap dumps or personal artwork. These local files are excluded by the repository's ignore rules. Required third-party licence attributions remain in the repository.
