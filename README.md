## DeluxeCoinflip
This plugin was made open source to help in development and bug fixes by accepting pull requests from the community. Anyone is free to compile this plugin and use it.

## Cozy Survival Setup fork

This fork is maintained for **Cozy Survival Setup**, under the same GPL-3.0 license (see `copyright.txt`), for Paper 1.21.11. It's built on upstream's `dev` branch rather than `master`, since `dev` is where the maintainers actually ship fixes (as of this fork, `master` is several months stale and missing a large rewrite that fixed a predictable coin-flip outcome and a couple of real dupes - see upstream issues #31/#45 and PR #46).

On top of that patched base:

- **Play with Bot**: a solo coinflip against the house instead of another player. No listing, resolves instantly, win chance and payout are configurable, and it keeps its own win/loss count separate from PvP stats. Disconnecting mid-flip still resolves and pays out correctly, same as upstream's own fix for that class of bug on the PvP side.
- bStats metrics removed entirely, this build never phones home.

This is an independent fork; upstream support (and the SpigotMC/BuiltByBit paid builds) remain with Zithium Studios.

# Support
If you need support for this plugin you must purchase it from [SpigotMC](https://www.spigotmc.org/resources/deluxecoinflip.79965/) or [BuiltByBit](https://builtbybit.com/resources/deluxecoinflip.10475/)

# Building
To build this plugin you need to add the missing APIs to the /libs/ folder then it's as simple as typing
`./gradlew shadowJar`

# Used Libraries
Here is a list of the various libraries used in this plugin.
- [Triump-GUI](https://github.com/TriumphTeam/triumph-gui)
- [ACF](https://github.com/aikar/commands) (Aikar Command Framework)
