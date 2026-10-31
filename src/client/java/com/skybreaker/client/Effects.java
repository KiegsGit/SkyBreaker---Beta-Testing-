package com.skybreaker.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import com.skybreaker.FxPayload;
import com.skybreaker.ModParticles;
import com.skybreaker.SkybreakerConfig;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** One running client-side visual effect. */
abstract class Fx {
	protected final Vec3 pos;
	protected int age = 0;

	Fx(Vec3 pos) {
		this.pos = pos;
	}

	/** Advance one tick; return true when finished. */
	abstract boolean tick(ClientLevel level);
}

/**
 * Skybreaker's own lightning. A jagged trunk (recursive midpoint displacement) with forked branches is built from a
 * seed and drawn out of glowing violet halo + white-hot core particles. It flickers in three pulses and ends in a
 * flash and a spray of sparks where it hits the ground.
 */
final class BoltFx extends Fx {
	private static final double SKY = 130.0;

	private final List<Vec3> trunk;
	private final List<List<Vec3>> branches = new ArrayList<>();
	private final Random rng;

	BoltFx(Vec3 pos, int seed, float power) {
		super(pos);
		this.rng = new Random(seed);
		Vec3 top = new Vec3(pos.x + (rng.nextDouble() - 0.5) * 36.0, pos.y + SKY, pos.z + (rng.nextDouble() - 0.5) * 36.0);
		this.trunk = jagged(top, pos, 0.17, 7, rng);
		int forks = 4 + rng.nextInt(4);
		for (int i = 0; i < forks; i++) {
			int idx = (int) (trunk.size() * (0.22 + rng.nextDouble() * 0.66));
			Vec3 from = trunk.get(Math.min(trunk.size() - 1, idx));
			double len = 14.0 + rng.nextDouble() * 28.0;
			double ang = rng.nextDouble() * Math.PI * 2.0;
			Vec3 to = new Vec3(from.x + Math.cos(ang) * len * 0.8, from.y - len * (0.35 + rng.nextDouble() * 0.5), from.z + Math.sin(ang) * len * 0.8);
			branches.add(jagged(from, to, 0.2, 5, rng));
		}
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age == 1) {
			draw(trunk, 0.9, 0.42, 1.0, true);
			for (List<Vec3> b : branches) {
				draw(b, 1.1, 0.7, 0.8, false);
			}
			impact();
		} else if (age == 3) {
			draw(trunk, 1.0, 0.5, 0.65, true);
			for (List<Vec3> b : branches) {
				draw(b, 1.3, 0.9, 0.5, false);
			}
		} else if (age == 5) {
			draw(trunk, 1.2, 0.7, 0.45, true);
		}
		return age > 7;
	}

	private void impact() {
		Pt.add(ModParticles.FLARE, pos.x, pos.y + 0.6, pos.z, 0, 0, 0);
		Pt.burst(ModParticles.SPARK, new Vec3(pos.x, pos.y + 0.3, pos.z), 26, 0.25, 0.9, 0.3);
		Pt.ring(ModParticles.PLASMA, pos, 1.2, 14, Pt.rnd() * 6.28, 0.2, 0.28, 0.02);
		for (int i = 0; i < 3; i++) {
			Pt.add(ModParticles.SMOKE, pos.x + Pt.sym() * 1.2, pos.y + 0.4, pos.z + Pt.sym() * 1.2, 0, 0.08, 0);
		}
	}

	/** Draws one pulse of a polyline. {@code keep} = chance each sample is drawn (makes the flicker). */
	static void draw(List<Vec3> pts, double haloStep, double coreStep, double keep, boolean bright) {
		walk(pts, haloStep, keep, p -> Pt.add(ModParticles.BOLT, p.x, p.y, p.z, 0, 0, 0));
		walk(pts, coreStep, keep, p -> Pt.add(ModParticles.CORE, p.x + Pt.sym() * 0.05, p.y, p.z + Pt.sym() * 0.05, 0, 0, 0));
		if (bright) {
			walk(pts, haloStep * 3.0, keep, p -> Pt.add(ModParticles.PLASMA, p.x, p.y, p.z, 0, 0, 0));
		}
	}

	/** A short arc between two points (used for crackling energy around the black hole). */
	static void arc(Vec3 a, Vec3 b, long seed) {
		List<Vec3> pts = jagged(a, b, 0.22, 5, new Random(seed));
		walk(pts, 0.45, 1.0, p -> Pt.add(ModParticles.ZAP, p.x, p.y, p.z, 0, 0, 0));
		walk(pts, 1.1, 1.0, p -> Pt.add(ModParticles.WISP, p.x, p.y, p.z, 0, 0, 0));
	}

	private interface Sampler {
		void at(Vec3 p);
	}

	private static void walk(List<Vec3> pts, double step, double keep, Sampler s) {
		for (int i = 0; i < pts.size() - 1; i++) {
			Vec3 a = pts.get(i);
			Vec3 b = pts.get(i + 1);
			double len = a.distanceTo(b);
			int n = Math.max(1, (int) Math.ceil(len / step));
			for (int k = 0; k < n; k++) {
				if (keep < 1.0 && Pt.rnd() > keep) {
					continue;
				}
				double u = k / (double) n;
				s.at(new Vec3(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, a.z + (b.z - a.z) * u));
			}
		}
	}

	/** Recursive midpoint displacement: every pass splits each segment and pushes the middle sideways. */
	static List<Vec3> jagged(Vec3 a, Vec3 b, double relJitter, int depth, Random r) {
		List<Vec3> pts = new ArrayList<>();
		pts.add(a);
		pts.add(b);
		double amp = relJitter;
		for (int d = 0; d < depth; d++) {
			List<Vec3> next = new ArrayList<>(pts.size() * 2);
			for (int i = 0; i < pts.size() - 1; i++) {
				Vec3 p = pts.get(i);
				Vec3 q = pts.get(i + 1);
				Vec3 along = q.subtract(p);
				double len = along.length();
				Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
				if (len > 1.0e-6) {
					Vec3 u = along.scale(1.0 / len);
					off = off.subtract(u.scale(off.dot(u)));
				}
				Vec3 mid = new Vec3((p.x + q.x) * 0.5, (p.y + q.y) * 0.5, (p.z + q.z) * 0.5).add(off.scale(amp * len * 0.5));
				next.add(p);
				next.add(mid);
			}
			next.add(pts.get(pts.size() - 1));
			pts = next;
			amp *= 0.58;
		}
		return pts;
	}
}

