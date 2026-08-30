# AutoSell

[![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-red)](https://fabricmc.net)
[![Environment](https://img.shields.io/badge/Environment-Client-purple)]()
[![Modrinth](https://img.shields.io/modrinth/dt/autosellmod?color=00AF5C&label=downloads&logo=modrinth)](https://modrinth.com/mod/autosellmod)
[![License](https://img.shields.io/github/license/Uhuli/AutoSell)](LICENSE)

A client side Fabric mod that runs your server's sell command for you once your
inventory fills up, and moves the items you picked into the shop.

## Requirements

* Minecraft 26.2
* Fabric Loader 0.18.5 or newer
* Fabric API
* Java 25

The mod only does something useful on servers where a command opens a shop
container, for example `/sell`. In singleplayer there is nothing for it to talk
to.

## Usage

Press `K` to open the menu. The key can be rebound in the vanilla controls
screen under the AutoSell category.

In the menu you can:

* Pick the items to sell. Clicking an item in the grid adds or removes it, and
  you can select up to 32. The search box matches display names and registry
  IDs, and the check button next to it narrows the grid down to what you already
  picked.
* Set the inventory threshold. AutoSell triggers once that percentage of your
  36 inventory slots is occupied and at least one of your selected items is in
  there.
* Set the command. Default is `sell`. A leading slash is stripped, so both
  `sell` and `/sell` work.
* Choose the HUD corner with the small button next to the threshold slider.

Press start and close the menu. While AutoSell runs, a small box in the chosen
corner shows the items it will sell. Its border colour follows the current
state: green while watching the inventory, orange while waiting for the shop,
cyan while moving items.

When it triggers, AutoSell sends the command, waits for the container to open,
quick moves every matching stack out of your own inventory, and closes the
container again. Items sitting in the shop's own slots are never touched.

AutoSell stops on its own if the command fails to open a shop three times in a
row, and tells you so in chat. It also stops when you leave the server, or when
you close the menu with nothing selected.

## Configuration

Settings are stored in `config/autosell.json` and are written whenever you
change something in the menu.

```json
{
  "selectedItemIds": ["minecraft:cobblestone"],
  "inventoryThreshold": 90,
  "sellCommand": "sell",
  "hudCorner": "TOP_RIGHT"
}
```

`hudCorner` accepts `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT` and `BOTTOM_RIGHT`.
`inventoryThreshold` is clamped to 1 through 100. Item IDs that no longer
resolve are skipped instead of being dropped from the file, so a missing mod
does not wipe your selection.

## Building

```bash
./gradlew build
```

The jar ends up in `build/libs`. Use `./gradlew runClient` for a development
client.

## Contributing

Pull requests are welcome. Fork the repository, work on a branch, and describe
what you changed and why. Please match the existing code style and make sure
`./gradlew build` passes before you open the request.

## Issues and feature requests

Open an issue on GitHub. For bugs, include the steps to reproduce, your
Minecraft and mod versions, and the relevant part of your log. For features,
describe what you want to do and why the current behaviour gets in the way.
