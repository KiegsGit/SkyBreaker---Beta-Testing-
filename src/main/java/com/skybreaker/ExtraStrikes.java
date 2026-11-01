package com.skybreaker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The four newer weapons. They are package-private on purpose: only StrikeItem creates them.
// (Several top-level classes share this one file to keep the repository small.)

/** EMP Pulse: a blue shockwave that freezes mobs, blacks out redstone lamps/torches and wrecks redstone wiring. */
final class EmpStrike extends Strike {
	private final int countdown;
	private final double radius;
	private final int stunTicks;
	private final int reach;
	private final int layers;
	private final BlockPos base;
	private final List<Mob> stunned = new ArrayList<>();

	EmpStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.empCountdownSeconds);
		this.radius = cfg.empRadius;
		this.stunTicks = SkybreakerConfig.ticks(cfg.empStunSeconds);
		this.reach = (int) Math.ceil(radius);
		this.layers = reach * 2 + 1;
		this.base = BlockPos.containing(pos);
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.EMP, pos.x, pos.y, pos.z, countdown, 0, (float) radius, new float[0]));
		cinema(countdown, 5);
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 1.9f);
		countdownTick(countdown, "EMP Pulse");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "EMP Pulse");
		if (age == countdown) {
			pulse();
		}
		int s = age - countdown;
		if (s > 0) {
			if (s <= stunTicks) {
				for (Mob m : stunned) {
					if (m.isAlive()) {
						m.getNavigation().stop();
						Vec3 v = m.getDeltaMovement();
						m.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
					}
				}
			}
			if (cfg.empRedstone && s <= layers) {
				sweep(s - 1);
			}
		}
		return s >= Math.max(stunTicks, layers);
	}

	private void pulse() {
		StrikeUtil.sound(level, pos, ModSounds.FIRE, 10.0f, 1.7f);
		StrikeUtil.sound(level, pos, ModSounds.BOOM, 12.0f, 1.4f);
		StrikeUtil.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 8.0f, 1.8f);
		StrikeUtil.recoil(ownerPlayer(), 10, 2.0f);
		StrikeUtil.flash(level, pos, 0.8f, 14);
		StrikeUtil.shake(level, pos, 30, 2.0f);
		StrikeUtil.blast(level, pos, radius, cfg.empDamage, owner, false, 0.0f);
		AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
		for (Mob m : level.getEntitiesOfClass(Mob.class, box)) {
			if (m.position().distanceTo(pos) <= radius && StrikeUtil.validTarget(m, owner)) {
				stunned.add(m);
			}
		}
	}

	/** One horizontal slice of the sphere per tick, so a big radius never stalls the server. */
	private void sweep(int layerIndex) {
		int dy = layerIndex - reach;
		double r2 = radius * radius;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dy * dy + dz * dz > r2) {
					continue;
				}
				BlockPos p = base.offset(dx, dy, dz);
				if (!level.hasChunkAt(p)) {
					continue;
				}
				BlockState st = level.getBlockState(p);
				if (!Guard.canDestroy(level, p)) {
					continue;
				}
				if (st.is(Blocks.REDSTONE_LAMP) || st.is(Blocks.REDSTONE_TORCH) || st.is(Blocks.REDSTONE_WALL_TORCH)) {
					if (st.getValue(BlockStateProperties.LIT)) {
						level.setBlock(p, st.setValue(BlockStateProperties.LIT, false), 3);
					}
				} else if (cfg.terrainDestruction && (st.is(Blocks.REDSTONE_WIRE) || st.is(Blocks.REPEATER) || st.is(Blocks.COMPARATOR))) {
					StrikeUtil.tryRemove(level, p);
				}
			}
		}
	}
}

/** Orbital Napalm: thermite rain, then a field of fire that keeps burning (and burning anything inside) for a while. */
final class NapalmStrike extends Strike {
	private final int countdown;
	private final int burnTicks;
	private final double radius;

	NapalmStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.napalmCountdownSeconds);
		this.burnTicks = SkybreakerConfig.ticks(cfg.napalmBurnSeconds);
		this.radius = cfg.napalmRadius;
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.NAPALM, pos.x, pos.y, pos.z, countdown, burnTicks, (float) radius, new float[0]));
		cinema(countdown, 6);
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 1.1f);
		StrikeUtil.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 4.0f, 0.8f);
		countdownTick(countdown, "Orbital Napalm");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Orbital Napalm");
		if (age == countdown) {
			StrikeUtil.sound(level, pos, ModSounds.FIRE, 8.0f, 0.8f);
			StrikeUtil.sound(level, pos, ModSounds.BOOM, 14.0f, 0.9f);
			StrikeUtil.sound(level, pos, SoundEvents.GENERIC_EXPLODE, 10.0f, 0.6f);
			StrikeUtil.recoil(ownerPlayer(), 10, 1.8f);
			StrikeUtil.flash(level, pos, 0.5f, 12);
			StrikeUtil.shake(level, pos, 24, 2.0f);
			StrikeUtil.blast(level, pos, radius, cfg.napalmDamage, owner, true, 10.0f);
		}
		int s = age - countdown;
		if (s > 0 && s <= burnTicks) {
			if (cfg.napalmFire && cfg.terrainDestruction) {
				for (int i = 0; i < 6; i++) {
					igniteRandomColumn();
				}
			}
			if (s % 10 == 0) {
				StrikeUtil.blast(level, pos, radius, cfg.napalmDamage * 0.12f, owner, false, 6.0f);
			}
			if (s % 40 == 1) {
				StrikeUtil.sound(level, pos, SoundEvents.FIRE_AMBIENT, 8.0f, 0.7f);
			}
		}
		return s >= burnTicks;
	}

	private void igniteRandomColumn() {
		RandomSource rnd = level.getRandom();
		double ang = rnd.nextDouble() * Math.PI * 2.0;
		double rr = Math.sqrt(rnd.nextDouble()) * radius;
		int x = (int) Math.floor(pos.x + Math.cos(ang) * rr);
		int z = (int) Math.floor(pos.z + Math.sin(ang) * rr);
		if (!level.hasChunkAt(new BlockPos(x, (int) pos.y, z))) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(y - pos.y) > 12) {
			return;
		}
		BlockPos p = new BlockPos(x, y, z);
		if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir() && Guard.canDestroy(level, p)) {
			level.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
		}
	}
}

/** Tactical Nuke: long countdown, a blinding flash, a huge blast, a crater and a second shockwave. */
final class NukeStrike extends Strike {
	private final int countdown;
	private final double blast;

	NukeStrike(ServerLevel level, UUID owner, Vec3 pos) {
		super(level, owner, pos);
		this.countdown = SkybreakerConfig.ticks(cfg.nukeCountdownSeconds);
		this.blast = cfg.nukeBlastRadius;
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.NUKE, pos.x, pos.y, pos.z, countdown, 0, (float) blast, new float[0]));
		cinema(countdown, 7);
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 6.0f, 0.6f);
		StrikeUtil.sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, 6.0f, 0.5f);
		tell("\u00a7cNuclear launch detected. Stay clear of the blast radius!");
		countdownTick(countdown, "Tactical Nuke");
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Tactical Nuke");
		if (age == countdown) {
			detonate();
		}
		int s = age - countdown;
		if (s == 10) {
			// the expanding shock front
			StrikeUtil.blast(level, pos, blast * 1.4, cfg.nukeDamage * 0.25f, owner, true, 8.0f);
			StrikeUtil.sound(level, pos, ModSounds.BOOM, 40.0f, 0.5f);
		}
		if (s > 0 && s <= 40 && s % 4 == 0) {
			StrikeUtil.lightningNear(level, pos, blast * 0.1, blast * 0.7);
		}
		return s >= 40;
	}

	private void detonate() {
		StrikeUtil.sound(level, pos, ModSounds.FIRE, 24.0f, 0.5f);
		StrikeUtil.sound(level, pos, ModSounds.BOOM, 48.0f, 0.55f);
		StrikeUtil.sound(level, pos, ModSounds.BH_COLLAPSE, 32.0f, 0.8f);
		StrikeUtil.sound(level, pos, SoundEvents.GENERIC_EXPLODE, 32.0f, 0.3f);
		StrikeUtil.recoil(ownerPlayer(), 20, 3.0f);
		StrikeUtil.flash(level, pos, 1.0f, 100);
		StrikeUtil.shake(level, pos, 160, 6.0f);
		StrikeUtil.blast(level, pos, blast, cfg.nukeDamage, owner, true, 15.0f);
		if (cfg.nukeTerrain) {
			StrikeUtil.crater(level, pos, cfg.nukeCraterRadius, cfg.maxBlocksPerStrike, cfg.debrisCount);
		}
	}
}

