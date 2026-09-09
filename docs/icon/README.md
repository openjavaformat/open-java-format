# Icon

A broken method chain: one long line, then a group of shorter ones sharing a left edge, with a
column guide down the right. It is the formatter's own output shape — the thing that distinguishes
it is deciding *where* to break and what to line up, not laying everything out uniformly.

| File | Use |
|---|---|
| `icon.svg` | The mark. Inherits `currentColor`, so one file covers light, dark and monochrome. |
| `favicon.svg` | Same geometry, guide at full strength. Below ~24px contrast is what keeps the guide visible, not shape. |
| `avatar.svg` | The mark on a white rounded square, for the GitHub organisation and marketplace tiles. |
| `avatar-512.png`, `avatar-128.png` | Rasterised from `avatar.svg`; GitHub organisation avatars must be raster. |

Regenerate the PNGs after editing the SVG:

```bash
rsvg-convert -w 512 -h 512 docs/icon/avatar.svg -o docs/icon/avatar-512.png
rsvg-convert -w 128 -h 128 docs/icon/avatar.svg -o docs/icon/avatar-128.png
```

## What not to break

The mark has seven elements, which is the ceiling for 16px. Anything added has to displace
something else.

- **The shared right edge does the work.** The first and third bars end on the same x. That
  implied vertical is what carries "there is a boundary" at sizes where the dashes disappear, and
  it costs nothing because no thin element draws it. Changing either bar's width breaks the idea
  silently — the icon will still look fine at 64px and mean nothing at 16px.
- **The guide's rhythm must not match the bars'.** Three dashes against four bars is deliberate:
  mismatched rhythms read as two layers, so the boundary belongs to the format rather than to any
  one line. Align them and the dashes turn into decoration stuck on the bar ends.
- **Check every edit at 16px first.** Every version of this mark looked fine large. The small size
  is the only one that rejects anything.

The two colours in `avatar.svg` are the only brand decision baked in here, and they are a
placeholder. The white ground means that on GitHub's light theme the tile itself disappears and
only the mark reads; an off-white ground keeps the tile visible if that is preferred.
