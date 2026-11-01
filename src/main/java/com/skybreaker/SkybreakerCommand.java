package com.skybreaker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /skybreaker ... - every sub-command is OWNER ONLY (server console, the world host, or a UUID in "owners").
 * Operators are deliberately NOT enough. The check runs when the command executes, not just in the suggestions.
 */
public final class SkybreakerCommand {
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	/** Console / command blocks (no entity) pass; a player must be an owner; any other entity (/execute as) is refused. */
	private static boolean gate(CommandSourceStack src, String what) {
		ServerPlayer p = src.getPlayer();
		if (p != null) {
			if (Owner.require(p, "/skybreaker " + what)) {
				return true;
			}
			src.sendFailure(Component.literal("Only the server owner can use /skybreaker."));
			return false;
		}
		if (src.getEntity() != null) {
			src.sendFailure(Component.literal("Only the server owner can use /skybreaker."));
			return false;
		}
		return true;
	}

	private static boolean consoleOnly(CommandSourceStack src) {
		if (src.getEntity() == null && src.getPlayer() == null) {
			return true;
		}
		src.sendFailure(Component.literal("This can only be done from the server console."));
		return false;
	}

	private static int ok(CommandSourceStack src, String msg) {
		src.sendSuccess(() -> Component.literal("\u00a7a[Skybreaker] \u00a7f" + msg), true);
		return 1;
	}

	private static int fail(CommandSourceStack src, String msg) {
		src.sendFailure(Component.literal(msg));
		return 0;
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("skybreaker");

		root.then(Commands.literal("reload").executes(c -> gate(c.getSource(), "reload") ? ok(c.getSource(), OwnerActions.reload()) : 0));
		root.then(Commands.literal("status").executes(c -> gate(c.getSource(), "status") ? ok(c.getSource(), OwnerActions.status()) : 0));
		root.then(Commands.literal("cancelall").executes(c -> gate(c.getSource(), "cancelall") ? ok(c.getSource(), OwnerActions.cancelAll()) : 0));
		root.then(Commands.literal("clearcooldowns").executes(c -> gate(c.getSource(), "clearcooldowns") ? ok(c.getSource(), OwnerActions.clearCooldowns()) : 0));

		root.then(Commands.literal("set")
			.then(Commands.argument("key", StringArgumentType.word())
				.then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
					if (!gate(c.getSource(), "set")) {
						return 0;
					}
					String err = ConfigSettings.apply(StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value"));
					return err == null ? ok(c.getSource(), "Saved.") : fail(c.getSource(), err);
				}))));

		// allowlist (used when firePermission = ALLOWLIST)
		root.then(Commands.literal("allow")
			.then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word()).executes(c -> listEdit(c.getSource(), StringArgumentType.getString(c, "player"), true, false))))
			.then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word()).executes(c -> listEdit(c.getSource(), StringArgumentType.getString(c, "player"), false, false))))
			.then(Commands.literal("list").executes(c -> gate(c.getSource(), "allow list")
				? ok(c.getSource(), SkybreakerConfig.get().allowedPlayers.isEmpty() ? "Allowlist is empty." : "Allowed UUIDs: " + String.join(", ", SkybreakerConfig.get().allowedPlayers)) : 0)));

		// owners (console only, so nobody can promote themselves)
		root.then(Commands.literal("owner")
			.then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word()).executes(c -> consoleOnly(c.getSource())
				? listEdit(c.getSource(), StringArgumentType.getString(c, "player"), true, true) : 0)))
			.then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word()).executes(c -> consoleOnly(c.getSource())
				? listEdit(c.getSource(), StringArgumentType.getString(c, "player"), false, true) : 0)))
			.then(Commands.literal("list").executes(c -> consoleOnly(c.getSource())
				? ok(c.getSource(), SkybreakerConfig.get().owners.isEmpty() ? "No owners listed." : "Owners: " + String.join(", ", SkybreakerConfig.get().owners)) : 0)));

		// protected zones
		root.then(Commands.literal("zone")
			.then(Commands.literal("add").then(Commands.argument("radius", IntegerArgumentType.integer(4, 2000)).executes(c -> {
				if (!gate(c.getSource(), "zone add")) {
					return 0;
				}
				ServerPlayer p = c.getSource().getPlayer();
				if (p == null) {
					return fail(c.getSource(), "Run this in-game, standing where the zone should be centred.");
				}
				return ok(c.getSource(), OwnerActions.zoneHere(p, IntegerArgumentType.getInteger(c, "radius")));
			})))
			.then(Commands.literal("list").executes(c -> {
				if (!gate(c.getSource(), "zone list")) {
					return 0;
				}
				List<String> z = SkybreakerConfig.get().protectedZones;
				if (z.isEmpty()) {
					return ok(c.getSource(), "No protected zones.");
				}
				for (int i = 0; i < z.size(); i++) {
					ok(c.getSource(), "#" + (i + 1) + "  " + z.get(i));
				}
				return z.size();
			}))
			.then(Commands.literal("remove").then(Commands.argument("number", IntegerArgumentType.integer(1)).executes(c -> {
				if (!gate(c.getSource(), "zone remove")) {
					return 0;
				}
				List<String> z = SkybreakerConfig.get().protectedZones;
				int n = IntegerArgumentType.getInteger(c, "number");
				if (n > z.size()) {
					return fail(c.getSource(), "No zone #" + n);
				}
				z.remove(n - 1);
				SkybreakerConfig.save();
				return ok(c.getSource(), "Zone #" + n + " removed.");
			}))));

		// banned dimensions
		root.then(Commands.literal("dimension")
			.then(Commands.literal("ban").then(Commands.argument("id", StringArgumentType.greedyString()).executes(c -> dimEdit(c.getSource(), StringArgumentType.getString(c, "id"), true))))
			.then(Commands.literal("unban").then(Commands.argument("id", StringArgumentType.greedyString()).executes(c -> dimEdit(c.getSource(), StringArgumentType.getString(c, "id"), false))))
			.then(Commands.literal("list").executes(c -> gate(c.getSource(), "dimension list")
				? ok(c.getSource(), SkybreakerConfig.get().blockedDimensions.isEmpty() ? "No banned dimensions." : "Banned: " + String.join(", ", SkybreakerConfig.get().blockedDimensions)) : 0)));

		d.register(root);
	}

	private static int dimEdit(CommandSourceStack src, String id, boolean ban) {
		if (!gate(src, "dimension")) {
			return 0;
		}
		id = id.trim();
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}
		List<String> list = SkybreakerConfig.get().blockedDimensions;
		if (ban && !list.contains(id)) {
			list.add(id);
		} else if (!ban) {
			list.remove(id);
		}
		SkybreakerConfig.save();
		return ok(src, (ban ? "Strikes banned in " : "Strikes allowed again in ") + id);
	}

	/** Adds/removes an ONLINE player's UUID in the allowlist or the owners list. */
	private static int listEdit(CommandSourceStack src, String name, boolean add, boolean owners) {
		if (!owners && !gate(src, "allow")) {
			return 0;
		}
		ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(name);
		if (target == null) {
			return fail(src, "Player must be online: " + name);
		}
		String uuid = target.getUUID().toString();
		SkybreakerConfig cfg = SkybreakerConfig.get();
		List<String> list = owners ? cfg.owners : cfg.allowedPlayers;
		if (add && !list.contains(uuid)) {
			list.add(uuid);
		} else if (!add) {
			list.remove(uuid);
		}
		SkybreakerConfig.save();
		return ok(src, (add ? "Added " : "Removed ") + name + (owners ? " as owner." : " on the allowlist."));
	}

	private SkybreakerCommand() {
	}
}
