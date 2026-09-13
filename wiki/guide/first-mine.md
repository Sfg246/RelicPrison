# 4. Create Your First Mine

<div class="pathline">Setup Journey → Installation → Understand the plugin → First server setup → <strong>First mine</strong></div>

We will create a mine with ID `a`.

## What you are making

A RelicPrison mine needs a 3D box in a loaded Minecraft world.

That box has two opposite corners:

```text
Point 1  +------------------+
         |                  |
         |      MINE A      |
         |                  |
         +------------------+  Point 2
```

RelicPrison calls this box the mine's **bounds** or **cuboid**.

## Step 1: give yourself the selection wand

Run:

```text
/relicmine wand
```

The current selection wand uses the material configured under `selection.wand-material` in `config.yml`. The packaged default is a `BLAZE_ROD`.

Its controls are:

```text
Left click  = point 1
Right click = point 2
```

## Step 2: select the whole mine volume

Stand at one corner of the mine and left-click the first block.

Move to the opposite corner, including the height you want the mine to have, and right-click the second block.

::: tip Think in 3D
Do not select only the floor. The two points need to enclose the entire volume that will refill with mine blocks.
:::

## Step 3: create the preview

Run:

```text
/relicmine create a
```

RelicPrison checks that:

- the ID does not already exist;
- your selection is complete;
- the selection is within configured limits;
- the pending mine action can be previewed.

The command does **not** immediately create the mine. It starts a pending action so you can confirm it.

## Step 4: confirm

If the preview is correct:

```text
/relicmine confirm
```

If you made a mistake:

```text
/relicmine cancel
```

When Mine A is created through this flow, the new mine starts enabled, receives the next menu sort order, uses a default stone composition, and receives the default reset settings from `config.yml`.

## Step 5: set the safe teleport location

Stand where players should arrive, then run:

```text
/relicmine setspawn a
```

Choose a safe location outside the blocks that will be regenerated.

<div class="danger-zone"><strong>Do not put the spawn inside the refill volume.</strong><br>If the mine resets around the player, you can create a bad player experience or trap them inside regenerated blocks.</div>

## Step 6: inspect what RelicPrison saved

Run:

```text
/relicmine info a
```

Also useful:

```text
/relicmine list
```

You should see Mine A as an enabled configured mine.

## Step 7: test teleporting as a normal player

Use:

```text
/mine a
```

or open the normal mine menu:

```text
/mine
```

If the test player cannot access Mine A, check:

1. the mine is enabled;
2. the player has `relicprison.mine.teleport`;
3. rank/prestige requirements on the mine;
4. any custom access permission;
5. combat teleport restrictions if enabled.

## Step 8: configure what the mine is made of

Mine composition is managed with:

```text
/relicmine composition a list
```

The command family supports:

```text
/relicmine composition <mine> <list|set|remove|normalize|copy>
```

Start simple. A single-material test mine is much easier to verify than a large weighted custom composition.

## Step 9: understand the reset defaults

A newly created mine uses the current global defaults:

```yaml
mine-resets:
  default-interval-seconds: 900
  default-mined-percentage: 80.0
  default-warning-seconds: [30, 10, 5, 3, 2, 1]
```

In normal words:

- **900 seconds** = 15 minutes for the default interval.
- **80% mined** = the default percentage trigger.
- players can receive warnings at 30, 10, 5, 3, 2, and 1 seconds before a reset when that warning path applies.

## Step 10: force one reset for testing

Run:

```text
/relicmine reset a
```

Watch the blocks and the console.

Afterward:

```text
/relicmine info a
```

If block counts look wrong, the administration command set also provides:

```text
/relicmine recount a
```

## Step 11: add access rules only after basic access works

The mine requirement command supports rank, prestige, permission, and clearing requirements:

```text
/relicmine requirement a rank A
/relicmine requirement a permission your.permission.node
/relicmine requirement a clear all
```

Do not stack several requirements until you have tested each one separately.

## Mine A finished checklist

<ul class="big-checklist">
<li>☐ Selection covers the full 3D mine.</li>
<li>☐ `/relicmine create a` produced the expected preview.</li>
<li>☐ `/relicmine confirm` created Mine A.</li>
<li>☐ Spawn is outside the refill volume.</li>
<li>☐ `/relicmine info a` looks correct.</li>
<li>☐ A normal player can `/mine a`.</li>
<li>☐ Player can break the intended blocks.</li>
<li>☐ Forced reset works.</li>
<li>☐ Access rules are added only after the basic mine works.</li>
</ul>

## Where to go next

- [Mines & Resets](/systems/mines) for the full mine system.
- [Ranks & Prestiges](/systems/progression) to connect progression to mines.
- [Selling & Boosters](/systems/economy) to turn mined blocks into economy progression.
