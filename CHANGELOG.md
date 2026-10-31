# Changelog

Newest first. Every update gets a new version number, an entry here, and its own release notes file
(`.github/changes/<number>.md`), written as NEW / FIXED / CHANGES.

## v12

### NEW
- Tracking Strike live lock: ring on the ground, ring around the target, sky beam and light trail follow the target every tick.
- `TRACKING` / `LOCKED` readout with live coordinates and range.

### FIXED
- Tracking Strike: you couldn't see if it really tracks (fullscreen cutscene + forced camera). No cutscene now.
- Orbital cutscene no longer covers the whole countdown of the other remotes.
- White fade before impact hid the strike landing. Removed.
- FIRE / COLLAPSE text blocked the middle of the screen. Smaller and moved up.

### CHANGES
- Orbital intro shortened to 1.5 seconds; countdown then runs over the real world (slim bars, small timer, no tint).
- Particle textures redrawn clean (smooth anti-aliased glows).
- Every update now gets its own release (v12, v13, ...). Old releases are kept.

## v11 and earlier

- Eight remotes (Railgun, Laser, Meteor, Singularity, EMP, Napalm, Nuke, Tracking Strike) + Targeting Chip.
- Orbital cutscene on every remote, custom sounds, full config.
- Published as a single rolling `latest` release (replaced on every push).
