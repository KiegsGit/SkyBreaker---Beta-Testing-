package com.skybreaker.client;

import com.skybreaker.FxPayload;
import com.skybreaker.ModItems;
import com.skybreaker.SkybreakerConfig;
import com.skybreaker.StrikeItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Cinematic overlay. Sequence: cut from black, glitching title card, letterbox + targeting HUD with the giant
 * countdown, then FIRE. The Singularity Cannon adds a darkening vignette and a status readout while the black hole
 * drags everything in, and a COLLAPSE call-out. A white impact flash sits on top of everything.
 */
final class CinematicHud {
	private static final String[] NAMES = {"", "ORBITAL RAILGUN", "SATELLITE LASER", "METEOR BARRAGE", "SINGULARITY CANNON",
		"EMP PULSE", "ORBITAL NAPALM", "TACTICAL NUKE", "TRACKING STRIKE"};
	private static final String[] SUBS = {"", "KINETIC ORBITAL BOMBARDMENT", "SUSTAINED SATELLITE BEAM",
		"KINETIC DEBRIS FIELD INBOUND", "SPACETIME COLLAPSE IMMINENT",
		"ELECTROMAGNETIC SHOCKWAVE", "INCENDIARY SATURATION STRIKE", "THERMONUCLEAR DETONATION", "TARGET-LOCKED KINETIC STRIKE"};
	private static final int CYAN = 0xFF7FE0FF;
	private static final int CYAN_DIM = 0xFF3FA8D8;
	private static final int VIOLET = 0xFFD08CFF;
	private static final int INTRO = 46;
	/** Four caption phases per weapon, shown as subtitles during the orbital view. */
	private static final String[][] CAPTIONS = {
		{},
		{"Guide laser locked", "Tungsten rod - orbital drop", "Capacitors charging...", "Firing solution confirmed"},
		{"Guide laser locked", "Helios Lance - solar orbit", "Mirror array focusing...", "Beam aligned"},
		{"Guide laser locked", "Debris field - capturing", "Trajectories computing...", "Impact vectors set"},
		{"Guide laser locked", "Singularity core - spooling", "Tearing spacetime...", "Event horizon stable"},
		{"Guide laser locked", "EMP emitter - powering up", "Capacitor bank charging...", "Pulse aligned"},
		{"Guide laser locked", "Thermite canisters - released", "Payload dispersing...", "Ignition sequence armed"},
		{"Guide laser locked", "Warhead armed - re-entry", "Safeties disengaged...", "Detonation confirmed"},
		{"Guide laser locked", "Target acquired", "Tracking movement...", "Lock confirmed"}
	};
	/** Ticks over which the camera is eased toward the target. */
	private static final int CAMERA_TICKS = 38;

	/** Length of the third-person orbit shot that opens the cutscene (ticks), 0 when disabled or the countdown is too short. */
	private static int orbit = 0;
	private static float startYaw = 0f;
	private static float startPitch = 0f;

	private static boolean active = false;
	private static int age = 0;
	private static int total = 60;
	private static int extra = 0;
	private static int weapon = 1;
	private static double distance = 0;
	private static double tx, ty, tz;

	private static float flashAlpha = 0f;
	private static float flashDecay = 0.05f;

	static void start(FxPayload p) {
		active = true;
		age = 0;
		total = Math.max(1, p.a());
		weapon = Math.max(1, Math.min(8, p.b()));
		extra = p.data().length > 0 ? Math.max(0, (int) p.data()[0]) : 0;
		distance = p.f();
		orbit = (SkybreakerConfig.get().cinematicCamera && total >= 50) ? 24 : 0;
		var pl = Minecraft.getInstance().player;
		if (pl != null) {
			startYaw = pl.getYRot();
			startPitch = pl.getXRot();
		}
		tx = p.x();
		ty = p.y();
		tz = p.z();
	}

	static void flash(float intensity, int ticks) {
		if (intensity > flashAlpha) {
			flashAlpha = Math.min(1f, intensity);
		}
		flashDecay = Math.max(0.01f, flashAlpha / Math.max(1, ticks));
	}

	/** True while the camera should be in third person (the orbit shot plus a short crossfade into the space view). */
	static boolean orbitCamera() {
		return active && orbit > 0 && age < orbit + 8;
	}

