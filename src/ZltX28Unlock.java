import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ZltX28Unlock extends JFrame {
    private static final String MODEM_IP = "192.168.70.1";
    private static final String EXPECTED_MD5 = "1468b8686d86b34337c1ee0086a42a76";

    private final JTextField urlField = new JTextField("https://" + MODEM_IP);
    private final JPasswordField sessionField = new JPasswordField();
    private final JTextField hostField = new JTextField(detectLocalIp());
    private final JTextField portField = new JTextField("8000");
    private final JTextField normalUserField = new JTextField("user");
    private final JPasswordField normalPassField = new JPasswordField();
    private final JTextField seniorUserField = new JTextField("root");
    private final JPasswordField seniorPassField = new JPasswordField();
    private final JTextField superUserField = new JTextField("superadmin");
    private final JPasswordField superPassField = new JPasswordField("strong_password");
    private final JCheckBox versionCheck = new JCheckBox("I verified the modem software version is exactly 1.5.13.");
    private final JButton unlockButton = new JButton("Unlock modem");
    private final JButton telnetButton = new JButton("Enable Telnet only");
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JProgressBar progress = new JProgressBar(0, 3);
    private volatile PackageServer packageServer;

    private static final class Settings {
        final String modemUrl;
        final String modemHost;
        final String sessionId;
        final String localHost;
        final int port;
        final String normalUser;
        final String normalPass;
        final String seniorUser;
        final String seniorPass;
        final String superUser;
        final String superPass;

        Settings(String modemUrl, String modemHost, String sessionId, String localHost, int port,
                 String normalUser, String normalPass, String seniorUser, String seniorPass,
                 String superUser, String superPass) {
            this.modemUrl = modemUrl;
            this.modemHost = modemHost;
            this.sessionId = sessionId;
            this.localHost = localHost;
            this.port = port;
            this.normalUser = normalUser;
            this.normalPass = normalPass;
            this.seniorUser = seniorUser;
            this.seniorPass = seniorPass;
            this.superUser = superUser;
            this.superPass = superPass;
        }
    }

    public ZltX28Unlock() {
        super("ZLT X28 Unlock Utility - Firmware 1.5.13");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(780, 700));
        setSize(880, 800);
        setLocationRelativeTo(null);
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (packageServer != null) packageServer.close();
                dispose();
                System.exit(0);
            }
        });
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("ZLT X28 Unlock Utility");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        JLabel subtitle = new JLabel("LAN-only offline unlock for firmware 1.5.13 — no WAN or internet required.");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(subtitle);
        top.add(Box.createVerticalStrut(12));

        JPanel connection = new JPanel(new GridBagLayout());
        connection.setBorder(BorderFactory.createTitledBorder("Connection"));
        addRow(connection, 0, "Modem URL", urlField);
        addRow(connection, 1, "Session ID", sessionField);
        addRow(connection, 2, "This PC's modem-facing IP", hostField);
        addRow(connection, 3, "Local HTTP port", portField);
        connection.setAlignmentX(Component.LEFT_ALIGNMENT);
        connection.setMaximumSize(new Dimension(Integer.MAX_VALUE, connection.getPreferredSize().height));
        top.add(connection);
        top.add(Box.createVerticalStrut(10));

        JPanel credentials = new JPanel(new GridBagLayout());
        credentials.setBorder(BorderFactory.createTitledBorder("Web-panel credentials written during installation"));
        addRow(credentials, 0, "Normal username", normalUserField);
        addRow(credentials, 1, "Normal password (router label)", normalPassField);
        addRow(credentials, 2, "Senior username", seniorUserField);
        addRow(credentials, 3, "Senior password", seniorPassField);
        addRow(credentials, 4, "Super username", superUserField);
        addRow(credentials, 5, "Super password", superPassField);
        JCheckBox showPasswords = new JCheckBox("Show passwords");
        char passwordEcho = normalPassField.getEchoChar();
        showPasswords.addActionListener(e -> {
            char echo = showPasswords.isSelected() ? (char) 0 : passwordEcho;
            normalPassField.setEchoChar(echo);
            seniorPassField.setEchoChar(echo);
            superPassField.setEchoChar(echo);
        });
        GridBagConstraints show = new GridBagConstraints();
        show.gridx = 1; show.gridy = 6; show.anchor = GridBagConstraints.WEST;
        show.insets = new Insets(2, 0, 4, 8);
        credentials.add(showPasswords, show);
        credentials.setAlignmentX(Component.LEFT_ALIGNMENT);
        credentials.setMaximumSize(new Dimension(Integer.MAX_VALUE, credentials.getPreferredSize().height));
        top.add(credentials);
        top.add(Box.createVerticalStrut(10));

        JPanel checks = new JPanel();
        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        checks.setBorder(BorderFactory.createTitledBorder("Required checks"));
        checks.add(versionCheck);
        checks.setAlignmentX(Component.LEFT_ALIGNMENT);
        checks.setMaximumSize(new Dimension(Integer.MAX_VALUE, checks.getPreferredSize().height));
        top.add(checks);
        root.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(unlockButton);
        actions.add(telnetButton);
        JButton copyButton = new JButton("Copy terminal");
        copyButton.addActionListener(e -> {
            logArea.selectAll();
            logArea.copy();
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        actions.add(copyButton);
        JButton clearButton = new JButton("Clear terminal");
        clearButton.addActionListener(e -> {
            logArea.setText("");
            terminalLine("Terminal cleared. Ready.");
        });
        actions.add(clearButton);
        center.add(actions, BorderLayout.NORTH);

        JPanel output = new JPanel(new BorderLayout(0, 5));
        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(progress, BorderLayout.SOUTH);
        output.add(statusPanel, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(18, 18, 18));
        logArea.setForeground(new Color(220, 255, 220));
        logArea.setCaretColor(Color.WHITE);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Live terminal - modem and installer output"));
        scroll.setPreferredSize(new Dimension(700, 260));
        output.add(scroll, BorderLayout.CENTER);
        center.add(output, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);

        unlockButton.addActionListener(e -> startUnlock());
        telnetButton.addActionListener(e -> startTelnetOnly());
        terminalLine("Ready. All unlock files are embedded; external internet is not required.");
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = row; left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(4, 8, 4, 12);
        panel.add(new JLabel(label), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = row; right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 0, 4, 8);
        panel.add(field, right);
    }

    private Settings validateSettings(boolean fullUnlock) throws Exception {
        if (!versionCheck.isSelected()) throw new Exception("Confirm that the modem firmware is exactly 1.5.13.");
        String session = new String(sessionField.getPassword()).trim();
        if (!session.matches("[A-Za-z0-9_-]{8,256}")) throw new Exception("Enter a valid session ID copied from the modem web panel.");
        String url = urlField.getText().trim();
        URI uri = new URI(url);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
            throw new Exception("Enter a valid modem URL beginning with http:// or https://.");
        String host = hostField.getText().trim();
        InetAddress.getByName(host);
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException ex) { throw new Exception("Enter a valid local HTTP port."); }
        if (port < 1 || port > 65535) throw new Exception("The HTTP port must be from 1 to 65535.");
        String normalUser = normalUserField.getText().trim();
        String normalPass = new String(normalPassField.getPassword());
        String seniorUser = seniorUserField.getText().trim();
        String seniorPass = new String(seniorPassField.getPassword());
        String superUser = superUserField.getText().trim();
        String superPass = new String(superPassField.getPassword());
        if (fullUnlock) {
            validateUsername("Normal username", normalUser);
            validatePassword("Normal password", normalPass);
            validateUsername("Senior username", seniorUser);
            validatePassword("Senior password", seniorPass);
            validateUsername("Super username", superUser);
            validatePassword("Super password", superPass);
            verifyArchive();
        }
        return new Settings(url, uri.getHost(), session, host, port,
                normalUser, normalPass, seniorUser, seniorPass, superUser, superPass);
    }

    private static void validateUsername(String label, String value) throws Exception {
        if (!value.matches("[A-Za-z0-9_.-]{1,32}"))
            throw new Exception(label + " must be 1-32 letters, numbers, dots, underscores, or hyphens.");
    }

    private static void validatePassword(String label, String value) throws Exception {
        if (value.length() < 4 || value.length() > 128 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new Exception(label + " must be 4-128 characters without line breaks.");
    }

    private void startTelnetOnly() {
        final Settings settings;
        try { settings = validateSettings(false); }
        catch (Exception ex) { showError(ex.getMessage()); return; }
        setBusy(true);
        new Thread(() -> {
            try {
                setStatus("Enabling Telnet...");
                terminalLine("POST " + settings.modemUrl + "/cgi-bin/http.cgi -> cmd 172 (enable Telnet)");
                terminalLine("Telnet response: " + postJson(settings.modemUrl, telnetJson(settings.sessionId)));
                if (!waitForPort(settings.modemHost, 23, 12000))
                    throw new Exception("The modem API responded, but Telnet port 23 did not open. Obtain a fresh session ID and retry.");
                terminalLine("Telnet port 23 is open.");
                setStatus("Telnet enabled and verified");
            } catch (Exception ex) { showErrorLater(ex.getMessage()); }
            finally { setBusyLater(false); }
        }, "zlt-stage").start();
    }

    private void startUnlock() {
        final Settings settings;
        try { settings = validateSettings(true); }
        catch (Exception ex) { showError(ex.getMessage()); return; }
        int answer = JOptionPane.showConfirmDialog(this,
                "The modem configuration and system files will be modified and the modem will reboot.\n" +
                "The super default is public; change it first if other users can reach the modem.\n\nContinue?",
                "Start unlock?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        setBusy(true);
        progress.setValue(0);
        new Thread(() -> unlock(settings), "zlt-unlock").start();
    }

    private void unlock(Settings settings) {
        try {
            terminalLine("===== Unlock started =====");
            terminalLine("Modem: " + settings.modemUrl + " | Local file host: " + settings.localHost + ":" + settings.port);
            setStatus("Step 1/3: Enabling Telnet...");
            terminalLine("[1/3] POST modem API -> cmd 172 (enable Telnet)");
            terminalLine("Telnet response: " + postJson(settings.modemUrl, telnetJson(settings.sessionId)));
            terminalLine("Waiting for Telnet port 23...");
            if (!waitForPort(settings.modemHost, 23, 12000))
                throw new Exception("Telnet port 23 did not open. Obtain a fresh session ID and retry.");
            terminalLine("Telnet port 23 is open.");
            setProgress(1);

            setStatus("Step 2/3: Starting local package server...");
            terminalLine("[2/3] Verifying and serving embedded x28.tgz locally");
            byte[] archive = readResource("/x28.tgz");
            packageServer = new PackageServer(settings.port, archive, installerScript(settings));
            packageServer.start();
            terminalLine("Package server listening: http://" + settings.localHost + ":" + settings.port + "/");
            setProgress(2);

            setStatus("Step 3/3: Installing files over Telnet...");
            terminalLine("[3/3] Connecting to telnet://" + settings.modemHost + ":23");
            TelnetShell shell = new TelnetShell(settings.modemHost);
            String output;
            try {
                String command = "wget http://" + settings.localHost + ":" + settings.port +
                        "/x28.sh -O /tmp/x28.sh && sh /tmp/x28.sh";
                terminalLine("modem$ " + command);
                terminalLine("----- raw modem output -----");
                output = shell.run(command, 35000);
                terminalLine("----- end modem output -----");
            } finally { shell.close(); }
            if (!output.contains("ZLT_INSTALL_OK"))
                throw new Exception("The installer did not confirm completion. Check the log, your OS firewall, and the selected PC IP.");
            setProgress(3);
            setStatus("Unlock sent successfully; the modem should now reboot.");
            terminalLine("SUCCESS: Installer confirmed completion. The modem is rebooting.");
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "The unlock files were installed. Wait for the modem to finish rebooting.",
                    "Unlock complete", JOptionPane.INFORMATION_MESSAGE));
        } catch (Exception ex) {
            terminalLine("ERROR: " + ex.getMessage());
            showErrorLater(ex.getMessage());
        }
        finally {
            if (packageServer != null) { packageServer.close(); packageServer = null; }
            setBusyLater(false);
        }
    }

    private static String telnetJson(String session) {
        return "{\"enabled\":\"1\",\"ip\":\"192.168.1.1 ; telnetd -l /bin/ash\",\"cmd\":172," +
                "\"method\":\"POST\",\"subcmd\":6,\"language\":\"EN\",\"sessionId\":\"" + session + "\"}";
    }

    private boolean waitForPort(String host, int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(host, port), 1000);
                return true;
            } catch (IOException ignored) {
                try { Thread.sleep(500); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }

    private static String postJson(String baseUrl, String json) throws Exception {
        URL url = new URL(baseUrl.replaceAll("/+$", "") + "/cgi-bin/http.cgi");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection instanceof HttpsURLConnection) configureInsecureTls((HttpsURLConnection) connection);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(body); }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = stream == null ? "" : new String(readAll(stream), StandardCharsets.UTF_8);
        if (status >= 400) throw new IOException("Modem returned HTTP " + status + ": " + response);
        return "HTTP " + status + " " + response;
    }

    private static void configureInsecureTls(HttpsURLConnection connection) throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        connection.setSSLSocketFactory(context.getSocketFactory());
        HostnameVerifier verifier = (hostname, session) -> true;
        connection.setHostnameVerifier(verifier);
    }

    private static void verifyArchive() throws Exception {
        byte[] archive = readResource("/x28.tgz");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(archive);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        if (!EXPECTED_MD5.equals(hex.toString())) throw new Exception("Embedded x28.tgz checksum mismatch.");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static byte[] installerScript(Settings settings) {
        String script = "#!/bin/sh\nset -e\n" +
                "escape_config_value() {\nprintf '%s' \"$1\" | sed 's/[\\\\&|\\\"]/\\\\&/g'\n}\n" +
                "set_config_value() {\nkey=\"$1\"\nvalue=$(escape_config_value \"$2\")\n" +
                "sed -i \"s|^export ${key}=.*|export ${key}=\\\"${value}\\\"|\" /mnt/data/etc/tzcfg/main_config\n}\n" +
                "echo '[installer] Remounting root filesystem read-write'\nmount -o remount,rw /\n" +
                "echo '[installer] Downloading embedded x28.tgz from the laptop'\n" +
                "wget http://" + settings.localHost + ":" + settings.port + "/x28.tgz -O /tmp/x28.tgz\n" +
                "hash=$(md5sum /tmp/x28.tgz | awk '{print $1}')\necho \"[installer] Archive MD5: $hash\"\n" +
                "if [ \"$hash\" = \"" + EXPECTED_MD5 + "\" ]; then\n" +
                "echo '[installer] Checksum verified; creating backups'\n" +
                "rm -rf /mnt/data/etc/tzcfg/update_config\n" +
                "[ -e /mnt/data/etc/tzcfg/main_config.bk ] || cp /mnt/data/etc/tzcfg/main_config /mnt/data/etc/tzcfg/main_config.bk\n" +
                "[ -e /tzwww/cgi-bin/http.cgi.bk ] || cp /tzwww/cgi-bin/http.cgi /tzwww/cgi-bin/http.cgi.bk\n" +
                "[ -e /usr/bin/mtk_netagent.bk ] || cp /usr/bin/mtk_netagent /usr/bin/mtk_netagent.bk\n" +
                "pids=$(ps | grep '[m]tk_netagent' | awk '{print $1}')\n[ -z \"$pids\" ] || kill $pids\n" +
                "echo '[installer] Extracting replacement files'\ntar -xzvf /tmp/x28.tgz -C /\n" +
                "echo '[installer] Applying selected login credentials'\n" +
                "set_config_value SYS_USER_LOGIN_NAME " + shellQuote(settings.normalUser) + "\n" +
                "set_config_value SYS_USER_LOGIN_PWD " + shellQuote(settings.normalPass) + "\n" +
                "set_config_value SYS_SENIOR_LOGIN_NAME " + shellQuote(settings.seniorUser) + "\n" +
                "set_config_value SYS_SENIOR_LOGIN_PWD " + shellQuote(settings.seniorPass) + "\n" +
                "set_config_value SYS_SUPER_LOGIN_NAME " + shellQuote(settings.superUser) + "\n" +
                "set_config_value SYS_SUPER_LOGIN_PWD " + shellQuote(settings.superPass) + "\n" +
                "echo '[installer] Disabling TR-069 remote management'\n" +
                "set_config_value USR_TR069_SW '0'\nset_config_value USR_TR069_PERIODIC_SW '0'\n" +
                "set_config_value USR_TR069_UPGRADE_AUTO_SW '0'\nset_config_value SYS_TR069_UPGRADE_PROMPT_SW '0'\n" +
                "set_config_value USR_TR069_LONG_CONNECT_ENABLE '0'\nset_config_value USR_TR069_ACS_URL ''\n" +
                "set_config_value USR_TR069_ACS_USERNAME ''\nset_config_value USR_TR069_ACS_PWD ''\n" +
                "set_config_value USR_TR069_CPE_USERNAME ''\nset_config_value USR_TR069_CPE_PWD ''\n" +
                "set_config_value USR_TR069_SERVER_IP ''\nset_config_value USR_TR069_SERVER_PORT ''\n" +
                "set_config_value USR_TR069_CHECK_VERSION_ADDR ''\n" +
                "echo '[installer] Restarting modem service'\n/usr/bin/mtk_netagent &\nsleep 3\nsync\n" +
                "echo ZLT_INSTALL_OK\nreboot\nelse\necho \"Archive checksum mismatch: $hash\"\nexit 1\nfi\n";
        return script.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readResource(String name) throws IOException {
        InputStream input = ZltX28Unlock.class.getResourceAsStream(name);
        if (input == null) throw new FileNotFoundException("Embedded resource not found: " + name);
        try { return readAll(input); } finally { input.close(); }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String detectLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(MODEM_IP), 9);
            String address = socket.getLocalAddress().getHostAddress();
            return "0.0.0.0".equals(address) ? "192.168.70.131" : address;
        } catch (Exception ex) { return "192.168.70.131"; }
    }

    private final class PackageServer implements Closeable {
        private final ServerSocket server;
        private final byte[] archive;
        private final byte[] script;
        private volatile boolean running = true;

        PackageServer(int port, byte[] archive, byte[] script) throws IOException {
            this.server = new ServerSocket(port);
            this.archive = archive;
            this.script = script;
        }

        void start() {
            Thread thread = new Thread(() -> {
                while (running) {
                    try {
                        final Socket client = server.accept();
                        Thread worker = new Thread(() -> serve(client), "zlt-http-client");
                        worker.setDaemon(true);
                        worker.start();
                    } catch (IOException ex) { if (running) terminalLine("Package server error: " + ex.getMessage()); }
                }
            }, "zlt-http-server");
            thread.setDaemon(true);
            thread.start();
        }

        private void serve(Socket client) {
            try (Socket socket = client) {
                socket.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                String path = request == null ? "" : request.split(" ").length > 1 ? request.split(" ")[1] : "";
                byte[] body;
                String type;
                int status;
                if ("/x28.tgz".equals(path)) { body = archive; type = "application/gzip"; status = 200; }
                else if ("/x28.sh".equals(path)) { body = script; type = "text/x-shellscript"; status = 200; }
                else { body = "Not found".getBytes(StandardCharsets.US_ASCII); type = "text/plain"; status = 404; }
                OutputStream out = socket.getOutputStream();
                String headers = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n" +
                        "Content-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
                terminalLine("Served " + path + " to " + socket.getInetAddress().getHostAddress());
            } catch (IOException ex) { terminalLine("Package transfer error: " + ex.getMessage()); }
        }

        @Override public void close() {
            running = false;
            try { server.close(); } catch (IOException ignored) { }
        }
    }

    private final class TelnetShell implements Closeable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        TelnetShell(String host) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, 23), 12000);
            socket.setSoTimeout(500);
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        String run(String command, long waitMs) throws IOException {
            readFor(1000);
            output.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            return readFor(waitMs);
        }

        private String readFor(long durationMs) throws IOException {
            long deadline = System.currentTimeMillis() + durationMs;
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                try {
                    int count = input.read(buffer);
                    if (count < 0) break;
                    ByteArrayOutputStream clean = new ByteArrayOutputStream();
                    for (int i = 0; i < count; i++) {
                        int value = buffer[i] & 0xff;
                        if (value == 255 && i + 2 < count) {
                            int command = buffer[++i] & 0xff;
                            int option = buffer[++i] & 0xff;
                            if (command == 253 || command == 254) output.write(new byte[]{(byte)255, (byte)252, (byte)option});
                            else if (command == 251 || command == 252) output.write(new byte[]{(byte)255, (byte)254, (byte)option});
                        } else clean.write(value);
                    }
                    output.flush();
                    byte[] bytes = clean.toByteArray();
                    all.write(bytes);
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    if (!text.isEmpty()) appendLog(text);
                } catch (SocketTimeoutException ignored) { }
            }
            return new String(all.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override public void close() { try { socket.close(); } catch (IOException ignored) { } }
    }

    private void appendLog(final String text) {
        SwingUtilities.invokeLater(() -> { logArea.append(text); logArea.setCaretPosition(logArea.getDocument().getLength()); });
    }
    private void terminalLine(String text) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        appendLog("[" + time + "] " + text + "\n");
    }
    private void setStatus(final String text) { SwingUtilities.invokeLater(() -> statusLabel.setText(text)); }
    private void setProgress(final int value) { SwingUtilities.invokeLater(() -> progress.setValue(value)); }
    private void setBusy(boolean busy) {
        unlockButton.setEnabled(!busy); telnetButton.setEnabled(!busy);
    }
    private void setBusyLater(final boolean busy) { SwingUtilities.invokeLater(() -> setBusy(busy)); }
    private void showError(String message) { JOptionPane.showMessageDialog(this, message, "Cannot continue", JOptionPane.ERROR_MESSAGE); }
    private void showErrorLater(final String message) {
        setStatus("Failed");
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Unlock failed", JOptionPane.ERROR_MESSAGE));
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--dump-installer".equals(args[0])) {
            Settings test = new Settings("https://192.168.70.1", "192.168.70.1", "testsession",
                    "192.168.70.55", 8000, "user", "label-password", "root", "private-password",
                    "superadmin", "strong_password");
            try { System.out.write(installerScript(test)); }
            catch (IOException ex) { System.err.println(ex.getMessage()); System.exit(1); }
            return;
        }
        if (args.length == 1 && "--self-test".equals(args[0])) {
            try {
                verifyArchive();
                Settings test = new Settings("https://192.168.70.1", "192.168.70.1", "testsession",
                        "192.168.70.55", 8000, "user", "label-password", "root", "private-password",
                        "superadmin", "strong_password");
                String script = new String(installerScript(test), StandardCharsets.UTF_8);
                if (!script.contains("ZLT_INSTALL_OK") || !script.contains(EXPECTED_MD5) ||
                        !script.contains("SYS_SUPER_LOGIN_NAME 'superadmin'") ||
                        !script.contains("USR_TR069_SW '0'") || script.contains("cmd 302"))
                    throw new Exception("Generated installer validation failed.");
                System.out.println("ZLT X28 JAR self-test: OK");
            } catch (Exception ex) {
                System.err.println("ZLT X28 JAR self-test failed: " + ex.getMessage());
                System.exit(1);
            }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new ZltX28Unlock().setVisible(true);
        });
    }
}
