# CC: Create Redstone Link

Based on <https://modrinth.com/mod/cc-redstone-link-bridge>. I would have simply contributed the additional feature of adding color support there, but unfortunately that mod is not source available (despite the MIT license on its page).

This mod connects **Create Redstone Link networks** with **CC:Tweaked computers**. It adds a single block, the **CC Redstone Link Bridge**, which can be placed in the world and then used as a ComputerCraft peripheral.

This mod requires **Create** and **CC:Tweaked**.

## What the Mod Does

The bridge acts as a small adapter between two systems:

- **Create** provides the Redstone Link network.
- **CC:Tweaked** provides Lua control from computers.

With the bridge block in the world, a connected computer can:

- read the current signal strength of any Redstone Link frequency pair
- send a signal strength to any Redstone Link frequency pair
- optionally tag either frequency slot with a 24-bit RGB dye color

The mod is intentionally minimal. The Lua interface only exposes the operations required for direct network interaction.

## How It Works

When the peripheral is used from Lua:

- `getLinkSignal(freq1, freq2)` looks up the current strength for that frequency pair.
- `watchLinkSignal(freq1, freq2)` subscribes the attached computer to change events for that frequency pair.
- `unwatchLinkSignal(freq1, freq2)` removes that subscription.
- `sendLinkSignal(freq1, freq2, strength)` sets the target frequency pair and transmits the chosen strength.

All four functions accept two optional trailing arguments, `color1` and `color2`, which apply a dye color to the corresponding frequency slot.
The frequency values are item IDs written as strings. For example, `minecraft:iron_ingot` and `minecraft:oak_sapling` form one valid pair.

## Crafting Recipe

![The recipe is a 3×3 shaped craft with Redstone Links in all four corners, a Wireless Modem in the center, and Cobblestone in the bottom-middle slot. The three remaining middle-edge slots (top-middle, middle-left, and middle-right) are filled with Create transmitters.](docs/crafting.webp)

## Lua API

Peripheral type:

- `redstone_link_bridge`

Methods:

- `getLinkSignal(freq1, freq2 [, color1 [, color2]])` -> `number`
- `watchLinkSignal(freq1, freq2 [, color1 [, color2]])` -> `number`
- `unwatchLinkSignal(freq1, freq2 [, color1 [, color2]])`
- `sendLinkSignal(freq1, freq2, strength [, color1 [, color2]])`


### Redstone Link Change Events

Call `watchLinkSignal(...)` once for each frequency pair you want to monitor. It returns the current signal immediately, then queues a ComputerCraft event whenever Create reports a different received signal for that pair.

Event shape:

```lua
"redstone_link_change", freq1, freq2, newStrength, oldStrength, color1, color2
```

`color1` and `color2` are `nil` for uncolored frequency slots.

Example:

```lua
local bridge = peripheral.find("redstone_link_bridge")
assert(bridge, "No redstone_link_bridge found")

local current = bridge.watchLinkSignal("minecraft:diamond", "minecraft:redstone")
print("Initial signal:", current)

while true do
    local _, freq1, freq2, newStrength, oldStrength = os.pullEvent("redstone_link_change")
    print(freq1, freq2, oldStrength, "->", newStrength)
end
```

Use `unwatchLinkSignal(...)` with the same arguments when the computer no longer needs events for that frequency pair.

### Frequency Values

`freq1` and `freq2` must be item IDs as strings.

Examples:

- `"minecraft:iron_ingot"`
- `"minecraft:oak_sapling"`
- `"minecraft:redstone"`

Use an empty string (`""`) if you want to represent an empty frequency value.

### Frequency Colors

`color1` and `color2` are optional 24-bit RGB integers in the form `0xRRGGBB`. They are only meaningful when the corresponding frequency item supports Minecraft's `DYED_COLOR` data component, which is only leather armor and leather horse armor in vanilla. A colored slot is on a different Redstone Link network from an uncolored slot of the same item, and different colors produce different networks.

The Minecraft Wiki Dye page lists the RGB value each of the 16 standard dyes produces when applied to leather armor in Java Edition. Useful values:

- `0xB02E26` -- Red Dye
- `0x3C44AA` -- Blue Dye
- `0xFED83D` -- Yellow Dye
- `0x5E7C16` -- Green Dye
- `0x1D1D21` -- Black Dye
- `0xF9FFFE` -- White Dye

Pass `nil` (or simply omit the argument) for a slot that should remain uncolored. To color only the second slot, pass `nil` for the first.

## Example

```lua
local bridge = peripheral.find("redstone_link_bridge")
assert(bridge, "No redstone_link_bridge found")

-- Read an existing frequency pair
local current = bridge.getLinkSignal("minecraft:diamond", "minecraft:redstone")
print("Current signal:", current)

-- Send a signal to a frequency pair
bridge.sendLinkSignal("minecraft:diamond", "minecraft:redstone", 7)

-- Send on a color-tagged channel. This matches a physical Redstone Link
-- whose leather-armor slots were dyed with one Red Dye and one Blue Dye.
bridge.sendLinkSignal(
    "minecraft:leather_chestplate",
    "minecraft:leather_helmet",
    15,
    0xB02E26,   -- Red Dye
    0x3C44AA)   -- Blue Dye

-- Only the second slot is colored
bridge.sendLinkSignal(
    "minecraft:leather_chestplate",
    "minecraft:leather_helmet",
    15,
    nil,
    0x3C44AA)   -- Blue Dye
```
