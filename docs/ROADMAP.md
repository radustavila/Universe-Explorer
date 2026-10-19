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

The app focuses on the universe and Earth. Launches stay a single small card on purpose.

Bottom bar: **Today · Universe · Earth · Favourites · More** (defined in F0).

## Features, in build order

| # | Section | Feature | What it adds | APIs |
|---|---|---|---|---|
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

F1 and F4 can run in parallel. F0 should land before the next data source. F9 builds on F10's map.

None of these APIs need a key.
