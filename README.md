# Echoes of Aegis: Companions

Server-side friendly Fabric `26.1.2` mod for tiny humanoid companions.

## Features

- Wild `Little Wanderer` companions can spawn rarely in open grass/plains-like areas and near village beds or bells.
- Admins can spawn a test companion with `/companions summon`.
- Wild companions watch for dropped food, walk to it, consume one item, gain trust, and can bond with the player who dropped or stood nearest to the food.
- Wild companions take occasional idle walks when no food is nearby, instead of standing perfectly still.
- Tamed companions become `Little <player>`, follow their owner, defend against nearby monsters, and teleport back if too far or stuck.
- Follow-type roles can follow their owner through portals after a short configurable delay. Stay and Guard Claim companions remain anchored unless recalled.
- Follow-type roles can also catch up after large same-dimension teleports, such as homes or warps.
- Follow-type roles can also catch up after elytra or distance travel once the owner is no longer actively gliding, even if the companion was left outside the active tick range.
- Follow-type roles can enter and exit the owner's boat when a seat is available.
- Tamed companions ignore fall and fly-into-wall damage, and they do not snap to the owner mid-elytra flight.
- Players can have one active bonded companion by default.
- Tamed companions have a level, XP, and one small personality trait: Brave, Loyal, Scrappy, Careful, or Lucky.
- Companions gain XP from combat hits, mob kills, companion duels, survival time, and owner feeding.
- Companion roles can be changed from the GUI or `/companions role <role>`: Defender, Scout, Passive, Aggressive, Stay, or Guard Claim.
- GUI buttons and decorative panes are read-only server slots, marked as disposable UI items, and cleaned from cursor/player inventory/storage/equipment paths so role clicks/close paths cannot give players stained glass or button icons.
- Owners manage armor, weapons, shields, totems, and hidden helmets from the companion management screen.
- Companion gear is treated as managed saved equipment, so recall/rebuild cleanup cannot drop duplicate armor or weapons into the world.
- Helmet, chest, leggings, boots, main-hand, and offhand slots are now classified explicitly so leggings and boots stay usable even though they sit lower in the paper-doll layout.
- Owners can toggle follow/stay with an empty-hand right-click.
- Owners can heal a hurt companion by right-clicking it with food.
- Owners can open the companion management screen with sneak-right-click, even while holding an item.
- The companion management screen has a paper-doll style layout, role controls, level/trait/damage/armor/shield/respawn status, gear slots, and 9 storage slots.
- Armor, main-hand weapons, and offhand items can be removed from the management screen and returned to the owner.
- Main-hand weapon damage now contributes to companion melee damage, and offhand shields can block incoming mob hits.
- Helmet items are stored and returned through the GUI, but the visible head remains the tiny player head.
- `/companions recall` can recover a companion from any loaded world, or rebuild it beside the owner from saved companion data if it is not loaded.
- Portal-follow uses the same saved-state safety as recall, preserves health and gear, and skips defeated companions, duels, Stay, and Guard Claim.
- `/companions whistle` gives a Companion Whistle. Right-click it to recall the companion; sneak-right-click recalls and opens the menu.
- If a bonded companion is defeated by mobs, it becomes a Little Companion Egg. Keep the egg in the owner inventory and it automatically revives next to the owner after the configured real-world delay, default 5 minutes.
- The Little Companion Egg updates its countdown lore while in the owner inventory and gains a faint glint when ready.
- `/companions egg` reissues the bound revive egg for a defeated companion if the player loses it.
- `/companions bed [color]` gives the owner a placeable Little Companion Bed, rendered server-side as tiny scaled vanilla bed displays.
- Players can craft colored Little Companion Beds with `red bed + stick + dye`.
- `/companions duel <player>` sends or accepts an opt-in companion duel request.
- Companion duel wins and losses are tracked per player and exposed to EchoesSidebar placeholders.
- Claim-aware combat is conservative by default: companions do not target players unless player targeting is enabled and the target is allowed by claim/war rules.
- Neutral mobs, such as calm zombified piglins, are ignored until they are actively angry at the owner or companion.
- Non-hostile mobs, such as llamas, are calmed if they target a companion because of the hidden vanilla host body.
- Villagers, wandering traders, llamas, and other avoidant mobs no longer treat tagged companions as hostile scare sources.
- Guard Claim anchors the companion to the spot where the role is selected, patrols the guarded claim chunk when EchoesClaims is present, and falls back to patrolling around the guard post outside claims.
- Recall preserves the companion's current health, and saved-data rebuilds no longer come back at vanilla zombie health.
- Food held in either hand now heals first, so feeding cannot accidentally toggle companion roles or open the menu.

## Commands

- `/companions`
- `/companions summon` - operator test command.
- `/companions recall`
- `/companions follow`
- `/companions stay`
- `/companions mode <follow|stay>`
- `/companions role <defender|scout|passive|aggressive|stay|guard_claim>`
- `/companions storage`
- `/companions whistle`
- `/companions bed [color]`
- `/companions bed clear`
- `/companions egg`
- `/companions duel <player>`
- `/companions reload` - operator command.
- `/ecompanions` - shorter alias.

