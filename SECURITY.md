# Security notes

Read this before running or redistributing the package.

## Important risks

- This procedure is intended only for a ZLT X28 running firmware 1.5.13.
- Enabling Telnet starts an unauthenticated BusyBox root shell on the local
  network. Keep the modem isolated from untrusted devices while port 23 is open.
- The archive replaces the privileged `http.cgi` and `mtk_netagent` ARM64
  binaries. They are stripped and their source code is not included, so a
  complete security audit is not possible from this repository.
- Installing incompatible files can break the modem web interface or prevent
  normal operation.
- The installer deletes `/mnt/data/etc/tzcfg/update_config` before extraction.
- Backups are stored on the same modem and are not a substitute for an external
  recovery image.

## Protections included by the installer

- The archive MD5 is checked before extraction.
- Existing backups are not overwritten.
- The public `superadmin/strong_password` starter values must be replaced with
  private values in `x28.sh` before installation.
- Installation stops while a `CHANGE_ME` credential placeholder remains.
- TR-069/CWMP, periodic reporting, automatic upgrades, upgrade prompts, and
  long-lived TR-069 connections are disabled.
- TR-069 ACS/CPE credentials and remote endpoints are cleared.
- Storage is synchronized before reboot.

## Recommended precautions

1. Keep the modem disconnected from external internet during installation.
2. Connect only through a trusted LAN cable.
3. Verify `CHECKSUMS.txt` before use.
4. Choose unique senior and super credentials and store them safely.
5. Never commit a live `sessionId` or real credentials to GitHub.
6. After reboot, verify that Telnet port 23 is closed when it is no longer
   required.
7. Confirm that you have the legal right to redistribute the included vendor
   binaries. No new license for those binaries is asserted by this repository.

## Static inspection summary

The archive contains three expected regular files and no path-traversal entries,
absolute paths, or symbolic links. No obvious reverse-shell address, SSH key,
cron persistence, or hidden download URL was found in printable binary strings.
This limited static inspection does not prove that the stripped binaries are
safe; full confidence would require comparison with trusted originals or
reverse engineering.
