# ZLT X28 1.5.13 — Manual Offline Unlock

> [!CAUTION]
> Use this procedure only on a **ZLT X28 running software version 1.5.13**.
> Using these files on another model or firmware can break the web panel or
> prevent the modem from working. Do not disconnect power during installation.

This method is completely local:

- Connect the laptop to an X28 **LAN port**.
- No WAN connection, second modem, or external internet is required.
- The laptop serves `x28.sh` and `x28.tgz` directly to the modem.

## Files used

| Item | Purpose |
|------|---------|
| `x28.sh` | Shell installer executed inside the modem through Telnet |
| `x28.tgz` | Replacement configuration and ARM64 modem binaries |
| `reference/x28/` | Extracted reference copy of the archive; it is not executed |

See [SECURITY.md](SECURITY.md) before using or redistributing the package, and
use [CHECKSUMS.txt](CHECKSUMS.txt) to verify that the files have not changed.

The `.sh` extension is optional on Linux. We use it only to make the script's
purpose clear. The command `sh /tmp/x28.sh` does not require executable
permission.

## 1. Connect the laptop

1. Connect the laptop directly to a LAN port on the X28 using Ethernet.
2. Temporarily disconnect Wi-Fi, VPNs, and other network connections if they
   could route `192.168.70.x` traffic through the wrong interface.