## Taming

This mod intentionally does not copy TinyMe's taming ritual. Echoes Companions uses a dropped-food trust loop:

1. Find or summon a wild Little Wanderer.
2. Drop food nearby.
3. Let the companion walk to the item and pick it up.
4. Each accepted food increases trust.
5. Once trust is high enough, each food has a configurable bonding chance.

Better food helps more. Bread, apples, and golden carrots get a small default bonus.

## Healing

Right-click a bonded companion with food to heal it. Better food restores more health, and the amount can be tuned with `healing_per_food_nutrition` and `minimum_heal_amount` in `config/echoescompanions.json`.

Sneak-right-click still opens the companion management screen instead of feeding.

Feeding also grants a small configurable amount of companion XP.

## Levels, Roles, And Traits

Companions start at level 1 and can level up from fights, duels, survival time, and feeding. Leveling increases health and damage modestly, so long-lived companions feel valuable without replacing player gear.

Roles:

- `Defender` - balanced follow and protect behavior.
- `Scout` - faster follow, smaller combat appetite.
- `Passive` - follows without attacking.
- `Aggressive` - larger combat radius and slightly higher damage.
- `Stay` - waits or rests at its saved Little Companion Bed.
- `Guard Claim` - anchors at the current spot, patrols the guarded claim chunk, and respects claim boundaries when EchoesClaims is present.

Traits:

- `Brave` - a little more health and damage.
- `Loyal` - a little more health, speed, and shield reliability.
- `Scrappy` - a small damage bump.
- `Careful` - better shield reliability with a small damage tradeoff.
- `Lucky` - slightly better XP gains.

## Defeat And Revival

If a bonded companion is defeated outside a duel, its saved gear and storage are preserved and the owner receives a `Little Companion Egg`. The egg must be in the owner's inventory to revive. Its lore updates with the remaining countdown, and it gains a faint glint when ready. After `companion_respawn_delay_seconds`, default 300 seconds, the egg is consumed and the companion respawns next to the owner.

If the egg is lost, `/companions egg` reissues the bound revive egg for the current defeated companion.

## Companion Beds

`/companions bed` gives a `Little Companion Bed`, a custom bed item that places a tiny two-piece vanilla bed using server-side block display entities. `/companions bed <color>` gives a specific bed color, and players can craft colored versions with `red bed + stick + dye`. Place it on open ground and set the companion to stay to have it rest there. `/companions bed clear` removes the saved bed display.

## Whistle

The Companion Whistle is a server-side marked goat horn item. Craft it with `copper ingot + amethyst shard + string`, or use `/companions whistle`.

- Right-click: recall your companion.
- Sneak-right-click: recall and open the companion management screen.

## Natural Spawns

Natural companion spawns are server-driven and configurable in `config/echoescompanions.json`.

- Every `spawn_check_interval_ticks` ticks, the server checks each Overworld player.
- By default that is every 1200 ticks, or about once per minute at 20 TPS.
- Each checked player has a `spawn_chance_per_player` chance, default `0.05`.
- The mod tries to place a wild Little Wanderer between `spawn_min_distance` and `spawn_max_distance` blocks from the player.
- The spawn position must be open to the sky, have air at the spawn block, and be on grass/path terrain or near a village bed or bell.
- By default, only 2 wild companions can be near a player inside the configured nearby radius.

## Server-Side Notes

Echoes Companions avoids custom entity types so players do not need a client mod. Companions use an AI-suppressed vanilla tiny humanoid body with separated arms, scale, tags, player heads, armor, gear, names, saved equipment, storage, recall, and custom server behavior. Vanilla hostile goals are removed and vanilla attack damage is forced to zero, while companion defense and duels use controlled server-side damage.

Tagged companions do not count as sleep-blocking monsters for bed checks, and vanilla avoid/hostile-sensor AI ignores them so villagers, wandering traders, llamas, and similar mobs do not panic around a bonded Little Wanderer. Their vanilla zombie sounds stay muted, and the server plays occasional higher-pitched villager-style chirps instead.

Storage persistence uses Minecraft's registry-aware `ItemStack` codec, so enchantments, names, lore, trims, durability, and modded item components are preserved when the companion inventory is saved.

## Config And Data

Generated config:

```text
config/echoescompanions.json
```

Portal-follow config:

```json
"follow_owner_through_portals": true,
"portal_follow_delay_ticks": 60,
"portal_follow_requires_not_in_combat": true,
"follow_owner_after_teleports": true,
"owner_teleport_follow_distance": 48.0,
"owner_teleport_follow_delay_ticks": 20,
"follow_owner_into_boats": true,
"companion_fall_damage_immunity": true
```

Generated world data:

```text
<world>/echoescompanions/companions.json
```

## Sidebar Stats

When EchoesSidebar is installed, it can read these companion placeholders:

- `%companion_duel_wins%`
- `%companion_duel_losses%`
- `%companion_duel_record%`
- `%companion_kills%` - alias for duel wins
- `%companion_deaths%` - alias for duel losses