/** Targeting ring + converging lock-on, orbital beam, descending bolt, impact flash and expanding shockwave. */
final class RailgunFx extends Fx {
	private static final double SKY = 110.0;
	private final int countdown;
	private final int bolt;
	private final double radius;

	RailgunFx(Vec3 pos, int countdown, int bolt, double radius) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.bolt = bolt;
		this.radius = radius;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			charge();
		} else if (age <= countdown + bolt) {
			bolt();
		} else {
			aftermath();
		}
		return age > countdown + bolt + 45;
	}

	private void charge() {
		double prog = age / (double) countdown;
		double rot = age * 0.18;
		// fixed targeting ring: continuous thin line plus four evenly spaced ticks
		Pt.ring(ModParticles.ZAP, pos, 3.6, 72, 0.0, 0.12, 0.0, 0.0);
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2.0 + Math.PI / 4.0;
			for (int s = 0; s < 4; s++) {
				double r = 2.6 + s * 0.5;
				Pt.add(ModParticles.ZAP, pos.x + Math.cos(a) * r, pos.y + 0.12, pos.z + Math.sin(a) * r, 0, 0, 0);
			}
		}
		// converging lock-on ring, one clean circle that closes in
		double r2 = 8.0 - 6.2 * prog;
		Pt.ring(ModParticles.PLASMA, pos, r2, 48, rot, 0.15, 0.0, 0.0);
		// guide beam: a straight line that gets denser and grows a thin tube as the shot approaches
		double step = 3.0 - 2.0 * prog;
		Pt.column(ModParticles.ZAP, pos.x, pos.y, pos.z, (age * 1.7) % step, 95.0, step);
		if (prog > 0.5) {
			Pt.tube(ModParticles.ZAP, pos.x, pos.y, pos.z, 0.0, 95.0, 0.25 + 0.25 * prog, 6, 6.0 - 3.0 * prog, rot);
		}
		// data packets sliding down the beam
		if (age % 3 == 0) {
			for (int i = 0; i < 3; i++) {
				Pt.add(ModParticles.CORE, pos.x, pos.y + 95.0 - i * 18.0, pos.z, 0, -2.4, 0);
			}
		}
		// ground ring in the last second
		if (age > countdown - 20) {
			double k = (age - (countdown - 20)) / 20.0;
			Pt.ring(ModParticles.CORE, pos, 2.4 * (1.0 - k) + 0.4, 36, -rot, 0.1, 0.0, 0.0);
		}
	}

	private void bolt() {
		double t = (age - countdown) / (double) bolt;
		double head = SKY * (1.0 - t);
		// the beam: white centre line, a tight tube, and a wide faint tube, all perfectly straight
		Pt.column(ModParticles.CORE, pos.x, pos.y, pos.z, head, SKY, 1.0);
		Pt.tube(ModParticles.CORE, pos.x, pos.y, pos.z, head, SKY, 0.8, 10, 4.0, 0.0);
		Pt.tube(ModParticles.ZAP, pos.x, pos.y, pos.z, head, SKY, 1.7, 14, 8.0, 0.4);
		// shock ring riding the head of the beam
		Pt.ring(ModParticles.PLASMA, new Vec3(pos.x, pos.y + head, pos.z), 2.4, 28, age * 0.5, 0.0, 0.0, 0.0);
		Pt.ring(ModParticles.ZAP, pos, 1.5, 24, 0.0, 0.2, 0.5, 0.0);
		if (age == countdown + bolt) {
			impact();
		}
	}

	private void impact() {
		Vec3 c = new Vec3(pos.x, pos.y + 0.5, pos.z);
		Pt.add(ModParticles.FLARE, c.x, c.y + 0.5, c.z, 0, 0, 0);
		Pt.explosion(c, 4.0);
		// radial light streaks along the ground, then two clean expanding shock rings
		Pt.spokes(ModParticles.ZAP, c, 36, 1.2, 13.0, 1.0, 0.1, 0.45, 0.0);
		Pt.ring(ModParticles.CORE, c, 1.0, 96, 0.0, 0.15, 1.15, 0.0);
		Pt.ring(ModParticles.PLASMA, c, 1.0, 64, 0.0, 0.4, 0.6, 0.0);
		// a short, controlled burst of sparks instead of a big random cloud
		Pt.burst(ModParticles.SPARK, c, 70, 0.5, 1.8, 0.4);
		Pt.burst(ModParticles.DUST, c, 40, 0.3, 1.1, 0.5);
	}

	private void aftermath() {
		int s = age - (countdown + bolt);
		double maxR = radius * 2.3;
		double r = Math.min(maxR, s * 0.95);
		int n = (int) Math.min(220, 40 + r * 8);
		Pt.ring(ModParticles.ZAP, pos, r, n, 0.0, 0.3, 0.0, 0.02);
		Pt.ring(ModParticles.DUST, pos, r * 0.85, n / 5, 0.5, 0.4, 0.04, 0.05);
		if (s < 12) {
			// the beam column fades out by thinning, not by flickering
			double stepUp = 1.0 + s * 0.35;
			Pt.column(ModParticles.CORE, pos.x, pos.y, pos.z, 0.0, 80.0, stepUp);
			Pt.tube(ModParticles.ZAP, pos.x, pos.y, pos.z, 0.0, 80.0, 0.8 + s * 0.12, 10, 5.0 + s, 0.0);
		}
		if (s < 35) {
			for (int i = 0; i < 4; i++) {
				Pt.add(ModParticles.SMOKE, pos.x + Pt.sym() * radius * 0.5, pos.y + 0.5, pos.z + Pt.sym() * radius * 0.5, 0, 0.12 + Pt.rnd() * 0.1, 0);
			}
		}
	}
}

