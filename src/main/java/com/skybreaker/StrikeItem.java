package com.skybreaker;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** One class for all four weapons. Right-click aims at a block up to 128 blocks away and starts a strike on the server. */
public class StrikeItem extends Item {
	public enum Kind {
		RAILGUN("Orbital Railgun", 2.5),
		LASER("Satellite Laser", 2.5),
		METEOR("Meteor Barrage", 8.0),
		BLACKHOLE("Singularity Cannon", 9.0),
		EMP("EMP Pulse", 6.0),
		NAPALM("Orbital Napalm", 10.0),
		NUKE("Tactical Nuke", 12.0),
		TRACKING("Tracking Strike", 2.0);

		public final String display;
		/** Radius of the client-side aiming reticle. */
		public final double previewRadius;

		Kind(String display, double previewRadius) {
			this.display = display;
			this.previewRadius = previewRadius;
		}
	}

	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	/** Per player: game time until which NO remote may be fired (global cooldown). */
	private static final Map<java.util.UUID, Long> GLOBAL_UNTIL = new HashMap<>();

	public final Kind kind;

	public StrikeItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public static void clearCooldowns() {
		COOLDOWNS.clear();
		GLOBAL_UNTIL.clear();
	}

	/** Returns why this player may not fire, or null if allowed. Re-checked on the server on every shot. */
	private static String permissionDenied(ServerPlayer sp, SkybreakerConfig cfg) {
		if (Owner.isOwner(sp)) {
			return null;
		}
		return switch (cfg.firePermission) {
			case "OWNER_ONLY" -> "Only the server owner may fire orbital remotes.";
			case "CREATIVE_ONLY" -> sp.isCreative() ? null : "Orbital remotes only work in creative mode on this server.";
			case "ALLOWLIST" -> cfg.allowedPlayers.contains(sp.getUUID().toString()) ? null : "You are not on this server's remote allowlist.";
			default -> null;
		};
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS; // client: just swing; the server decides everything
		}
		SkybreakerConfig cfg = SkybreakerConfig.get();

		String denied = permissionDenied(sp, cfg);
		if (denied != null) {
			sp.sendOverlayMessage(Component.literal("\u00a7c" + denied));
			return InteractionResult.FAIL;
		}
		if (Guard.dimensionBlocked(server)) {
			sp.sendOverlayMessage(Component.literal("\u00a7cStrikes are banned in this dimension."));
			return InteractionResult.FAIL;
		}

		long now = server.getGameTime();
		Long globalUntil = GLOBAL_UNTIL.get(sp.getUUID());
		if (globalUntil != null && now < globalUntil) {
			sp.sendOverlayMessage(Component.literal("\u00a7cRemote uplink busy: " + String.format("%.1f", (globalUntil - now) / 20.0) + "s"));
			return InteractionResult.FAIL;
		}
		String key = sp.getUUID() + ":" + kind.name();
		Long until = COOLDOWNS.get(key);
		if (until != null && now < until) {
			sp.sendOverlayMessage(Component.literal("\u00a7c" + kind.display + " recharging: "
				+ String.format("%.1f", (until - now) / 20.0) + "s"));
			return InteractionResult.FAIL;
		}

		double range = Math.min(cfg.maxTargetRange, SkybreakerMod.MAX_RANGE);
		Vec3 target;
		LivingEntity lock = null;
		if (kind == Kind.TRACKING) {
			lock = TrackingStrike.pick(server, sp, range);
			if (lock == null) {
				sp.sendOverlayMessage(Component.literal("\u00a7eNo lock: aim at a mob or player within " + (int) range + " blocks."));
				return InteractionResult.FAIL;
			}
			target = lock.position();
		} else {
			Vec3 eye = sp.getEyePosition();
			Vec3 end = eye.add(sp.getLookAngle().scale(range));
			BlockHitResult hit = server.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
			if (hit.getType() == HitResult.Type.MISS) {
				sp.sendOverlayMessage(Component.literal("\u00a7eNo target within " + (int) range + " blocks."));
				return InteractionResult.FAIL;
			}
			target = hit.getLocation();
		}

