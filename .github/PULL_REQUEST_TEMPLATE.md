<!--
Thanks for contributing! Please read CONTRIBUTING.md first.
Keep PRs focused — one logical change per PR is much easier to review.
-->

## What does this PR do?

<!-- A short description of the change and why it's needed. Link any related issue with "Fixes #123". -->

## Type of change

- [ ] Bug fix (non-breaking)
- [ ] New feature (non-breaking)
- [ ] Breaking change (behaviour or API differs)
- [ ] Docs / wiki / CI only

## How was it tested?

<!--
Only 1.21.11 is runtime-verified in game. If you touched a mixin, say so:
the Meteor-Baritone minified targets can differ per Minecraft version.
-->

- [ ] Built locally with `gradlew build` (1.21.11 default)
- [ ] Ran the dev client (`gradlew runClient`) and reproduced the fix / feature in game
- [ ] Built against other matrix versions (list which): 

**Dimension(s) tested:** <!-- Overworld / End / Nether -->

## Checklist

- [ ] My change keeps working with the **Meteor** Baritone fork (the official obfuscated build is unsupported).
- [ ] I checked the obfuscation gotchas in CONTRIBUTING.md before adding/editing a mixin.
- [ ] Addon output still goes to the `ElytraEverywhere/Debug` logger, not chat (water-landing `Done :)` is the one exception).
- [ ] I updated the wiki / README if user-facing behaviour changed.