/** Charging orb in the sky, converging lock-on, then a sustained blue-white beam with spiral and ground sparks. */
final class LaserFx extends Fx {
	private static final double SKY = 120.0;
	private final int countdown;
	private final int duration;
	private final double radius;

	LaserFx(Vec3 pos, int countdown, int duration, double radius) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.duration = duration;
		this.radius = radius;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			charge();
		} else if (age <= countdown + duration) {
			sustain();
		} else {
			fade();
		}
		return age > countdown + duration + 14;
	}

	private void orb(double size, int count, double inward) {
		Vec3 top = new Vec3(pos.x, pos.y + SKY - 8.0, pos.z);
		for (int i = 0; i < count; i++) {
			double vx = Pt.sym();
			double vy = Pt.sym();
			double vz = Pt.sym();
			double len = Math.max(0.001, Math.sqrt(vx * vx + vy * vy + vz * vz));
			double r = size * (0.7 + 0.3 * Pt.rnd());
			Pt.add(ModParticles.CORE, top.x + vx / len * r, top.y + vy / len * r, top.z + vz / len * r,
				-vx / len * inward, -vy / len * inward, -vz / len * inward);
		}
	}

	private void charge() {
		double prog = age / (double) countdown;
		double rot = age * 0.25;
		Pt.ring(ModParticles.PLASMA, pos, radius + 1.5, 36, rot * 0.4, 0.12, 0.0, 0.0);
		double r2 = (radius + 7.0) * (1.0 - prog) + radius * 0.5;
		Pt.ring(ModParticles.ZAP, pos, r2, 28, -rot, 0.15, 0.0, 0.0);
		// double helix climbing a thin guide beam
		if (age % 2 == 0) {
			for (int i = 0; i < 20; i++) {
				double h = i * 5.0;
				double ang = h * 0.35 + age * 0.35;
				Pt.add(ModParticles.CORE, pos.x + Math.cos(ang) * 1.0, pos.y + h, pos.z + Math.sin(ang) * 1.0, 0, 0, 0);
				Pt.add(ModParticles.CORE, pos.x - Math.cos(ang) * 1.0, pos.y + h, pos.z - Math.sin(ang) * 1.0, 0, 0, 0);
			}
		}
		int n = 2 + (int) (prog * 18);
		for (int i = 0; i < n; i++) {
			Pt.add(ModParticles.ZAP, pos.x + Pt.sym() * 0.15, pos.y + Pt.rnd() * SKY, pos.z + Pt.sym() * 0.15, 0, 0, 0);
		}
		orb(0.6 + 3.0 * prog, 6 + (int) (prog * 14), 0.25);
	}

	private void sustain() {
		int t = age - countdown;
		double core = radius * 0.35;
		for (int i = 0; i < 95; i++) {
			Pt.add(ModParticles.ZAP, pos.x + Pt.sym() * core, pos.y + Pt.rnd() * SKY, pos.z + Pt.sym() * core, 0, 0, 0);
		}
		for (int i = 0; i < 26; i++) {
			double a = Pt.rnd() * Math.PI * 2.0;
			double rr = Pt.rnd() * core * 0.7;
			Pt.add(ModParticles.CORE, pos.x + Math.cos(a) * rr, pos.y + Pt.rnd() * SKY, pos.z + Math.sin(a) * rr, 0, -0.7, 0);
		}
		for (int i = 0; i < 30; i++) {
			double h = i * 4.0;
			double ang = h * 0.3 - age * 0.5;
			double rr = radius * 0.75;
			Pt.add(ModParticles.PLASMA, pos.x + Math.cos(ang) * rr, pos.y + h, pos.z + Math.sin(ang) * rr, 0, 0, 0);
		}
		// where the beam meets the ground
		Pt.ring(ModParticles.EMBER, pos, radius, 24, age * 0.3, 0.1, 0.06, 0.04);
		Pt.burst(ModParticles.ZAP, new Vec3(pos.x, pos.y + 0.3, pos.z), 8, 0.3, 0.9, 0.35);
		for (int i = 0; i < 2; i++) {
			Pt.add(ModParticles.SMOKE, pos.x + Pt.sym() * radius * 0.5, pos.y + 0.3, pos.z + Pt.sym() * radius * 0.5, 0, 0.15, 0);
		}
		if (t % 10 == 0) {
			Pt.ring(ModParticles.DUST, pos, 0.5, 20, 0, 0.2, 0.35, 0.02);
		}
		orb(3.4, 10, 0.0);
	}

	private void fade() {
		int s = age - (countdown + duration);
		double k = 1.0 - s / 14.0;
		int n = (int) (60 * k);
		for (int i = 0; i < n; i++) {
			Pt.add(ModParticles.ZAP, pos.x + Pt.sym() * radius * 0.3, pos.y + Pt.rnd() * SKY * k, pos.z + Pt.sym() * radius * 0.3, 0, 0, 0);
		}
		if (s == 1) {
			Vec3 c = new Vec3(pos.x, pos.y + 0.5, pos.z);
			Pt.explosion(c, 4.0);
			Pt.burst(ModParticles.CORE, c, 90, 0.4, 1.6, 0.2);
		}
	}
}

/** Area marker ring, then every meteor falls with its own fiery trail and explodes on impact. */
final class MeteorFx extends Fx {
	private final int countdown;
	private final int fall;
	private final double impactRadius;
	private final double areaRadius;
	private final int count;
	private final int[] delay;
	private final Vec3[] impact;
	private int maxDelay = 0;