	/** True during the orbit shot itself, when the view is swung around the player by FxManager. */
	static boolean orbitActive() {
		return active && orbit > 0 && age < orbit;
	}

	static float orbitProgress() {
		return orbit <= 0 ? 1f : clamp01(age / (float) orbit);
	}

	static float orbitStartYaw() {
		return startYaw;
	}

	static float orbitStartPitch() {
		return startPitch;
	}

	static boolean isActive() {
		return active;
	}

	static void clear() {
		active = false;
		flashAlpha = 0f;
	}

	static void tick() {
		if (active) {
			age++;
			if (age > total + extra + 26) {
				active = false;
			}
		}
		if (flashAlpha > 0f) {
			flashAlpha = Math.max(0f, flashAlpha - flashDecay);
		}
	}

	/** True while the camera should still be easing toward the target. */
	static boolean cameraActive() {
		return active && age < CAMERA_TICKS;
	}

	/** The point the camera looks at (for the black hole: the point where it hangs). */
	static Vec3 lookTarget() {
		return new Vec3(tx, ty + (weapon == 4 ? FxPayload.BH_HEIGHT : 0.0), tz);
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static String spaced(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			b.append(s.charAt(i));
			if (i < s.length() - 1 && s.charAt(i) != ' ') {
				b.append(' ');
			}
		}
		return b.toString();
	}

	/** Darkens the screen edges with stacked translucent bands. */
	private static void vignette(GuiGraphicsExtractor g, int w, int h, int alphaPerLayer, int rgb) {
		for (int i = 0; i < 9; i++) {
			int bandY = (i + 1) * h / 34;
			int bandX = (i + 1) * w / 48;
			int c = argb(alphaPerLayer, rgb);
			g.fill(0, 0, w, bandY, c);
			g.fill(0, h - bandY, w, h, c);
			g.fill(0, bandY, bandX, h - bandY, c);
			g.fill(w - bandX, bandY, w, h - bandY, c);
		}
	}

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		var font = Minecraft.getInstance().font;

