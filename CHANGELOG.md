# Changelog

Newest first. Every update gets a new version number, an entry here, and its own release notes file
(`.github/changes/<number>.md`), written as NEW / FIXED / CHANGES.

## v2

### NEW
- **Owner-only server control.** New "Sky Breaker Config" button on the Esc menu opens a pop-up with **Config** and **Commands**. Only the server owner can use it; anyone else is warned, blocked and logged (the server re-checks every packet, so a modified client cannot bypass it).
- **Config screen**: change who can fire, who gets the cutscene (shooter / owner only / everyone nearby), strike limits, safety, effects, ammo, chips and every cooldown without touching the file.
- **Commands screen + `/skybreaker`**: `reload`, `status`, `cancelall`, `clearcooldowns`, `set`, `allow`, `zone`, `dimension` and `owner`. All of them are owner-only (operators are NOT enough). `owner` is console-only.
- **Who can fire** (`firePermission`): EVERYONE, CREATIVE_ONLY, ALLOWLIST or OWNER_ONLY.
- **Protected zones and banned dimensions**: no strike can be aimed there, nothing inside is destroyed or damaged. Set a zone around spawn with one click.
- **Claim mod support**: asks FTB Chunks, Open Parties and Claims and others through the Common Protection API when it is installed (optional).
- **Strike limits**: `maxActiveStrikes` (server), `maxActiveStrikesPerPlayer` and a **global cooldown** after any shot, so one player can no longer chain all eight remotes.
- **Ammo**: every shot burns **Orbital Charges** (creative is exempt, cost per weapon is configurable).
- **Chips** (off-hand, one at a time): Targeting Chip (faster recharge), **Overcharge Chip** (+35% damage), **Efficiency Chip** (40% chance a shot is free).
- **Crafting progression**: Uplink Circuit -> Orbital Core -> remotes. Tier III remotes (Satellite Laser, Singularity Cannon, Tactical Nuke) are crafted FROM a lower remote.

### FIXED
- Strikes could be fired by anyone, anywhere, with no permission, zone or claim check.
- Unlimited simultaneous strikes could lag a server.

### CHANGES
- **All recipes are much harder** (see the README). Old recipes are replaced.
- Targeting Chip recharge bonus lowered from 40% to 25% (configurable).
- Config file gains new options; missing keys are filled with defaults, your existing values are kept.

## v1

### NEW
- Eight orbital strike remotes: Orbital Railgun, Satellite Laser, Meteor Barrage, Singularity Cannon, EMP Pulse, Orbital Napalm, Tactical Nuke and Tracking Strike, plus the Targeting Chip.
- Tracking Strike live lock: a ring on the ground, a ring around the target, a beam from the sky and a light trail follow the target every tick.
- `TRACKING` / `LOCKED` readout with live coordinates and range.
- Custom sounds, slow-motion impacts and a full config.

### FIXED
- Tracking Strike: you can see that it really tracks. No cutscene, you keep full control.
- The orbital cutscene no longer covers the whole countdown.
- No white fade before impact, so you see the strike land.
- FIRE / COLLAPSE text no longer blocks the middle of the screen.

### CHANGES
- Short 1.5 second orbital intro, then the countdown runs over the real world (slim bars, small timer, no tint).
- Clean particle textures: smooth, anti-aliased glows instead of blocky pixels.
- Every update gets its own release (v1, v2, v3, ...). Old releases are kept.
