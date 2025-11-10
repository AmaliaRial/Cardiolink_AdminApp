import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Swing interface for Admin.java
 * - Connect to server (IP + Port)
 * - Send "ADMMIN" handshake
 * - Buttons: Shut Down (asks for password(Which is: cardiolink_admin_pass)), Disconnect: Exit
 * - Live log area for server responses
 *
 * Drop this file alongside your project and run the main().
 */
public class AdminSwing extends JFrame {

    private final java.util.concurrent.atomic.AtomicBoolean closing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean closed  = new java.util.concurrent.atomic.AtomicBoolean(false);

    // UI
    private JTextField ipField;
    private JTextField portField;
    private JButton connectBtn;
    private JButton shutdownBtn;
    private JButton disconnectBtn;
    private JTextArea logArea;
    private JLabel statusLabel;

    // Networking
    private volatile Socket socket;
    private volatile DataInputStream in;
    private volatile DataOutputStream out;

    // State
    private volatile boolean listening;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AdminSwing ui = new AdminSwing();
            ui.setVisible(true);
        });
    }

    public AdminSwing() {
        super("CardioLink – Admin Console");
        buildUI();
        wireEvents();
    }

    private void buildUI() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(720, 520);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        // Top: connection panel
        JPanel connPanel = new JPanel(new GridBagLayout());
        connPanel.setBorder(BorderFactory.createTitledBorder("Connection"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_END;
        connPanel.add(new JLabel("Server IP:"), c);

        ipField = new JTextField("127.0.0.1", 14);
        c.gridx = 1; c.gridy = 0; c.anchor = GridBagConstraints.LINE_START;
        connPanel.add(ipField, c);

        c.gridx = 0; c.gridy = 1; c.anchor = GridBagConstraints.LINE_END;
        connPanel.add(new JLabel("Port:"), c);

        portField = new JTextField("9000", 6);
        c.gridx = 1; c.gridy = 1; c.anchor = GridBagConstraints.LINE_START;
        connPanel.add(portField, c);

        connectBtn = new JButton("Connect");
        c.gridx = 2; c.gridy = 0; c.gridheight = 2; c.fill = GridBagConstraints.VERTICAL;
        connPanel.add(connectBtn, c);

        root.add(connPanel, BorderLayout.NORTH);

        // Center: log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Server Log"));
        root.add(scroll, BorderLayout.CENTER);

        // Bottom: actions + status
        JPanel bottom = new JPanel(new BorderLayout(8,8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        shutdownBtn = new JButton("Shut Down Server");
        shutdownBtn.setEnabled(false);
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);
        actions.add(shutdownBtn);
        actions.add(disconnectBtn);

        statusLabel = new JLabel("Disconnected");
        bottom.add(actions, BorderLayout.WEST);
        bottom.add(statusLabel, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        connectBtn.addActionListener(e -> onConnect());
        // user-initiated disconnect: try to send "exit" once
        disconnectBtn.addActionListener(e -> onDisconnect(true));
        shutdownBtn.addActionListener(e -> onShutdown());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                onDisconnect(true);
                dispose();
                System.exit(0);
            }
        });
    }

    // === Actions ===

    private void onConnect() {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();

        if (!isValidIPv4(ip)) {
            append("Invalid IP address: " + ip + ". Check for errors.");
            append("It should be in IPv4 format (e.g. 192.168.0.1 or 103.101.101.103) or 'localhost' if the Server is running from this computer.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            append("Invalid port: " + portStr);
            append("Port should be between 1 and 65535.");
            return;
        }

        connectBtn.setEnabled(false);
        ipField.setEnabled(false);
        portField.setEnabled(false);

        connectBtn.setEnabled(false); ipField.setEnabled(false); portField.setEnabled(false);
        append("Connecting to " + ip + " : in port " + port + " ...");

        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // handshake literal must match your server ("ADMIN" per your log)
                out.writeUTF("ADMIN");
                out.flush();
                append("Conexion request sent: ADMIN");

                // reset flags for this session
                closing.set(false);
                closed.set(false);

                setConnectedState(true);
                listening = true;
                append("Connexion established.");
                listenLoop();

            } catch (IOException ex) {
                append("Connection failed: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    ipField.setEnabled(true);
                    portField.setEnabled(true);
                    setConnectedState(false);
                });
            }
        }, "Admin-Connect").start();
    }

    private void onShutdown() {
        if (!isConnected()) return;

        String pwd = promptPassword();
        //PASSWORD = cardiolink_admin_pass
        if (pwd == null) return; // cancelled

        closing.set(true);

        new Thread(() -> {
            try {
                // Mirror the console flow: send the option, then the password
                out.writeUTF("Shut down");
                out.flush();

                out.writeUTF(pwd);
                out.flush();
                append("Shutdown requested. Waiting for server response...");

            } catch (IOException ex) {
                append("Error sending shutdown: " + ex.getMessage());
                onDisconnect(false);
            }
        }, "Admin-Shutdown").start();
    }

    private void onDisconnect(boolean sendExit) {

        // make it idempotent: run once
        if (!closed.compareAndSet(false, true)) return;

        listening = false;

        new Thread(() -> {
            try {
                // Mirror the console flow: send the option, then the password
                out.writeUTF("exit");
                out.flush();
                append("Disconnecting from server...");

            } catch (IOException ex) {
                //append("Error sending disconnection: " + ex.getMessage());
                //Ignore since we are disconnecting anyway
            }
        }, "Admin-Disconnected").start();

        listening = false;
        closeQuietly(in);
        closeQuietly(out);
        closeQuietly(socket);
        setConnectedState(false);
        connectBtn.setEnabled(true);
        ipField.setEnabled(true);
        portField.setEnabled(true);
        append("Disconnected from server.");

    }

    // === Helpers ===

    private void listenLoop() {
        // Read lines/messages opportunistically and print to log.
        // This assumes the server speaks with writeUTF. Adjust if your protocol differs.
        try {
            while (listening && !socket.isClosed()) {
                String msg = in.readUTF();           // blocks
                append("[SERVER] " + msg);
                if (msg != null && msg.toLowerCase().contains("closing")) {
                    append("Server indicates closing. Disconnecting client...");
                    // remote-driven close: do NOT send "exit"
                    onDisconnect(false);
                    return;
                }
            }
        } catch (IOException ex) {
            if (listening) append("Connection lost: " + ex.getMessage());
            // remote/IO close: do NOT send "exit"
            onDisconnect(false);
        }
    }

    /*
    private void readAvailableOneShot() {
        try {
            // Try to read up to a few messages that might come as a response
            // without permanently blocking the thread.
            socket.setSoTimeout(1500);
            for (int i = 0; i < 5; i++) {
                String msg = in.readUTF();
                append("[SERVER] " + msg);
            }
        } catch (IOException ignore) {
            // timeout or no more messages; that's fine
            try {
                socket.setSoTimeout(0);
            } catch (IOException e) {
                // ignore
            }
        }
    }*/

    private void setConnectedState(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            shutdownBtn.setEnabled(connected);
            disconnectBtn.setEnabled(connected);
            statusLabel.setText(connected ? "Connected" : "Disconnected");
        });
    }

    private boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private String promptPassword() {
        JPasswordField pf = new JPasswordField();
        pf.requestFocusInWindow();
        int ok = JOptionPane.showConfirmDialog(
                this, pf, "Enter admin password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (ok == JOptionPane.OK_OPTION) {
            return new String(pf.getPassword());
        }
        return null;
    }

    private static boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if ("localhost".equalsIgnoreCase(ip)) return true;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                if (p.isEmpty() || (p.length() > 1 && p.startsWith("0"))) return false;
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return false;
            }
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignore) {}
    }


}
