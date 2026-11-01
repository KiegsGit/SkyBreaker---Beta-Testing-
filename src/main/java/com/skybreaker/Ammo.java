package com.skybreaker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Orbital Charges: the ammunition every shot burns. Creative players never need them. */
public final class Ammo {
	public static int count(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(ModItems.ORBITAL_CHARGE)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public static void take(ServerPlayer p, int amount) {
		for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(ModItems.ORBITAL_CHARGE)) {
				int t = Math.min(amount, s.getCount());
				s.shrink(t);
				amount -= t;
			}
		}
	}

	private Ammo() {
	}
}
