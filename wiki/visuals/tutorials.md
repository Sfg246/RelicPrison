# Visual Walkthroughs

This page is the screen-by-screen companion to the written setup guides. The current RC repository does not contain verified real-client screenshots yet, so this page deliberately describes **what you should see** instead of showing invented screenshots. Replace each checkpoint with a real capture after Java/Bedrock staging.

## Walkthrough 1: Create a mine

### Screen 1: Region selection

**You should see:** your selection tool/session is active and both corners are accepted.

**Check before continuing:** the two corners surround only the intended mine volume.

### Screen 2: Mine creation result

**You should see:** a success message naming the mine rather than a generic “done.”

**Check:** the mine appears in the mine list/admin view.

### Screen 3: Composition

**You should see:** configured block entries and percentages/weights for the mine.

**Check:** provider-qualified custom blocks resolve instead of appearing as missing/invalid entries.

### Screen 4: Spawn and teleport

**You should see:** teleport places the player at the configured safe spawn rather than inside the reset volume.

### Screen 5: Reset

**You should see:** countdown/safety behavior, player evacuation when required, then the refreshed mine composition.

Go deeper: [Create Your First Mine](/guide/first-mine) and [Mines & Resets](/systems/mines).

## Walkthrough 2: Rank up

### Before clicking

The progression view should clearly distinguish your **current**, **completed**, **available** and **locked** ranks. An unaffordable rank should not look identical to an affordable one.

### After rank up

Verify all four results:

1. money cost was applied once;
2. profile rank changed;
3. permission/group integration is correct when configured;
4. newly unlocked mine access actually works.

Go deeper: [Ranks & Prestiges](/systems/progression).

## Walkthrough 3: Activate a booster

**Before:** record the normal sell result.

**Activation screen/message:** confirm multiplier type and duration.

**After:** sell the same known quantity and compare the result. Restart once during a test booster to verify persistence.

Go deeper: [Boosters](/systems/boosters).

## Walkthrough 4: Gang administration

A complete staging pass should capture:

1. gang creation;
2. invitation and join;
3. member list with online/offline state;
4. custom rank/permission editor;
5. bank deposit/withdraw;
6. mission/upgrade state;
7. ownership transfer confirmation;
8. disband confirmation.

Go deeper: [Gangs](/systems/gangs).

## Walkthrough 5: Admin editor safety

For each high-risk editor, capture the **entry screen**, **changed value**, **validation result**, **confirmation screen**, and **final success/audit result**.

Approved editor families include ranks, prestiges, sell prices, boosters, Block Events, reset settings and mine composition.

## Walkthrough 6: Troubleshooting/recovery

When testing a deliberate failure, capture:

1. the player/staff-visible error;
2. the relevant diagnostics output;
3. the transaction/package state when applicable;
4. the recovery/repair action;
5. the post-recovery diagnostics result.

Go deeper: [Admin & Recovery](/admin/operations).

## Screenshot capture standard

When real staging captures are added, use the same format everywhere:

- **Java screenshot:** full inventory/chat area at native scale.
- **Bedrock screenshot:** same workflow through Geyser when supported.
- **Caption:** what the user should notice.
- **Callout:** the exact button/item/line to click or verify.
- **Version label:** RelicPrison, Paper and integration version.
- Never crop out the evidence needed to understand the step.

::: warning Why there are no fake screenshots
The current Stage 6 limitation report explicitly says real client rendering/screenshots have not been verified. Documentation should not present generated mockups as proof of actual Minecraft behavior.
:::