	MeteorFx(Vec3 pos, int countdown, int fall, double impactRadius, float[] data) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.fall = Math.max(1, fall);
		this.impactRadius = impactRadius;
		this.areaRadius = data.length > 0 ? data[0] : 10.0;
		this.count = Math.max(0, (data.length - 1) / 4);
		this.delay = new int[count];
		this.impact = new Vec3[count];
		for (int i = 0; i < count; i++) {
			delay[i] = (int) data[1 + i * 4];
			maxDelay = Math.max(maxDelay, delay[i]);
			impact[i] = new Vec3(pos.x + data[2 + i * 4], pos.y + data[3 + i * 4], pos.z + data[4 + i * 4]);
		}
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			marker();
		}
		for (int i = 0; i < count; i++) {
			int start = countdown + delay[i];
			if (age >= start - 20 && age < start + fall && age % 4 == 0) {
				Pt.ring(ModParticles.EMBER, impact[i], impactRadius * 0.8, 10, age * 0.2, 0.15, 0.0, 0.0);
			}
			if (age >= start && age < start + fall) {
				trail(i, age - start);
			} else if (age == start + fall) {
				boom(i);
			}
		}
		return age > countdown + maxDelay + fall + 20;
	}

	private void marker() {
		double prog = age / (double) countdown;
		double rot = age * 0.12;
		int n = (int) Math.min(220, areaRadius * 9);
		Pt.ring(ModParticles.PLASMA, pos, areaRadius, n, rot * 0.3, 0.15, 0.0, 0.0);
		Pt.ring(ModParticles.ZAP, pos, areaRadius * (1.0 - 0.75 * prog), n / 2, -rot, 0.2, 0.0, 0.0);
		for (int k = 0; k < 6; k++) {
			double a = rot + k * Math.PI / 3.0;
			for (int s = 1; s <= 5; s++) {
				double r = areaRadius * s / 5.0 * 0.95;
				Pt.add(ModParticles.ZAP, pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r, 0, 0, 0);
			}
		}
		for (int i = 0; i < 3 + (int) (prog * 12); i++) {
			Pt.add(ModParticles.ZAP, pos.x + Pt.sym() * 0.2, pos.y + Pt.rnd() * 90.0, pos.z + Pt.sym() * 0.2, 0, 0, 0);
		}
	}

	private Vec3 at(int i, double p) {
		double f = FxPayload.meteorFactor(p);
		return new Vec3(impact[i].x + FxPayload.ENTRY_X * f, impact[i].y + FxPayload.ENTRY_Y * f, impact[i].z + FxPayload.ENTRY_Z * f);
	}

	private void trail(int i, int t) {
		double p0 = Math.max(0.0, (t - 1) / (double) fall);
		double p1 = t / (double) fall;
		Vec3 a = at(i, p0);
		Vec3 b = at(i, p1);
		for (int k = 0; k <= 6; k++) {
			double u = k / 6.0;
			double x = a.x + (b.x - a.x) * u;
			double y = a.y + (b.y - a.y) * u;
			double z = a.z + (b.z - a.z) * u;
			Pt.add(ModParticles.EMBER, x + Pt.sym() * 0.3, y + Pt.sym() * 0.3, z + Pt.sym() * 0.3, Pt.sym() * 0.03, Pt.sym() * 0.03, Pt.sym() * 0.03);
			if (k % 2 == 0) {
				Pt.add(ModParticles.SMOKE, x, y, z, 0, 0.02, 0);
			}
		}
		Pt.burst(ModParticles.EMBER, b, 8, 0.02, 0.15, 0.0);
		Pt.add(ModParticles.CORE, b.x, b.y, b.z, 0, 0, 0);
	}

	private void boom(int i) {
		Vec3 c = new Vec3(impact[i].x, impact[i].y + 0.4, impact[i].z);
		Pt.explosion(c, impactRadius * 0.55);
		Pt.burst(ModParticles.EMBER, c, 60, 0.2, 1.2, 0.25);
		Pt.burst(ModParticles.SMOKE, c, 30, 0.1, 0.6, 0.3);
		Pt.burst(ModParticles.EMBER, c, 12, 0.2, 0.8, 0.3);
		Pt.ring(ModParticles.DUST, c, impactRadius * 0.5, 28, 0.0, 0.1, 0.55, 0.04);
	}
}

/**
 * Singularity Cannon visuals.
 * Intro: violet energy streams in, a ground sigil spins up, the tear flickers into being and arcs crackle.
 * Pull: a black sphere with a photon ring, a tilted spinning accretion disk, polar jets, matter spiralling in.
 * Collapse: the sphere implodes, flash, fireball, expanding shockwave rings, dust and a smoke column.
 */
final class BlackHoleFx extends Fx {
	private static final double TILT = 0.5;

	private final int countdown;
	private final int pull;
	private final double pullRadius;
	private final double collapseRadius;
	private final Vec3 core;