		String aimDenied = Guard.aimDenied(server, target);
		if (aimDenied != null) {
			sp.sendOverlayMessage(Component.literal("\u00a7c" + aimDenied));
			return InteractionResult.FAIL;
		}
		if (StrikeManager.activeCount() >= cfg.maxActiveStrikes) {
			sp.sendOverlayMessage(Component.literal("\u00a7cToo many strikes in progress on this server. Wait a moment."));
			return InteractionResult.FAIL;
		}
		if (StrikeManager.activeCount(sp.getUUID()) >= cfg.maxActiveStrikesPerPlayer) {
			sp.sendOverlayMessage(Component.literal("\u00a7cYou already have a strike in progress."));
			return InteractionResult.FAIL;
		}

		// ammo: every shot burns Orbital Charges (creative players are exempt)
		ItemStack chip = sp.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		int cost = cfg.ammoEnabled && !sp.isCreative() ? cfg.ammoFor(kind.name()) : 0;
		if (cost > 0 && chip.is(ModItems.EFFICIENCY_CHIP) && server.getRandom().nextDouble() < cfg.efficiencyFreeShotChance) {
			cost = 0;
			sp.sendOverlayMessage(Component.literal("\u00a7bEfficiency Chip: free shot!"));
		}
		if (cost > 0 && Ammo.count(sp) < cost) {
			sp.sendOverlayMessage(Component.literal("\u00a7c" + kind.display + " needs " + cost + " Orbital Charges (you have " + Ammo.count(sp) + ")."));
			return InteractionResult.FAIL;
		}

		Strike strike;
		double cooldown;
		switch (kind) {
			case RAILGUN -> {
				strike = new RailgunStrike(server, sp.getUUID(), target);
				cooldown = cfg.railgunCooldownSeconds;
			}
			case LASER -> {
				strike = new LaserStrike(server, sp.getUUID(), target);
				cooldown = cfg.laserCooldownSeconds;
			}
			case BLACKHOLE -> {
				strike = new BlackHoleStrike(server, sp.getUUID(), target);
				cooldown = cfg.blackHoleCooldownSeconds;
			}
			case EMP -> {
				strike = new EmpStrike(server, sp.getUUID(), target);
				cooldown = cfg.empCooldownSeconds;
			}
			case NAPALM -> {
				strike = new NapalmStrike(server, sp.getUUID(), target);
				cooldown = cfg.napalmCooldownSeconds;
			}
			case NUKE -> {
				strike = new NukeStrike(server, sp.getUUID(), target);
				cooldown = cfg.nukeCooldownSeconds;
			}
			case TRACKING -> {
				strike = new TrackingStrike(server, sp.getUUID(), target, lock.getUUID());
				cooldown = cfg.trackCooldownSeconds;
			}
			default -> {
				strike = new MeteorStrike(server, sp.getUUID(), target);
				cooldown = cfg.meteorCooldownSeconds;
			}
		}
		// chips (other hand): Targeting = faster recharge, Overcharge = more damage
		if (chip.is(ModItems.TARGETING_CHIP)) {
			cooldown *= 1.0 - cfg.chipCooldownReduction;
		}
		if (chip.is(ModItems.OVERCHARGE_CHIP)) {
			strike.power = 1.0 + cfg.overchargeDamageBonus;
		}
		if (cost > 0) {
			Ammo.take(sp, cost);
			sp.sendOverlayMessage(Component.literal("\u00a77Charges left: " + Ammo.count(sp)));
		}
		COOLDOWNS.put(key, now + SkybreakerConfig.ticks(cooldown));
		GLOBAL_UNTIL.put(sp.getUUID(), now + SkybreakerConfig.ticks(cfg.globalCooldownSeconds));
		sp.getCooldowns().addCooldown(sp.getItemInHand(hand), SkybreakerConfig.ticks(cooldown));
		StrikeManager.add(strike);
		return InteractionResult.SUCCESS;
	}
}