		if (active) {
			boolean bh = weapon == 4;
			int pullEnd = total + extra;
			boolean pulling = bh && age >= total && age < pullEnd;

			float in = Math.min(1f, age / 10f);
			float out = Math.max(0f, Math.min(1f, (age - (pullEnd + 10)) / 14f));
			float bar = in * (1f - out);
			int barH = (int) (h * 0.12f * bar);

			// tint: cold blue, red in the final second; the black hole goes deep violet instead
			int remaining = total - age;
			if (pulling) {
				float pp = (age - total) / (float) Math.max(1, extra);
				g.fill(0, 0, w, h, argb((int) (20 + 46 * pp), 0x20003A));
				vignette(g, w, h, (int) (10 + 22 * pp), 0x0A0014);
			} else if (remaining > 0 && remaining < 20) {
				g.fill(0, 0, w, h, argb((int) (46 * (1f - remaining / 20f)), bh ? 0x9030FF : 0xFF2030));
			} else {
				g.fill(0, 0, w, h, argb((int) (14 * bar), bh ? 0x7030FF : 0x2090FF));
			}
			vignette(g, w, h, (int) (7 * bar), 0x000000);

			g.fill(0, 0, w, barH, 0xFF000000);
			g.fill(0, h - barH, w, h, 0xFF000000);

			int accent = bh ? VIOLET : CYAN;
			int accentDim = bh ? 0xFF9A5CD0 : CYAN_DIM;
			int bracketRgb = bh ? 0xC070FF : 0x5CD6FF;

			if (bar > 0.6f) {
				g.centeredText(font, "SKYBREAKER // " + NAMES[weapon], w / 2, Math.max(2, barH / 2 - 4), accent);
				String line = pulling
					? "SINGULARITY ACTIVE   T+" + (age - total) / 20 + "s   EVENT HORIZON EXPANDING"
					: "TGT " + (int) Math.floor(tx) + " " + (int) Math.floor(ty) + " " + (int) Math.floor(tz) + "   RANGE " + (int) distance + " m";
				g.centeredText(font, line, w / 2, h - barH / 2 - 4, accentDim);
			}

			// corner brackets
			int m = barH + 12;
			int len = 26;
			int col = argb(150 + (int) (90 * Math.abs(Math.sin(age * 0.25))), bracketRgb);
			bracket(g, m, m, len, 1, 1, col);
			bracket(g, w - m, m, len, -1, 1, col);
			bracket(g, m, h - m, len, 1, -1, col);
			bracket(g, w - m, h - m, len, -1, -1, col);

			// scanline sweeping down the screen
			int span = Math.max(1, h - 2 * barH);
			int sy = barH + (age * 9) % span;
			g.fill(0, sy, w, sy + 1, argb(70, bracketRgb));

			if (age < total) {
				float prog = age / (float) total;
				int secs = (total - age + 19) / 20;
				float frac = ((total - age) % 20) / 20f;
				float scale = 4f + 3f * frac;
				g.pose().pushMatrix();
				g.pose().translate(w / 2f, h / 2f - 34f);
				g.pose().scale(scale);
				g.centeredText(font, String.valueOf(secs), 0, 0, remaining < 20 ? 0xFFFF5060 : 0xFFFFFFFF);
				g.pose().popMatrix();

				int bw = 150;
				int x0 = w / 2 - bw / 2;
				int y0 = h / 2 + 28;
				g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 5, 0xAA000000);
				g.fill(x0, y0, x0 + (int) (bw * prog), y0 + 4, accent);
				String lock = bh ? (prog > 0.92f ? "TEAR STABLE" : "OPENING  " + (int) (prog * 100) + "%")
					: (prog > 0.92f ? "TARGET LOCKED" : "LOCKING  " + (int) (prog * 100) + "%");
				g.centeredText(font, lock, w / 2, y0 + 10, accent);
			} else if (pulling) {
				float pp = (age - total) / (float) Math.max(1, extra);
				int bw = 190;
				int x0 = w / 2 - bw / 2;
				int y0 = h - barH - 26;
				g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 5, 0xAA000000);
				g.fill(x0, y0, x0 + (int) (bw * pp), y0 + 4, argb(255, 0xC070FF));
				g.centeredText(font, pp > 0.85f ? "COLLAPSE IMMINENT" : "EVENT HORIZON  " + (int) (pp * 100) + "%", w / 2, y0 + 8, VIOLET);
			} else if (age < pullEnd + 14) {
				float pop = 1f - (age - pullEnd) / 14f;
				g.pose().pushMatrix();
				g.pose().translate(w / 2f, h / 2f - 20f);
				g.pose().scale(5f + 3f * pop);
				g.centeredText(font, bh ? "COLLAPSE" : "FIRE", 0, 0, argb((int) (255 * Math.min(1f, pop + 0.3f)), bh ? 0xE0A0FF : 0xFF4050));
				g.pose().popMatrix();
			}

			if (age < total) {
				// every weapon opens with its own 3D orbital strike view (replaces the old title card)
				if (age >= orbit) {
					OrbitalView.render(g, w, h, weapon, age - orbit, total - orbit, (int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz), (int) distance);
				}
				caption(g, font, w, h, barH, accent);
				recIndicator(g, font, barH);
				// brighten into the shot as the beam is about to fire
				if (remaining <= 6) {
					g.fill(0, 0, w, h, argb((int) (150 * (1f - remaining / 6f)), 0xFFFFFF));
				}
			}
		}

		if (flashAlpha > 0.01f) {
			g.fill(0, 0, w, h, argb((int) (Math.min(1f, flashAlpha) * 255), 0xEAF6FF));
		}
	}

	/** Subtitle line in the style of the reference video ("Mirror array focusing..."). */
	private static void caption(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int w, int h, int barH, int accent) {
		String[] lines = CAPTIONS[weapon];
		if (lines.length == 0 || barH < 4) {
			return;
		}
		int phase;
		float vis;
		if (age < orbit) {
			phase = 0;
			vis = Math.min(1f, Math.min(age / 5f, (orbit - age) / 5f));
		} else {
			float prog = clamp01((age - orbit) / (float) Math.max(1, total - orbit));
			float scaled = prog * 3f;
			int idx = Math.min(2, (int) scaled);
			float local = scaled - idx;
			phase = idx + 1;
			vis = Math.min(1f, Math.min(local * 6f, (1f - local) * 6f));
			if (idx == 2) {
				vis = Math.min(1f, local * 6f);
			}
		}
		if (vis <= 0.02f) {
			return;
		}
		String text = lines[phase];
		int tw = font.width(text);
		int y = h - barH - 44;
		g.fill(w / 2 - tw / 2 - 6, y - 3, w / 2 + tw / 2 + 6, y + 11, argb((int) (150 * vis), 0x000000));
		g.centeredText(font, text, w / 2, y, argb((int) (255 * vis), accent & 0xFFFFFF));
	}

	/** Blinking REC marker in the top-left, under the letterbox. */
	private static void recIndicator(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int barH) {
		int x = 22;
		int y = barH + 14;
		if ((age / 10) % 2 == 0) {
			g.fill(x, y + 1, x + 6, y + 7, 0xFFFF2030);
		}
		g.centeredText(font, "REC", x + 12 + font.width("REC") / 2, y, 0xFFE0E0E0);
	}

	/** Cut from black into a glitching title card. */
	private static void intro(GuiGraphicsExtractor g, int w, int h, int accent) {
		var font = Minecraft.getInstance().font;
		int black = (int) (255 * Math.max(0f, 1f - age / 14f));
		if (black > 0) {
			g.fill(0, 0, w, h, argb(black, 0x000000));
		}
		float fade = Math.min(clamp01((age - 3) / 8f), clamp01((INTRO - age) / 9f));
		if (fade <= 0f) {
			return;
		}
		int a = (int) (255 * fade);
		String title = spaced(NAMES[weapon]);
		boolean glitch = age >= 5 && age <= 13 && age % 2 == 1;
		float scale = 2.6f + 0.5f * clamp01(age / (float) INTRO);
		int cy = h / 2 - 30;

		g.centeredText(font, "S K Y B R E A K E R   I N D U S T R I E S", w / 2, cy - 22, argb((int) (a * 0.7f), 0xFFFFFF));
		if (glitch) {
			g.pose().pushMatrix();
			g.pose().translate(w / 2f - 3f, cy);
			g.pose().scale(scale);
			g.centeredText(font, title, 0, 0, argb((int) (a * 0.55f), 0xFF2040));
			g.pose().popMatrix();
			g.pose().pushMatrix();
			g.pose().translate(w / 2f + 3f, cy);
			g.pose().scale(scale);
			g.centeredText(font, title, 0, 0, argb((int) (a * 0.55f), 0x2080FF));
			g.pose().popMatrix();
		}
		g.pose().pushMatrix();
		g.pose().translate(w / 2f, cy);
		g.pose().scale(scale);
		g.centeredText(font, title, 0, 0, argb(a, accent & 0xFFFFFF));
		g.pose().popMatrix();

		int lineW = (int) (w * 0.34f * fade);
		g.fill(w / 2 - lineW, cy + 22, w / 2 + lineW, cy + 23, argb(a, accent & 0xFFFFFF));
		g.centeredText(font, SUBS[weapon], w / 2, cy + 30, argb((int) (a * 0.85f), accent & 0xFFFFFF));
		g.centeredText(font, "AUTHORIZATION CONFIRMED", w / 2, cy + 44, argb((int) (a * 0.5f), 0xFFFFFF));

		// faint scanlines over the card
		for (int y = 0; y < h; y += 4) {
			g.fill(0, y, w, y + 1, argb((int) (22 * fade), 0x000000));
		}
	}


	private static void bracket(GuiGraphicsExtractor g, int x, int y, int len, int dx, int dy, int color) {
		// horizontal arm
		g.fill(Math.min(x, x + dx * len), Math.min(y, y + dy * 2), Math.max(x, x + dx * len), Math.max(y, y + dy * 2), color);
		// vertical arm
		g.fill(Math.min(x, x + dx * 2), Math.min(y, y + dy * len), Math.max(x, x + dx * 2), Math.max(y, y + dy * len), color);
	}

	private CinematicHud() {
	}
}