	BlackHoleFx(Vec3 pos, int countdown, int pull, double pullRadius, float[] data) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.pull = Math.max(1, pull);
		this.pullRadius = pullRadius;
		this.collapseRadius = data.length > 0 ? data[0] : 16.0;
		this.core = new Vec3(pos.x, pos.y + FxPayload.BH_HEIGHT, pos.z);
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			intro();
		} else if (age <= countdown + pull) {
			suck(age - countdown);
		} else {
			aftermath(age - (countdown + pull));
		}
		return age > countdown + pull + 56;
	}

	private Vec3 randomDir() {
		double vx = Pt.sym();
		double vy = Pt.sym();
		double vz = Pt.sym();
		double len = Math.max(0.001, Math.sqrt(vx * vx + vy * vy + vz * vz));
		return new Vec3(vx / len, vy / len, vz / len);
	}

	private void intro() {
		double prog = age / (double) countdown;

		// violet energy streaming into the point where the tear will open
		int n = 3 + (int) (prog * 16);
		for (int i = 0; i < n; i++) {
			Vec3 d = randomDir();
			double r = (7.0 + Pt.rnd() * 12.0) * (1.0 - 0.35 * prog);
			double sp = 0.30 + 0.25 * prog;
			Pt.add(ModParticles.WISP, core.x + d.x * r, core.y + d.y * r, core.z + d.z * r, -d.x * sp, -d.y * sp, -d.z * sp);
		}

		// spinning ground sigil and a ring that marks the pull radius
		Pt.ring(ModParticles.PLASMA, pos, 5.0 + 3.0 * (1.0 - prog), 30, age * 0.11, 0.12, 0.0, 0.0);
		Pt.ring(ModParticles.ZAP, pos, 2.5 + 1.5 * (1.0 - prog), 18, -age * 0.17, 0.15, 0.0, 0.0);
		double rr = pullRadius * prog;
		Pt.ring(ModParticles.ZAP, pos, rr, (int) Math.min(120, 16 + rr * 3.5), -age * 0.04, 0.2, 0.0, 0.0);
		for (int k = 0; k < 4; k++) {
			double a = age * 0.08 + k * Math.PI / 2.0;
			for (int s = 1; s <= 5; s++) {
				double r = 5.0 * s / 5.0;
				Pt.add(ModParticles.ZAP, pos.x + Math.cos(a) * r, pos.y + 0.15, pos.z + Math.sin(a) * r, 0, 0, 0);
			}
		}

		// the tear flickers into being
		if (prog > 0.30 && (prog > 0.6 || age % 2 == 0)) {
			Pt.add(ModParticles.DISK, core.x, core.y, core.z, 0, 0, 0);
			Pt.add(prog > 0.75 ? ModParticles.VOID_M : ModParticles.VOID_S, core.x, core.y, core.z, 0, 0, 0);
		}
		// arcs crackling between the tear and the ground
		if (prog > 0.35 && age % 4 == 0) {
			double a = Pt.rnd() * Math.PI * 2.0;
			double r = 3.0 + Pt.rnd() * 9.0;
			BoltFx.arc(core, new Vec3(pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r), (long) (Pt.rnd() * 1.0e9));
		}
		if (age == countdown) {
			Pt.add(ModParticles.FLARE, core.x, core.y, core.z, 0, 0, 0);
			Pt.burst(ModParticles.PLASMA, core, 40, 0.3, 1.0, 0.0);
		}
	}

	private void suck(int t) {
		double prog = t / (double) pull;
		boolean collapsing = t > pull - 8;

		// accretion glow first, then the black sphere on top of it
		Pt.add(ModParticles.DISK, core.x, core.y, core.z, 0, 0, 0);
		SimpleParticleType body = collapsing ? ModParticles.VOID_S : (prog < 0.25 ? ModParticles.VOID_M : ModParticles.VOID_L);
		Pt.add(body, core.x, core.y, core.z, 0, 0, 0);
		if (collapsing) {
			Pt.add(ModParticles.FLARE, core.x, core.y, core.z, 0, 0, 0);
		}

		disk(t, prog);
		jets(prog);
		infall(t, prog, collapsing);

		// lightning stabbing into the core from above
		if (t % 14 == 0) {
			double a = Pt.rnd() * Math.PI * 2.0;
			double r = 4.0 + Pt.rnd() * 10.0;
			BoltFx.arc(new Vec3(core.x + Math.cos(a) * r, core.y + 8.0 + Pt.rnd() * 10.0, core.z + Math.sin(a) * r), core, (long) (Pt.rnd() * 1.0e9));
		}
		// pull ring on the ground, collapsing inward
		if (t % 3 == 0) {
			double r = pullRadius * (1.0 - ((t % 30) / 30.0) * 0.8);
			Pt.ring(ModParticles.DUST, pos, r, (int) Math.min(40, 14 + r), Pt.rnd() * 6.28, 0.3, -0.22, 0.02);
		}
	}

	/** Tilted, spinning accretion disk: four spiral arms, hot white near the hole, orange further out, violet streamers at the rim. */
	private void disk(int t, double prog) {
		double rMax = 6.5 + 5.5 * Math.min(1.0, prog * 2.0);
		double cosT = Math.cos(TILT);
		double sinT = Math.sin(TILT);
		for (int arm = 0; arm < 4; arm++) {
			for (int j = 0; j < 15; j++) {
				double r = 2.7 + j * (rMax - 2.7) / 14.0;
				double spin = t * (3.2 / (r + 1.0)) + arm * Math.PI * 0.5 + 1.9 * Math.log(r);
				double px = Math.cos(spin) * r;
				double pz = Math.sin(spin) * r;
				// tilt the disk plane around the X axis
				double py = -pz * sinT;
				double pz2 = pz * cosT;
				double x = core.x + px;
				double y = core.y + py;
				double z = core.z + pz2;
				SimpleParticleType o = r < 4.3 ? ModParticles.CORE : (r < 7.0 ? ModParticles.SPARK : ModParticles.EMBER);
				Pt.add(o, x, y, z, 0, 0, 0);
				if (j % 4 == 3) {
					Pt.add(ModParticles.WISP, x, y, z, 0, 0, 0);
				}
			}
		}
	}

	/** Polar jets shooting out along the disk's axis. */
	private void jets(double prog) {
		double nx = 0.0;
		double ny = Math.cos(TILT);
		double nz = Math.sin(TILT);
		int n = 4 + (int) (prog * 6);
		for (int i = 0; i < n; i++) {
			double h = 2.8 + Pt.rnd() * (8.0 + 8.0 * prog);
			double w = 0.15 + h * 0.03;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				Pt.add(ModParticles.WISP, core.x + nx * h * sgn + Pt.sym() * w, core.y + ny * h * sgn + Pt.sym() * w,
					core.z + nz * h * sgn + Pt.sym() * w, 0, 0, 0);
			}
		}
	}

	/** Dust, embers and violet energy spiralling in from the edge of the pull radius, climbing up into the core. */
	private void infall(int t, double prog, boolean collapsing) {
		double r0 = pullRadius * (0.5 + 0.5 * Math.min(1.0, prog * 3.0));
		for (int stream = 0; stream < 3; stream++) {
			for (int i = 0; i < 9; i++) {
				double u = Pt.rnd();               // 0 = at the core, 1 = at the rim
				double r = 2.0 + u * (r0 - 2.0);
				double ang = -t * 0.13 + stream * (Math.PI * 2.0 / 3.0) + 3.4 * Math.log(r);
				double lift = FxPayload.BH_HEIGHT * Math.pow(1.0 - u, 1.6);
				double x = pos.x + Math.cos(ang) * r;
				double z = pos.z + Math.sin(ang) * r;
				double y = pos.y + 0.3 + lift;
				if (i % 3 == 0) {
					Pt.add(ModParticles.EMBER, x, y, z, 0, 0, 0);
				} else if (i % 3 == 1) {
					Pt.add(ModParticles.WISP, x, y, z, 0, 0, 0);
				} else if (u < 0.5) {
					Pt.add(ModParticles.DUST, x, y, z, 0, 0.02, 0);
				}
			}
		}
		if (collapsing) {
			// everything gets squeezed toward the centre
			for (int i = 0; i < 24; i++) {
				Vec3 d = randomDir();
				double r = 3.0 + Pt.rnd() * 6.0;
				Pt.add(ModParticles.PLASMA, core.x + d.x * r, core.y + d.y * r, core.z + d.z * r, -d.x * 0.7, -d.y * 0.7, -d.z * 0.7);
			}
		}
	}

	private void aftermath(int s) {
		Vec3 ground = new Vec3(pos.x, pos.y + 0.6, pos.z);
		if (s == 1) {
			Pt.add(ModParticles.FLARE, core.x, core.y, core.z, 0, 0, 0);
			Pt.add(ModParticles.FLARE, ground.x, ground.y, ground.z, 0, 0, 0);
			Pt.explosion(ground, collapseRadius * 0.55);
			Pt.explosion(core, collapseRadius * 0.35);
			Pt.burst(ModParticles.SPARK, core, 220, 0.5, 2.8, 0.0);
			Pt.burst(ModParticles.PLASMA, core, 120, 0.6, 2.2, 0.0);
			for (int i = 0; i < 6; i++) {
				BoltFx.arc(core, new Vec3(pos.x + Pt.sym() * collapseRadius, pos.y + 0.3, pos.z + Pt.sym() * collapseRadius), (long) (Pt.rnd() * 1.0e9));
			}
		}
		if (s <= 4) {
			Pt.add(ModParticles.VOID_S, core.x, core.y, core.z, 0, 0, 0);
		}
		// expanding shockwave rings at the core height and on the ground
		double maxR = collapseRadius * 2.6;
		double r = Math.min(maxR, s * 1.8);
		int n = (int) Math.min(200, 28 + r * 6);
		if (r < maxR) {
			Pt.ring(ModParticles.PLASMA, core, r, n, s * 0.2, 0.0, 0.0, 0.0);
			Pt.ring(ModParticles.CORE, ground, r * 0.9, n, 0.0, 0.2, 0.0, 0.04);
			Pt.ring(ModParticles.DUST, ground, r * 0.8, n / 2, 0.5, 0.4, 0.12, 0.05);
		}
		if (s < 14) {
			Pt.ring(ModParticles.FIRE, ground, r * 0.5, 14, 0.0, 0.3, 0.2, 0.08);
		}
		// smoke column and falling embers
		if (s < 44) {
			for (int i = 0; i < 4; i++) {
				Pt.add(ModParticles.SMOKE, pos.x + Pt.sym() * collapseRadius * 0.4, pos.y + 0.5, pos.z + Pt.sym() * collapseRadius * 0.4, 0, 0.14 + Pt.rnd() * 0.12, 0);
			}
			Pt.add(ModParticles.EMBER, pos.x + Pt.sym() * collapseRadius, pos.y + 1.0 + Pt.rnd() * 6.0, pos.z + Pt.sym() * collapseRadius, 0, -0.03, 0);
		}
	}
}

