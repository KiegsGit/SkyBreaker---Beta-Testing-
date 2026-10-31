package com.skybreaker;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Arrays;
import java.util.function.Function;

public final class ModItems {
	private static final String FIRE = "Right-click to fire.";
	private static final String CHIP = "Targeting Chip in the other hand: 40% faster recharge.";

	public static final StrikeItem ORBITAL_RAILGUN = register("orbital_railgun", p -> new StrikeItem(p, StrikeItem.Kind.RAILGUN), "A tungsten rod dropped from orbit.", "Pinpoint strike with a huge crater.", FIRE, CHIP);
	public static final StrikeItem SATELLITE_LASER = register("satellite_laser", p -> new StrikeItem(p, StrikeItem.Kind.LASER), "A satellite beam burns a path for seconds,", "then ends in a massive detonation.", FIRE, CHIP);
	public static final StrikeItem METEOR_BARRAGE = register("meteor_barrage", p -> new StrikeItem(p, StrikeItem.Kind.METEOR), "Rains meteors over a wide area.", "Lots of damage and scattered craters.", FIRE, CHIP);
	public static final StrikeItem BLACK_HOLE = register("black_hole", p -> new StrikeItem(p, StrikeItem.Kind.BLACKHOLE), "Opens a black hole that drags in blocks and mobs,", "then collapses in a giant blast.", FIRE, CHIP);

	public static final StrikeItem EMP_PULSE = register("emp_pulse", p -> new StrikeItem(p, StrikeItem.Kind.EMP), "Freezes mobs, blacks out lamps and torches", "and wrecks redstone wiring.", FIRE, CHIP);
	public static final StrikeItem ORBITAL_NAPALM = register("orbital_napalm", p -> new StrikeItem(p, StrikeItem.Kind.NAPALM), "Thermite rain, then a field of fire", "that burns everything inside.", FIRE, CHIP);
	public static final StrikeItem TACTICAL_NUKE = register("tactical_nuke", p -> new StrikeItem(p, StrikeItem.Kind.NUKE), "Long countdown, giant blast and crater,", "mushroom cloud. Stay far away!", FIRE, CHIP);
	public static final StrikeItem TRACKING_STRIKE = register("tracking_strike", p -> new StrikeItem(p, StrikeItem.Kind.TRACKING), "Aim at a mob or player: it locks on,", "follows them, then strikes where they end up.", FIRE, CHIP);
	/** Hold in the other hand: every remote recharges 40% faster. */
	public static final Item TARGETING_CHIP = register("targeting_chip", Item::new, "Hold in the other hand while firing a remote.", "Remotes recharge 40% faster.");

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, String... lore) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkybreakerMod.id(name));
		Item.Properties props = new Item.Properties()
			.setId(key)
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.component(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
				.<Component>map(l -> Component.literal(l).withStyle(st -> st.withColor(0xB0B0B0).withItalic(false))).toList()));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props));
	}

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "combat"));
	private static final ResourceKey<CreativeModeTab> SKYBREAKER_TAB =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkybreakerMod.id("skybreaker"));

	/** Every item of the mod, in tab order. */
	private static final Item[] ALL = {
		ORBITAL_RAILGUN, SATELLITE_LASER, METEOR_BARRAGE, BLACK_HOLE,
		EMP_PULSE, ORBITAL_NAPALM, TACTICAL_NUKE, TRACKING_STRIKE, TARGETING_CHIP
	};

	/**
	 * Fabric's tab-builder class has been renamed between versions, so it is looked up by name. If none of the known
	 * names exists the items simply go into the Combat tab, and the game still starts and works.
	 */
	private static CreativeModeTab.Builder fabricTabBuilder() {
		String[] classes = {
			"net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab",
			"net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup"
		};
		for (String name : classes) {
			try {
				return (CreativeModeTab.Builder) Class.forName(name).getMethod("builder").invoke(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
				// try the next name
			}
		}
		return null;
	}

	public static void init() {
		CreativeModeTab.Builder builder = fabricTabBuilder();
		if (builder != null) {
			CreativeModeTab tab = builder
				.title(Component.translatable("itemGroup.skybreaker"))
				.icon(() -> new ItemStack(ORBITAL_RAILGUN))
				.displayItems((params, output) -> {
					for (Item item : ALL) {
						output.accept(item);
					}
				})
				.build();
			Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SKYBREAKER_TAB, tab);
		} else {
			SkybreakerMod.LOGGER.warn("Fabric creative-tab builder not found, adding Skybreaker items to the Combat tab instead.");
			CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
				for (Item item : ALL) {
					output.accept(item);
				}
			});
		}
	}

	private ModItems() {
	}
}