/**
 * Status readout shown above the hotbar while a Skybreaker remote is in hand: model code + name, READY / RECHARGING
 * (with a charge bar) and target range, and the controls. Pure fills + text, a few draw calls.
 */
final class RemoteHud {
	private static final String[] CODE = {"OA-1", "OA-2", "OA-3", "OA-4", "OA-5", "OA-6", "OA-7", "OA-8"};
	private static final int[] ACCENT = {0xFFFFB030, 0xFFFF3040, 0xFFFF6A20, 0xFFC070FF, 0xFF3CD2FF, 0xFFFFE650, 0xFF78FF46, 0xFF28FFAA};

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || CinematicHud.isActive()) {
			return;
		}
		ItemStack stack = heldStack(mc);
		if (stack == null) {
			return;
		}
		StrikeItem item = (StrikeItem) stack.getItem();
		int k = item.kind.ordinal();
		var font = mc.font;

		// the server puts a vanilla item cooldown on the remote; its remaining fraction (1 -> 0) drives the bar
		float pct = mc.player.getCooldowns().getCooldownPercent(stack, 0.0f);
		boolean charging = pct > 0.001f;

		String name = CODE[k] + " " + item.kind.display;
		String status;
		int statusColor;
		if (charging) {
			status = "RECHARGING " + (int) Math.ceil(pct * cooldownSeconds(item.kind) * (hasChip(mc) ? 0.6 : 1.0)) + "s";
			statusColor = 0xFFFFAA33;
		} else {
			double d = FxManager.aimDistance;
			boolean locked = d >= 0.0;
			status = locked ? "READY  " + (int) d + " m" : "NO TARGET";
			statusColor = locked ? 0xFF55FF77 : 0xFFFF6060;
		}
		String hint = charging ? "Please wait" : "Right-click: fire";
		String sep = "  |  ";

		int wName = font.width(name);
		int wStat = font.width(status);
		int wHint = font.width(hint);
		int wSep = font.width(sep);
		int total = wName + wSep + wStat + wSep + wHint;

		int w = g.guiWidth();
		int h = g.guiHeight();
		int left = w / 2 - total / 2;
		int y = h - 62;
		g.fill(left - 6, y - 3, left + total + 6, y + 11, 0x99000000);
		g.fill(left - 6, y - 3, left - 4, y + 11, ACCENT[k]);
		if (charging) {
			// charge bar along the bottom edge of the panel: fills up as the remote recharges
			int bw = total + 12;
			g.fill(left - 6, y + 11, left - 6 + bw, y + 13, 0x99000000);
			g.fill(left - 6, y + 11, left - 6 + (int) (bw * (1f - pct)), y + 13, ACCENT[k]);
		}

		int x = left;
		g.centeredText(font, name, x + wName / 2, y, ACCENT[k]);
		x += wName;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, status, x + wStat / 2, y, statusColor);
		x += wStat;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, hint, x + wHint / 2, y, 0xFFB8B8B8);
	}

	/** Configured cooldown in seconds (used to turn the cooldown fraction into a countdown). */
	private static double cooldownSeconds(StrikeItem.Kind kind) {
		SkybreakerConfig c = SkybreakerConfig.get();
		return switch (kind) {
			case RAILGUN -> c.railgunCooldownSeconds;
			case LASER -> c.laserCooldownSeconds;
			case METEOR -> c.meteorCooldownSeconds;
			case BLACKHOLE -> c.blackHoleCooldownSeconds;
			case EMP -> c.empCooldownSeconds;
			case NAPALM -> c.napalmCooldownSeconds;
			case NUKE -> c.nukeCooldownSeconds;
			case TRACKING -> c.trackCooldownSeconds;
		};
	}

	/** A Targeting Chip in either hand (the server only counts the hand that does not hold the remote). */
	private static boolean hasChip(Minecraft mc) {
		return mc.player.getOffhandItem().is(ModItems.TARGETING_CHIP) || mc.player.getMainHandItem().is(ModItems.TARGETING_CHIP);
	}

	private static ItemStack heldStack(Minecraft mc) {
		ItemStack main = mc.player.getMainHandItem();
		if (main.getItem() instanceof StrikeItem) {
			return main;
		}
		ItemStack off = mc.player.getOffhandItem();
		if (off.getItem() instanceof StrikeItem) {
			return off;
		}
		return null;
	}

	private RemoteHud() {
	}
}