/**
 * Builds Skybreaker particles. They move like end-rod particles (fast start, smooth slow-down) but get an exact
 * size (half-width in blocks), an exact lifetime and their own animated texture from the particle's json.
 */
final class GlowProvider implements ParticleProvider<SimpleParticleType> {
	private final EndRodParticle.Provider base;
	private final float size;
	private final float jitter;
	private final int life;

	GlowProvider(SpriteSet sprites, float size, float jitter, int life) {
		this.base = new EndRodParticle.Provider(sprites);
		this.size = size;
		this.jitter = jitter;
		this.life = life;
	}

	@Override
	public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
	                               double xd, double yd, double zd, RandomSource random) {
		Particle p = this.base.createParticle(type, level, x, y, z, xd, yd, zd, random);
		if (p == null) {
			return null;
		}
		p.setLifetime(Math.max(1, this.life + random.nextInt(Math.max(1, this.life / 4))));
		if (p instanceof SingleQuadParticle quad) {
			float wanted = this.size * (1.0f + this.jitter * (random.nextFloat() * 2.0f - 1.0f));
			quad.scale(wanted / Math.max(0.0001f, quad.getQuadSize(0.0f)));
		}
		return p;
	}
}

/** Gives every Skybreaker particle its size (blocks, half-width), size jitter and lifetime (ticks). */
final class ParticleSetup {
	static void register() {
		//   type                  size   jitter life
		reg(ModParticles.PLASMA,  0.22f, 0.15f, 10);
		reg(ModParticles.CORE,    0.12f, 0.10f, 7);
		reg(ModParticles.BOLT,    0.34f, 0.15f, 7);
		reg(ModParticles.ZAP,     0.10f, 0.15f, 9);
		reg(ModParticles.SPARK,   0.08f, 0.40f, 16);
		reg(ModParticles.EMBER,   0.13f, 0.40f, 34);
		reg(ModParticles.FIRE,    1.30f, 0.50f, 24);
		reg(ModParticles.SMOKE,   1.90f, 0.50f, 60);
		reg(ModParticles.FLARE,   4.50f, 0.00f, 7);
		reg(ModParticles.VOID_S,  0.90f, 0.00f, 3);
		reg(ModParticles.VOID_M,  1.80f, 0.00f, 3);
		reg(ModParticles.VOID_L,  2.90f, 0.00f, 3);
		reg(ModParticles.DISK,    4.50f, 0.00f, 3);
		reg(ModParticles.WISP,    0.30f, 0.25f, 12);
		reg(ModParticles.DUST,    0.90f, 0.50f, 36);
	}

	private static void reg(SimpleParticleType type, float size, float jitter, int life) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> new GlowProvider(sprites, size, jitter, life));
	}

	private ParticleSetup() {
	}
}