3. Open <https://192.168.70.1> and confirm the software version is **1.5.13**.
4. Find the laptop's X28-facing IP address:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -like '192.168.70.*'
```

The examples use:

- Modem: `192.168.70.1`
- Laptop: `192.168.70.131`
- Local HTTP port: `8000`

Replace `192.168.70.131` below if your laptop received a different address.

## 2. Obtain a current session ID

1. Log in to the X28 web panel.
2. Open Developer Tools with `F12` or `Ctrl+Shift+I`.
3. Select **Network** and reload the page.
4. Open a request to `http.cgi` and copy its `sessionId` value.

Session IDs expire. Obtain a new one if the request in the next step does not
open port 23.

## 3. Enable Telnet

Replace `YOUR_SESSION_ID` and paste this as one PowerShell line:

```powershell
curl.exe -k -s "https://192.168.70.1/cgi-bin/http.cgi" -X POST -H "Content-Type: application/json" --data-raw '{"enabled":"1","ip":"192.168.1.1 ; telnetd -l /bin/ash","cmd":172,"method":"POST","subcmd":6,"language":"EN","sessionId":"YOUR_SESSION_ID"}'
```

Use the plain URL shown above. Do not paste Markdown link characters such as
`[URL](URL)` into PowerShell.

Wait briefly and check port 23:

```powershell
Start-Sleep -Seconds 3
Test-NetConnection 192.168.70.1 -Port 23
```

Continue only when the result is:

```text
TcpTestSucceeded : True
```

If it remains `False`, obtain a new session ID and repeat this step. The WAN
configuration request (`cmd:302`) is not used by this offline LAN procedure.

## 4. Configure safe login credentials

Open the local `x28.sh` in a text editor. Replace every `CHANGE_ME` value at the
top of the file.

```sh
NORMAL_LOGIN_NAME='user'
NORMAL_LOGIN_PWD='PASSWORD_PRINTED_ON_ROUTER_LABEL'
SENIOR_LOGIN_NAME='YOUR_PRIVATE_SENIOR_NAME'
SENIOR_LOGIN_PWD='YOUR_PRIVATE_SENIOR_PASSWORD'
SUPER_LOGIN_NAME='YOUR_PRIVATE_SUPER_NAME'
SUPER_LOGIN_PWD='YOUR_PRIVATE_SUPER_PASSWORD'
```

- For the normal account, use the credentials printed on the router label or
  another value you will remember and record. This makes recovery after a
  factory reset easier, although exact reset behavior can vary by firmware.
- Give the senior and super accounts different private credentials.
- Do not keep the public defaults `root/admin` or `mahdi/mahdi`.
- Do not reuse the normal-user password for the privileged accounts.
- Do not put a single quote or line break inside a value.

The installer refuses to continue if a `CHANGE_ME` placeholder remains.

### TR-069 protection

During installation, `x28.sh` automatically disables TR-069/CWMP remote
management, periodic reporting, automatic upgrades, upgrade prompts, and
long-lived TR-069 connections. It also clears the ACS/CPE credentials, ACS URL,
management server, management port, and remote version-check URL.

These changes are applied to the installed `main_config` after extraction. The
archive itself is not modified, so its expected MD5 remains unchanged.

## 5. Verify and serve the local files

Open PowerShell in the folder containing `x28.sh` and `x28.tgz`.

Verify the archive:

```powershell
(Get-FileHash .\x28.tgz -Algorithm MD5).Hash
```

The result must be:

```text
1468B8686D86B34337C1EE0086A42A76
```

Do not continue if it differs.

Start the local HTTP server:

```powershell
py -3 -m http.server 8000 --bind 0.0.0.0
```

If `py` is unavailable but `python` is installed:

```powershell
python -m http.server 8000 --bind 0.0.0.0
```

Keep this window open. If the firewall asks, allow Python on the **private
network** so the modem can download the two local files.

In a second PowerShell window, verify both URLs:

```powershell
curl.exe -I "http://192.168.70.131:8000/x28.sh"
curl.exe -I "http://192.168.70.131:8000/x28.tgz"
```

Both should return `HTTP/1.0 200 OK`.

## 6. Connect through Telnet

```powershell
telnet 192.168.70.1
```

If Windows does not recognize `telnet`, enable **Telnet Client** from:

`Control Panel → Programs → Turn Windows features on or off → Telnet Client`

The injected service opens a BusyBox shell directly and normally does not ask
for a username or password.

## 7. Run the installer inside Telnet

At the modem's Telnet prompt, run:

```sh
wget http://192.168.70.131:8000/x28.sh -O /tmp/x28.sh
sh /tmp/x28.sh 192.168.70.131 8000
```

The arguments after `x28.sh` are the laptop IP and local HTTP port. Change them
if your values are different.

The script will:

1. Refuse to run if credential placeholders remain.
2. Remount `/` as writable.
3. Download `x28.tgz` from the laptop.
4. Verify MD5 `1468b8686d86b34337c1ee0086a42a76`.
5. Back up the current configuration and binaries without overwriting older
   backups.
6. Install the replacement files.
7. Apply the selected logins and disable TR-069.
8. Restart `mtk_netagent`, synchronize storage, and reboot.

Watch the Telnet output. The connection will close when the modem reboots. Wait
several minutes before reconnecting to the web panel.

The original files are retained on the modem as:

```text
/mnt/data/etc/tzcfg/main_config.bk
/tzwww/cgi-bin/http.cgi.bk
/usr/bin/mtk_netagent.bk
```

## Troubleshooting

### Telnet port 23 stays closed

- Confirm the modem responds to `ping 192.168.70.1`.
- Confirm its HTTPS panel opens.
- Obtain a new session ID and repeat step 3.
- Replace `-s` with `-i` in the curl command to display the HTTP response.
- JSON assertion errors mean the curl command or payload was malformed.

### `curl` reports `missing close_notify`

Some firmware closes its self-signed HTTPS connection abruptly. Test port 23;
the message alone does not prove that the request failed.

### The modem cannot download a file

- Confirm the Python server is still running.
- Confirm the laptop and modem are on the same `192.168.70.x/24` network.
- Confirm the laptop IP in both Telnet commands is correct.
- Verify both URLs from the laptop.
- Allow the server through the private-network firewall.
- Confirm another application is not already using port 8000.

### The script reports a checksum mismatch

- Do not extract or install the archive manually.
- Confirm the served file is the original local `x28.tgz`.
- Verify its MD5 in step 5.
- Do not rebuild `x28.tgz`; credential and TR-069 changes belong in `x28.sh`.
