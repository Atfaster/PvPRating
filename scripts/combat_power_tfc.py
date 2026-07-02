#!/usr/bin/env python3
"""TFC combat power calculator for PvPRating tuning.

Values are extracted from TerraFirmaCraft-Forge-1.20.1-3.2.20:
- net.dries007.tfc.common.TFCArmorMaterials
- net.dries007.tfc.common.TFCTiers
- net.dries007.tfc.util.Metal$ItemType.SWORD

TFC leather armor recipes produce minecraft:leather_* armor, so the leather
loadout uses vanilla leather armor attributes.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass


@dataclass(frozen=True)
class ArmorSet:
    key: str
    name: str
    boots: int
    greaves: int
    chestplate: int
    helmet: int
    toughness_per_piece: float

    @property
    def armor(self) -> int:
        return self.boots + self.greaves + self.chestplate + self.helmet

    @property
    def toughness(self) -> float:
        return self.toughness_per_piece * 4


@dataclass(frozen=True)
class Sword:
    key: str
    name: str
    tier_attack_bonus: float

    @property
    def attack_damage_attribute(self) -> float:
        # TFC passes 0 as SwordItem attackDamageModifier. Minecraft then adds
        # tier.getAttackDamageBonus() as an item attribute modifier on top of
        # the player's base 1.0 attack damage.
        return 1.0 + self.tier_attack_bonus


ARMOR_SETS = [
    ArmorSet("copper", "Copper", 1, 3, 4, 1, 0.0),
    ArmorSet("bismuth_bronze", "Bismuth Bronze", 1, 4, 4, 1, 0.0),
    ArmorSet("black_bronze", "Black Bronze", 1, 4, 4, 1, 0.0),
    ArmorSet("bronze", "Bronze", 1, 4, 4, 1, 0.0),
    ArmorSet("wrought_iron", "Wrought Iron", 1, 4, 5, 2, 0.0),
    ArmorSet("steel", "Steel", 2, 5, 6, 2, 1.0),
    ArmorSet("black_steel", "Black Steel", 2, 5, 6, 2, 2.0),
    ArmorSet("blue_steel", "Blue Steel", 3, 6, 8, 3, 3.0),
    ArmorSet("red_steel", "Red Steel", 3, 6, 8, 3, 3.0),
]

SWORDS = [
    Sword("copper", "Copper Sword", 3.25),
    Sword("bismuth_bronze", "Bismuth Bronze Sword", 4.0),
    Sword("black_bronze", "Black Bronze Sword", 4.25),
    Sword("bronze", "Bronze Sword", 4.0),
    Sword("wrought_iron", "Wrought Iron Sword", 4.75),
    Sword("steel", "Steel Sword", 5.75),
    Sword("black_steel", "Black Steel Sword", 7.0),
    Sword("blue_steel", "Blue Steel Sword", 9.0),
    Sword("red_steel", "Red Steel Sword", 9.0),
]

BASIC_LOADOUTS = [
    ("Naked", 0.0, 0.0, 1.0),
    ("Leather", 7.0, 0.0, 1.0),
]


def combat_power(armor: float, toughness: float, attack_damage: float, args: argparse.Namespace) -> float:
    return max(
        0.0,
        armor * args.armor_weight
        + toughness * args.toughness_weight
        + attack_damage * args.attack_weight,
    )


def gain_coefficient(killer_power: float, victim_power: float, minimum: float) -> float:
    if killer_power <= 0.0 or victim_power >= killer_power:
        return 1.0
    return max(minimum, victim_power / killer_power)


def base_gain(victim_rating: float, args: argparse.Namespace) -> float:
    return args.gain + args.killer_rating * args.killer_multiplier + victim_rating * args.claim_multiplier


def print_table(headers: list[str], rows: list[list[object]]) -> None:
    widths = [len(header) for header in headers]
    for row in rows:
        for i, value in enumerate(row):
            widths[i] = max(widths[i], len(str(value)))

    print(" | ".join(header.ljust(widths[i]) for i, header in enumerate(headers)))
    print("-+-".join("-" * width for width in widths))
    for row in rows:
        print(" | ".join(str(value).ljust(widths[i]) for i, value in enumerate(row)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--armor-weight", type=float, default=1.0)
    parser.add_argument("--toughness-weight", type=float, default=1.0)
    parser.add_argument("--attack-weight", type=float, default=2.0)
    parser.add_argument("--min-gain-coefficient", type=float, default=0.15)
    parser.add_argument("--gain", type=float, default=1.0)
    parser.add_argument("--killer-rating", type=float, default=0.0)
    parser.add_argument("--killer-multiplier", type=float, default=0.0)
    parser.add_argument("--claim-multiplier", type=float, default=0.09)
    parser.add_argument("--ratings", type=float, nargs="+", default=[0.0, 100.0])
    args = parser.parse_args()

    armor_rows = [["Naked", 0.0, 0.0, f"{combat_power(0.0, 0.0, 0.0, args):.2f}"]]
    armor_rows.append(["Leather", 7.0, 0.0, f"{combat_power(7.0, 0.0, 0.0, args):.2f}"])
    for armor in ARMOR_SETS:
        power = combat_power(armor.armor, armor.toughness, 0.0, args)
        armor_rows.append([armor.name, armor.armor, armor.toughness, f"{power:.2f}"])
    print("\nTFC armor sets, armor-only contribution")
    print_table(["set", "armor", "toughness", "power"], armor_rows)

    sword_rows = []
    for sword in SWORDS:
        attack = sword.attack_damage_attribute
        power = combat_power(0.0, 0.0, attack, args)
        sword_rows.append([sword.name, f"{attack:.2f}", f"{power:.2f}"])
    print("\nTFC swords, weapon-only contribution")
    print_table(["sword", "attackDamage", "power"], sword_rows)

    loadouts = []
    for name, armor, toughness, attack in BASIC_LOADOUTS:
        power = combat_power(armor, toughness, attack, args)
        loadouts.append((name, armor, toughness, attack, power))

    for armor in ARMOR_SETS:
        for sword in SWORDS:
            if armor.key == sword.key:
                attack = sword.attack_damage_attribute
                power = combat_power(armor.armor, armor.toughness, attack, args)
                loadouts.append((armor.name, armor.armor, armor.toughness, attack, power))

    loadout_rows = [
        [name, armor, toughness, f"{attack:.2f}", f"{power:.2f}"]
        for name, armor, toughness, attack, power in loadouts
    ]
    print("\nMatching full loadouts")
    print_table(["loadout", "armor", "toughness", "attackDamage", "power"], loadout_rows)

    for rating in args.ratings:
        rows = []
        current_base_gain = base_gain(rating, args)
        for killer_name, _, _, _, killer_power in loadouts:
            for victim_name, _, _, _, victim_power in loadouts:
                coefficient = gain_coefficient(killer_power, victim_power, args.min_gain_coefficient)
                rows.append(
                    [
                        killer_name,
                        victim_name,
                        f"{killer_power:.2f}",
                        f"{victim_power:.2f}",
                        f"{coefficient:.3f}",
                        f"{current_base_gain * coefficient:.3f}",
                    ]
                )
        print(f"\nAll killer/victim loadout pairs, both ratings={rating:g}")
        print_table(["killer", "victim", "killerPower", "victimPower", "coef", "killerGain"], rows)


if __name__ == "__main__":
    main()