/** Tiny particle toolbox. Uses "always visible" particles so beams stay visible at long range. */
final class Pt {
	static ClientLevel level;

	static double rnd() {
		return ThreadLocalRandom.current().nextDouble();
	}

	/** -1..1 */
	static double sym() {
		return ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0;
	}

	static void add(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
		float k = SkybreakerConfig.get().particleScale;
		if (k < 0.999f && rnd() > k) {
			return;
		}
		level.addAlwaysVisibleParticle(o, x, y, z, vx, vy, vz);
	}

	/** Horizontal ring around c. */
	static void ring(ParticleOptions o, Vec3 c, double r, int n, double phase, double yOff, double outSpeed, double upSpeed) {
		for (int i = 0; i < n; i++) {
			double a = phase + i * (Math.PI * 2.0 / n);
			double cx = Math.cos(a);
			double sz = Math.sin(a);
			add(o, c.x + cx * r, c.y + yOff, c.z + sz * r, cx * outSpeed, upSpeed, sz * outSpeed);
		}
	}

	/** Random burst on a sphere. */
	static void burst(ParticleOptions o, Vec3 c, int n, double minSpeed, double maxSpeed, double upBias) {
		for (int i = 0; i < n; i++) {
			double vx = sym();
			double vy = sym();
			double vz = sym();
			double len = Math.max(0.001, Math.sqrt(vx * vx + vy * vy + vz * vz));
			double s = minSpeed + rnd() * (maxSpeed - minSpeed);
			add(o, c.x, c.y, c.z, vx / len * s, vy / len * s + upBias, vz / len * s);
		}
	}

	/** Hollow vertical tube made of evenly spaced rings: a crisp outline instead of a random cloud. */
	static void tube(ParticleOptions o, double x, double y, double z, double h0, double h1, double r, int sides, double step, double phase) {
		for (double h = h0; h <= h1; h += step) {
			for (int i = 0; i < sides; i++) {
				double a = phase + i * (Math.PI * 2.0 / sides) + h * 0.05;
				add(o, x + Math.cos(a) * r, y + h, z + Math.sin(a) * r, 0, 0, 0);
			}
		}
	}

	/** Perfectly straight vertical line of points. */
	static void column(ParticleOptions o, double x, double y, double z, double h0, double h1, double step) {
		for (double h = h0; h <= h1; h += step) {
			add(o, x, y + h, z, 0, 0, 0);
		}
	}

	/** Evenly spaced light streaks flying out of c along the ground (the radial burst at an impact). */
	static void spokes(ParticleOptions o, Vec3 c, int n, double r0, double r1, double step, double yOff, double outSpeed, double phase) {
		for (int i = 0; i < n; i++) {
			double a = phase + i * (Math.PI * 2.0 / n);
			double cx = Math.cos(a);
			double sz = Math.sin(a);
			// every third spoke is longer, so the burst has a rhythm instead of looking uniform
			double end = (i % 3 == 0) ? r1 : r1 * 0.65;
			for (double r = r0; r <= end; r += step) {
				add(o, c.x + cx * r, c.y + yOff, c.z + sz * r, cx * outSpeed, 0.0, sz * outSpeed);
			}
		}
	}

	/** A full fiery detonation made only of Skybreaker particles. {@code size} ~ blocks of blast radius. */
	static void explosion(Vec3 c, double size) {
		add(ModParticles.FLARE, c.x, c.y + size * 0.15, c.z, 0, 0, 0);
		burst(ModParticles.FIRE, c, (int) (14 + size * 5), 0.03 * size + 0.05, 0.11 * size + 0.15, 0.04);
		burst(ModParticles.SMOKE, c, (int) (8 + size * 2.5), 0.02 * size, 0.07 * size + 0.05, 0.1);
		burst(ModParticles.SPARK, c, (int) (30 + size * 12), 0.25 + 0.05 * size, 0.9 + 0.2 * size, 0.2);
		burst(ModParticles.EMBER, c, (int) (10 + size * 4), 0.1, 0.5 + 0.08 * size, 0.3);
		burst(ModParticles.DUST, c, (int) (10 + size * 3), 0.05 * size, 0.1 * size + 0.1, 0.12);
		ring(ModParticles.PLASMA, c, size * 0.3, (int) (16 + size * 2), 0.0, 0.0, 0.18 + 0.05 * size, 0.0);
	}

	private Pt() {
	}
}

/** EMP Pulse: a shrinking crackle ring while it charges, a cyan expanding shell on the pulse, then lingering ground arcs. */
final class EmpFx extends Fx {
	private final int countdown;
	private final double radius;

	EmpFx(Vec3 pos, int countdown, double radius) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.radius = radius;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			if (age % 2 == 0) {
				double r = radius * (1.0 - (double) age / countdown) + 1.0;
				Pt.ring(ModParticles.ZAP, pos, r, (int) Math.min(36, r * 1.5 + 8), age * 0.2, 0.2, 0.0, 0.01);
			}
			if (age % 3 == 0) {
				Pt.add(ModParticles.CORE, pos.x + Pt.sym() * 0.6, pos.y + 3.0 + Pt.rnd() * 8.0, pos.z + Pt.sym() * 0.6, 0, 0, 0);
			}
			return false;
		}
		int t = age - countdown;
		if (t == 1) {
			Pt.add(ModParticles.FLARE, pos.x, pos.y + 1.5, pos.z, 0, 0, 0);
			Pt.burst(ModParticles.SPARK, pos.add(0.0, 1.0, 0.0), 50, 0.3, 1.4, 0.1);
		}
		if (t <= 16) {
			double r = radius * (t / 16.0);
			for (int lat = -1; lat <= 1; lat++) {
				double ang = lat * 0.5;
				double rr = r * Math.cos(ang);
				double yy = 1.0 + r * Math.sin(ang) * 0.6;
				Pt.ring(ModParticles.PLASMA, pos, rr, (int) Math.min(36, rr + 8), t * 0.3, yy, 0.0, 0.0);
			}
		}
		if (t <= 70) {
			for (int i = 0; i < 4; i++) {
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = Math.sqrt(Pt.rnd()) * radius;
				Pt.add(ModParticles.ZAP, pos.x + Math.cos(a) * rr, pos.y + 0.1 + Pt.rnd() * 0.5, pos.z + Math.sin(a) * rr,
					Pt.sym() * 0.05, 0.05, Pt.sym() * 0.05);
			}
		}
		return t > 70;
	}
}