/** Tracking Strike: locks onto a mob or player, follows it until the last moment, then drops a bolt on its final position. */
final class TrackingStrike extends Strike {
	private static final int LOCK_LEAD = 12;

	private final UUID targetId;
	private final int countdown;
	private final int lockTick;
	private final int impactTick;
	private Vec3 last;
	private Vec3 lockPos;
	/** Size of the locked target, sent with every live marker so the client can fit the lock rings to it. */
	private double lastHeight = 1.8;
	private double lastWidth = 0.8;

	TrackingStrike(ServerLevel level, UUID owner, Vec3 pos, UUID targetId) {
		super(level, owner, pos);
		this.targetId = targetId;
		this.countdown = Math.max(LOCK_LEAD + 6, SkybreakerConfig.ticks(cfg.trackCountdownSeconds));
		this.lockTick = countdown - LOCK_LEAD;
		this.impactTick = countdown + FxPayload.BOLT_TICKS;
		this.last = pos;
		this.lockPos = pos;
	}

	/** The living entity closest to the crosshair (within about 10 degrees, in line of sight), or null. */
	static LivingEntity pick(ServerLevel level, ServerPlayer sp, double range) {
		Vec3 eye = sp.getEyePosition();
		Vec3 look = sp.getLookAngle();
		AABB box = sp.getBoundingBox().inflate(range);
		LivingEntity best = null;
		double bestDot = 0.985;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e == sp || !StrikeUtil.validTarget(e, sp.getUUID())) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 2.0 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && sp.hasLineOfSight(e)) {
				best = e;
				bestDot = dot;
			}
		}
		return best;
	}

	@Override
	public void start() {
		StrikeUtil.broadcast(level, pos, new FxPayload(FxPayload.MARK, pos.x, pos.y, pos.z, 0, 0, 1.6f, new float[0]));
		cinema(countdown, 8);
		StrikeUtil.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 4.0f, 1.5f);
		countdownTick(countdown, "Tracking Strike");
	}

	private void follow() {
		Entity e = level.getEntity(targetId);
		if (e instanceof LivingEntity le && le.isAlive()) {
			last = new Vec3(le.getX(), le.getY(), le.getZ());
			AABB bb = le.getBoundingBox();
			lastHeight = bb.getYsize();
			lastWidth = bb.getXsize();
		}
	}

	/** Live marker on the target: sent EVERY tick while tracking so the lock visibly sticks to a moving target. */
	private void mark() {
		StrikeUtil.broadcast(level, last, new FxPayload(FxPayload.MARK, last.x, last.y, last.z, 0,
			(int) Math.round(lastHeight * 10.0), (float) Math.max(1.0, lastWidth * 1.5), new float[0]));
	}

	@Override
	public boolean tick() {
		age++;
		countdownTick(countdown, "Tracking Strike");
		if (age < lockTick) {
			follow();
			mark();
		} else if (age == lockTick) {
			follow();
			lockPos = last;
			StrikeUtil.broadcast(level, lockPos, new FxPayload(FxPayload.RAILGUN, lockPos.x, lockPos.y, lockPos.z,
				LOCK_LEAD, FxPayload.BOLT_TICKS, (float) cfg.trackRadius, new float[0]));
			StrikeUtil.sound(level, lockPos, SoundEvents.BEACON_ACTIVATE, 6.0f, 1.7f);
		}
		if (age == countdown) {
			StrikeUtil.sound(level, lockPos, ModSounds.FIRE, 8.0f, 1.3f);
			StrikeUtil.sound(level, lockPos, SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0f, 2.0f);
			StrikeUtil.recoil(ownerPlayer(), 10, 1.8f);
		}
		if (age == impactTick) {
			impact();
		}
		return age >= impactTick + 8;
	}

	private void impact() {
		StrikeUtil.sound(level, lockPos, ModSounds.BOOM, 14.0f, 1.2f);
		StrikeUtil.sound(level, lockPos, SoundEvents.GENERIC_EXPLODE, 10.0f, 0.8f);
		StrikeUtil.flash(level, lockPos, 0.4f, 10);
		StrikeUtil.shake(level, lockPos, 20, 1.6f);
		StrikeUtil.blast(level, lockPos, cfg.trackRadius, cfg.trackDamage, owner, true, 6.0f);
		if (cfg.trackTerrain) {
			StrikeUtil.crater(level, lockPos, 3.0, 200, 30);
		}
		StrikeUtil.lightning(level, lockPos.x, lockPos.y, lockPos.z);
	}
}
