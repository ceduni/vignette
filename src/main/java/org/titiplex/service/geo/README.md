# `service/geo/` — country shapes

Used by `CountryGeoService` to find the country under a point clicked on the languages map.

- `TopoJsonDecoder` turns `resources/geo/countries-110m.json` (TopoJSON, shared borders) into plain
  polygons of `[longitude, latitude]` points.
- `CountryShape` holds one country's polygons and answers "does it contain this point?"
  (holes and islands included).
