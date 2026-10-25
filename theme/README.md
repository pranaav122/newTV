# Local theme data

`mood-catalog.json` contains the 54-title, seven-mood snapshot used on the TV, dated 2026-10-25. Links and availability are a historical snapshot, not a live catalogue service. `config.json` enables a static, undimmed wallpaper without rotation or captions.

Transfer these JSON files and locally supplied artwork with the upstream theme workflow. Files resolve relative to the launcher's app-specific external-files directory, then are cached internally for boot stability.

Expected local artwork:

- `wallpaper.jpg`: the chosen static Home wallpaper.
- `characters/sheldon.png`, `walter.png`, `spider.png`, `jane.png`, `jd.png`: transparent character cutouts.
- `scenes/sheldon.jpg`, `walter.jpg`, `spider.jpg`, `jane.jpg`, `jd.jpg`: character-title scenes.
- `posters/<id>.jpg`: poster for each catalogue title's `id`.
- `backdrops/<id>.jpg`: scene for that same catalogue ID.

Character ordering and title links live in FavouriteTitles.java. Catalogue poster IDs, moods and movie/show kinds live in the JSON. Artwork, live Continue watching entries and device settings are not included here. The launcher skips a catalogue title when its app is missing or disabled.
