# Echoes of Aegis: Companions - Modrinth Copy

## Project Title

Echoes of Aegis: Companions

## Short Summary

Server-side friendly tiny companions that can be tamed, equipped, leveled, recalled, healed, commanded, and revived.

## Recommended Modrinth Metadata

- Project type: Mod
- Loader: Fabric
- Minecraft version: 26.1.2
- Environment:
  - Server: Required
  - Client: Optional / Not required
- Java: 25+
- License: MIT
- Dependencies:
  - Fabric API
- Optional integrations:
  - EchoesClaims
  - EchoesSidebar
  - EchoesHub

## Project Description

Echoes of Aegis: Companions adds tiny server-side friendly companions to Minecraft. Wild Little Wanderers can appear in open grassland and village-like areas, be tamed with dropped food, and become loyal companions that follow, defend, carry gear, gain XP, and return through recall or revival systems.

Players do not need to install a client mod. The companion uses vanilla server-side behavior with saved gear, storage, roles, levels, traits, and careful compatibility handling for normal survival servers.

## Feature List

- Rare wild Little Wanderer spawns in open grass/plains-like areas and near village beds or bells.
- Dropped-food taming: companions walk to food, eat it, gain trust, and may bond.
- One active bonded companion per player by default.
- Companion roles: Defender, Scout, Passive, Aggressive, Stay, and Guard Claim.
- Leveling and XP from combat, duels, survival time, and feeding.
- Personality traits: Brave, Loyal, Scrappy, Careful, and Lucky.
- Gear management GUI with armor, weapon, offhand, hidden helmet storage, and 9 storage slots.
- Main-hand weapons affect companion damage, and offhand shields can block hits.
- Food healing with configurable healing values.
- Companion Whistle for recall and menu access.
- Little Companion Beds with color crafting.
- Defeated companions become Little Companion Eggs and revive after a configurable real-world timer.
- Portal-follow, home/warp catch-up, boat riding, multi-seat mount riding, and safer elytra/fall handling.
- Conservative targeting: companions ignore calm neutral mobs and calm non-hostile mobs that accidentally target them.
- Optional companion duels.
- Optional EchoesClaims-aware Guard Claim behavior.
- Optional EchoesSidebar placeholders for companion duel stats.

## Installation

1. Install Fabric Loader for Minecraft 26.1.2.
2. Install Fabric API for Minecraft 26.1.2.
3. Put `echoescompanions-1.0.28.jar` in the server `mods` folder.
4. Start the server once to generate `config/echoescompanions.json`.
5. Adjust config values if needed, then restart or use `/companions reload`.

Clients do not need to install the mod to join a server using EchoesCompanions.

## Commands

Player commands:

- `/companions`
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
- `/ecompanions`

Operator commands:

- `/companions summon`
- `/companions reload`

## Config And Data

Generated config:

```text
config/echoescompanions.json
```

Generated world data:

```text
<world>/echoescompanions/companions.json
```

Notable config options include:

- Natural spawn rate and spawn distance.
- Taming foods, trust requirement, and tame chance.
- Follow speed, catch-up speed, teleport distance, and stuck teleport timing.
- Companion health, damage, XP, and max level.
- Food healing values.
- Portal, home/warp, boat, multi-seat mount, and fall-safety behavior.
- Companion duels.
- Claim-aware combat and Guard Claim patrol behavior.
- Respawn egg delay.

## Compatibility Notes

EchoesCompanions is designed to be server-side friendly. It avoids custom entity types so players do not need a client mod.

Optional integrations:

- EchoesClaims: Guard Claim can patrol claim areas and respect claim-aware combat rules.
- EchoesSidebar: companion duel stats can be displayed with placeholders.
- EchoesHub: can link to companion commands from the suite hub.

Known visual note:

The companion uses a vanilla-backed tiny humanoid host with a player head and visible gear. A full custom player-skin body would require a client-side renderer or a more complex resource-pack/display-entity visual layer.

## Version 1.0.28 Release Notes

This release improves companion movement polish and long-distance follow recovery.

Highlights:

- Wild Little Wanderers now take occasional idle walks when no dropped food is nearby.
- Follow-type companions can catch up after elytra or distance travel once the owner is no longer actively gliding.
- Distance catch-up works from saved companion data, so companions left outside the active player tick range no longer require the whistle to recover.
- Stay and Guard Claim companions remain anchored unless manually recalled.
- Server-side friendly design with no required client install.

## Version 1.0.27 Release Notes

This release fixes a companion recall edge case that could duplicate managed companion gear.

Highlights:

- Companion armor, weapons, shields, and offhand items no longer use vanilla mob equipment drop chances.
- Whistle/recall rebuilds now keep the previous saved companion record active if the rebuild cannot be added to the world.
- Nearby duplicate companion bodies are safely retired in the default one-companion setup.
- Retired companion copies are removed without dropping gear or issuing extra revive eggs.
- Cross-dimension recall keeps the saved record aligned with the actual moved companion entity.
- Follow-type companions can ride with their owner in configured multi-seat mounts.
- Default multi-seat mount support includes camels, camel husks, and Happy Ghasts.
- Boat riding remains supported, and Stay, Guard Claim, defeated, and dueling companions do not abandon their role to mount up.
- Server-side friendly design with no required client install.

## Version 1.0.26 Release Notes

This release improves companion travel and mount behavior.

Highlights:

- Follow-type companions can ride with their owner in configured multi-seat mounts.
- Default multi-seat mount support includes camels, camel husks, and Happy Ghasts.
- Boat riding remains supported, and Stay, Guard Claim, defeated, and dueling companions do not abandon their role to mount up.
- Little Companion Beds can now be picked back up by their owner with sneak-right-click.
- `/companions bed clear` now returns the bed item instead of only deleting the marker.
- Tameable Little Wanderer companions.
- Gear, storage, roles, levels, traits, healing, recall, whistle, beds, duels, and revive eggs.
- Follow-through portals and large same-dimension teleports.
- Boat boarding and dismounting with the owner when a seat is available.
- Safer elytra travel: companions avoid snapping to owners mid-flight and ignore fall/fly-into-wall damage.
- Better passive/neutral mob handling for llamas, zombified piglins, and similar edge cases.
- Server-side friendly design with no required client install.

## Gallery Caption Ideas

- A bonded Little Wanderer following its owner.
- Companion management GUI with gear, storage, roles, and stats.
- Companion Whistle and Little Companion Egg.
- Guard Claim role patrolling a protected area.
- Companion riding in a boat with its owner.

## FAQ

### Do players need the mod installed on their client?

No. The mod is designed for server-side use. Players can join with a normal Fabric/vanilla-compatible client as long as the server allows it.

### Can companions wear gear?

Yes. Companions can use armor, main-hand weapons, and offhand items through the companion management GUI.

### What happens if a companion dies?

The owner receives a Little Companion Egg. Keep it in the owner inventory until the revive timer finishes, and the companion will respawn next to the owner.

### Can companions follow through Nether portals or homes?

Yes. Follow-type roles can follow through portals and large same-dimension teleports when enabled in config. Stay and Guard Claim companions remain anchored unless recalled.

### Why does the companion not look exactly like a full tiny player skin?

Vanilla clients do not allow a server to render arbitrary custom player-model entities without a client mod. EchoesCompanions keeps the experience server-side friendly by using a vanilla-backed tiny body, player heads, visible gear, and server-controlled behavior.