/** Orbital Napalm: burning rain from the sky while it charges, a fireball, then a roaring field of flames. */
final class NapalmFx extends Fx {
	private final int countdown;
	private final int burnTicks;
	private final double radius;

	NapalmFx(Vec3 pos, int countdown, int burnTicks, double radius) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.burnTicks = burnTicks;
		this.radius = radius;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			for (int i = 0; i < 4; i++) {
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = Math.sqrt(Pt.rnd()) * radius * 0.8;
				Pt.add(ModParticles.EMBER, pos.x + Math.cos(a) * rr, pos.y + 24.0 + Pt.rnd() * 20.0, pos.z + Math.sin(a) * rr, 0, -0.9, 0);
			}
			if (age % 4 == 0) {
				double r = radius * (1.0 - (double) age / countdown) + 1.0;
				Pt.ring(ModParticles.PLASMA, pos, r, (int) Math.min(30, r * 1.5 + 8), age * 0.15, 0.2, 0.0, 0.0);
			}
			return false;
		}
		int t = age - countdown;
		if (t == 1) {
			Pt.explosion(pos.add(0.0, 1.0, 0.0), radius * 0.6);
		}
		if (t <= burnTicks) {
			for (int i = 0; i < 8; i++) {
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = Math.sqrt(Pt.rnd()) * radius;
				Pt.add(ModParticles.FIRE, pos.x + Math.cos(a) * rr, pos.y + 0.3, pos.z + Math.sin(a) * rr, 0, 0.07 + Pt.rnd() * 0.05, 0);
			}
			for (int i = 0; i < 3; i++) {
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = Math.sqrt(Pt.rnd()) * radius;
				Pt.add(ModParticles.SMOKE, pos.x + Math.cos(a) * rr, pos.y + 1.0, pos.z + Math.sin(a) * rr, 0, 0.1, 0);
				Pt.add(ModParticles.EMBER, pos.x + Math.cos(a) * rr, pos.y + 0.5, pos.z + Math.sin(a) * rr, Pt.sym() * 0.05, 0.12, Pt.sym() * 0.05);
			}
		}
		return t > burnTicks;
	}
}

/** Tactical Nuke: warning ring while it counts down, then fireball, shockwave ring, rising stem and mushroom cap. */
final class NukeFx extends Fx {
	private final int countdown;
	private final double blast;

	NukeFx(Vec3 pos, int countdown, double blast) {
		super(pos);
		this.countdown = Math.max(1, countdown);
		this.blast = blast;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		age++;
		if (age <= countdown) {
			if (age % 4 == 0) {
				double r = blast * (1.0 - (double) age / countdown) * 0.5 + 1.0;
				Pt.ring(ModParticles.PLASMA, pos, r, 24, age * 0.1, 0.2, 0.0, 0.0);
			}
			return false;
		}
		int t = age - countdown;
		double top = pos.y + blast * 1.5 * Math.min(1.0, t / 110.0);
		if (t == 1) {
			Pt.add(ModParticles.FLARE, pos.x, pos.y + 2.0, pos.z, 0, 0, 0);
		}
		if (t <= 30) {
			Pt.burst(ModParticles.FIRE, pos.add(0.0, blast * 0.1, 0.0), 22, 0.1, 0.1 + blast * 0.012, 0.1);
		}
		if (t <= 40) {
			double r = t * blast / 40.0;
			Pt.ring(ModParticles.PLASMA, pos, r, (int) Math.min(48, 12 + r), t * 0.2, 0.3, 0.0, 0.0);
			Pt.ring(ModParticles.DUST, pos, r * 0.9, (int) Math.min(30, 8 + r * 0.5), 0.0, 0.5, 0.1, 0.05);
		}
		if (t >= 4 && t <= 130) {
			// stem: fire low, smoke high
			for (int i = 0; i < 10; i++) {
				double h = Pt.rnd();
				double y = pos.y + (top - pos.y) * h;
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = blast * 0.07 * (0.4 + Pt.rnd());
				Pt.add(h < 0.5 ? ModParticles.FIRE : ModParticles.SMOKE, pos.x + Math.cos(a) * rr, y, pos.z + Math.sin(a) * rr, 0, 0.05, 0);
			}
		}
		if (t >= 30 && t <= 150) {
			// cap: a rolling ring of fire and smoke that spreads as it rises
			double capR = blast * 0.5 * Math.min(1.0, (t - 30) / 60.0) + 2.0;
			for (int i = 0; i < 14; i++) {
				double a = Pt.rnd() * Math.PI * 2.0;
				double rr = capR * (0.6 + 0.4 * Pt.rnd());
				double yy = top + Math.sin(a * 2.0) * capR * 0.12 + Pt.sym() * 1.5;
				Pt.add(i % 3 == 0 ? ModParticles.FIRE : ModParticles.SMOKE, pos.x + Math.cos(a) * rr, yy, pos.z + Math.sin(a) * rr,
					Math.cos(a) * 0.03, 0.03, Math.sin(a) * 0.03);
			}
		}
		return t > 160;
	}
}

/** One-tick lock-on ring. */
final class MarkFx extends Fx {
	private final double radius;

	MarkFx(Vec3 pos, double radius) {
		super(pos);
		this.radius = radius;
	}

	@Override
	boolean tick(ClientLevel level) {
		Pt.level = level;
		Pt.ring(ModParticles.PLASMA, pos.add(0.0, 0.1, 0.0), radius, 14, 0.0, 0.0, 0.0, 0.02);
		Pt.add(ModParticles.CORE, pos.x, pos.y + 0.2, pos.z, 0, 0.05, 0);
		return true;
	}
}
