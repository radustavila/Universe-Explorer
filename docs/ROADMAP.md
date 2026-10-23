# Roadmap

The full plan lives in the Claude Docs doc **NASA Explorer – Space API Roadmap**:
https://claude.ai/artifact/TxJyhHfaAHHwTJ9mJWi49C

That doc is the source of truth. Each feature section there is a self-contained brief (APIs and
endpoints, modules to add, "done when" checklist), and its tracker table holds the live status.
This file is only an index so sessions can `@docs/ROADMAP.md`; do not copy briefs or status here.

To implement a feature, start a session with:

```
Implement F<n> from @docs/ROADMAP.md
```

and read that feature's section in the doc before writing code.

## Direction

The app focuses on the universe and Earth. Launches stay small: one card and, from F27, one screen
(no launch news or events).

Bottom bar: **Today · Universe · Earth · Favourites · More** (defined in F0).

## Next batch, in build order

Chosen by comparing the app again with the main space, sky and launch apps (2026-10-23). Every source
is free and allows commercial use unless noted.

| # | Section | Feature | What it adds | APIs |
|---|---|---|---|---|
| F19 | Earth | Earthquakes | Recent quakes on the Earth maps and in Live Earth events; opt-in strong-quake alert | USGS GeoJSON feeds (public domain) |
| F20 | Universe | Milky Way planner | When the galactic core is up in a dark, moonless sky; twilight times | Astronomy Engine (offline) |
| F21 | Today | NASA podcasts | Latest episodes in News, played in the app with background playback | NASA podcast RSS |
| F27 | Launches | Launches screen | Upcoming and past launches, details, booster landings, launch reminders | Launch Library 2 (15 requests/hour) |
| F22 | Universe | AR sky | The camera behind the sky map, field of view matched to the lens, alignment | CameraX (on device) |
| F23 | Universe | Deep sky and observing log | Messier and bright NGC objects on the sky map; Messier and Caldwell checklists | OpenNGC (CC BY-SA 4.0) |
| F24 | Universe | Webb right now | What JWST is observing now and next this week | STScI observing schedules |
| F25 | Universe | Moon map | Zoomable Moon with landing sites and today's terminator | Own tiles from the LRO WAC mosaic (PDS), NSSDCA |
| F26 | Earth | Climate vital signs | CO2, global temperature, sea level and Arctic sea ice as small charts | NOAA GML, NASA GISTEMP, NOAA STAR, NSIDC (see M4) |
| F28 | Today | Daily quiz and fun facts | 5 questions a day, practice by topic, streaks, badges, a "Did you know" card; F19–F27 each add their own questions | Own question bank + the app's own data (offline) |

After the first release: **F29** Play Games leaderboards and achievements, and a "find it in the sky" quiz
(Google Play Games Services v2; needs F28, F22, F23).

Do not use Open-Meteo or 7Timer (non-commercial free tiers) or Stellarium data (GPL).

## Before monetizing (ads, premium)

| # | Task |
|---|---|
| M1 | Rename the app: no "NASA" in the name, no NASA logos, add "Not affiliated with NASA" |
| M2 | Ask the APOD editors for permission to show APOD with banner ads; until then no ads on APOD screens |
| M3 | AdMob banners with Google UMP consent (EEA/UK), privacy policy, Play Data safety form |
| M4 | Confirm commercial terms with Launch Library 2, CelesTrak and NSIDC (F26); replace wheretheiss.at with on-device SGP4 |

## Built (F0–F18)

| # | Section | Feature | What it adds | APIs |
|---|---|---|---|---|
| F11 | Universe | Stargazing forecast | Tonight's observing score from clouds, darkness and Moon; clear-night alert | MET Norway (CC BY 4.0), Astronomy Engine |
| F16 | Earth | Live Sun | Today's Sun in several wavelengths and the last 48 hours as video | SDO, Helioviewer |
| F12 | Universe | Sky events calendar | Eclipses, meteor showers, Moon phases, oppositions, seasons, with reminders | Astronomy Engine (offline), own meteor table |
| F13 | Universe | Deep Space Network live | Which antennas talk to which spacecraft now; distances to Voyager and New Horizons | DSN Now, JPL Horizons |
| F15 | Universe | More satellites | Passes for Hubble, Tiangong, bright satellites and fresh Starlink trains | CelesTrak (terms unclear, see M4) |
| F14 | Today | NASA news and video | NASA stories, Image of the Day, NASA YouTube videos and live streams | NASA RSS, YouTube feed |
| F17 | Universe | Sky map | Stars, constellations, planets and ISS, aimed by moving the phone; red night mode | HYG (CC BY-SA 4.0), d3-celestial, Astronomy Engine |
| F18 | Universe | Bright comets | Comets visible now and where to look | JPL SBDB query, JPL Horizons |
| F1 | Universe | APOD upgrades | Archive search, jump to date, On this day, daily wallpaper, correct share links, alt text | science.nasa.gov apod-basic |
| F4 | Earth | Earth today | Daily whole-Earth photos from DSCOVR, played as a rotation | EPIC |
| F0 | All | Shared groundwork | Per-API rate limits, cache eviction, favourites for every source, the 5-tab navigation | – |
| F10 | Earth | Satellite map | Zoomable map of yesterday's Earth from orbit, night lights, date slider; shared `:core:map` | NASA GIBS, MapLibre |
| F9 | Earth | Live Earth events | Wildfires, storms, volcanoes and floods on the map | NASA EONET |
| F3 | Universe | Universe gallery | Webb and Hubble rails plus search across NASA's image and video library | NASA Image and Video Library |
| F8 | Universe | Exoplanets | Newest planets with size and distance compared | NASA Exoplanet Archive |
| F5 | Universe | Sky tonight | Moon, visible planets, ISS passes, who is in space | Astronomy Engine, CelesTrak, wheretheiss.at, Launch Library 2 |
| F6 | Earth | Aurora and space weather | Aurora forecast, map and alerts; solar storms; asteroids passing Earth | NOAA SWPC, DONKI, JPL |
| F2 | Launches | Next launches card | Next 3 launches with countdown and livestream link | Launch Library 2 |
| F7 | Universe | Mars photos | Latest raw rover photos | Mars raw images |

None of these APIs need a key.
