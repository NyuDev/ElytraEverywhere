# Security Policy

ElytraEverywhere is a **client-side** Fabric mod (a Baritone addon). It has no
server component and opens no network sockets of its own. The realistic security
surface is small, but reports are still welcome — especially anything that could
crash a client, corrupt a world, or be abused on a server.

## Supported versions

Fixes land against the latest release. Only the most recent tag is maintained;
older versions are not patched. The newest jars are always on the
[releases page](https://github.com/NyuDev/ElytraEverywhere/releases/latest).

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Instead, open a private report:
[**Report a vulnerability**](https://github.com/NyuDev/ElytraEverywhere/security/advisories/new).

Include:

- what the problem is and the impact you see,
- the Minecraft and addon versions,
- steps to reproduce (a log or crash report helps a lot).

You'll get an acknowledgement, and once a fix is out it will be credited in the
release notes unless you'd rather stay anonymous.

## Out of scope

- Bugs that only crash *your own* client with no wider impact — file those as a
  normal [bug report](https://github.com/NyuDev/ElytraEverywhere/issues/new/choose).
- Issues in **Baritone**, **Meteor**, **Fabric**, or **Minecraft** themselves —
  report those to their respective projects.
- Anti-cheat detection. This is an automation/cheat utility; using it on servers
  that forbid it is your own risk and not a security matter for this project.
