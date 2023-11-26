import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.event.*;

public class FTPClient {
    private JFrame frame;
    private JList<String> fileList;
    private DefaultListModel<String> fileModel;
    private JTextArea textArea;
    private JButton downloadButton;
    private JButton reconnectButton;
    private String serverAddress; // Store the server IP address

    public FTPClient() {
        frame = new JFrame("FTP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setSize(600, 400);

        createUIComponents();

        frame.setVisible(true);

        serverAddress = JOptionPane.showInputDialog(frame, "Enter Server IP Address:");
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Server IP cannot be empty.");
            System.exit(0);
        }

        listNotices();
    }

    private void createUIComponents() {
        createFileList();
        createTextArea();
        createButtonPanel();
    }

    private void createFileList() {
        JPanel filePanel = new JPanel();
        filePanel.setLayout(new BorderLayout());

        fileModel = new DefaultListModel<>();
        fileList = new JList<>(fileModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                downloadButton.setEnabled(true);
            }
        });

        JScrollPane fileScrollPane = new JScrollPane(fileList);
        filePanel.add(fileScrollPane, BorderLayout.CENTER);

        frame.add(filePanel, BorderLayout.WEST);
    }

    private void createTextArea() {
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane textScrollPane = new JScrollPane(textArea);
        frame.add(textScrollPane, BorderLayout.CENTER);
    }

    private void createButtonPanel() {
        JPanel buttonPanel = new JPanel();

        downloadButton = new JButton("Download Selected Notice");
        downloadButton.setEnabled(false);

        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedFile = fileList.getSelectedValue();
                downloadNotice(selectedFile);
            }
        });

        reconnectButton = new JButton("Reconnect to Another Server");
        reconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reconnectToServer();
            }
        });

        buttonPanel.add(downloadButton);
        buttonPanel.add(reconnectButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void reconnectToServer() {
        String newServerAddress = JOptionPane.showInputDialog(frame, "Enter New Server IP Address:");
        if (newServerAddress == null || newServerAddress.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Server IP cannot be empty.");
        } else {
            serverAddress = newServerAddress;
            textArea.setText(""); // Clear the text area
            listNotices();
        }
    }

    private void listNotices() {
        try (Socket socket = new Socket(serverAddress, 12345);
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

            dataOutputStream.writeUTF("LIST");
            listNotices(dataInputStream);
        } catch (IOException e) {
            textArea.append("Error: " + e.getMessage() + "\n");
            JOptionPane.showMessageDialog(frame, "Server not available at " + serverAddress, "Server Not Found", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadNotice(String fileName) {
        textArea.setText("Downloading " + fileName + "...\n");

        try (Socket socket = new Socket(serverAddress, 12345);
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

            dataOutputStream.writeUTF("GET " + fileName);
            downloadNotice(fileName, dataInputStream);
        } catch (IOException e) {
            textArea.append("Error: " + e.getMessage() + "\n");
            JOptionPane.showMessageDialog(frame, "Server not available at " + serverAddress, "Server Not Found", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void listNotices(DataInputStream dataInputStream) throws IOException {
        fileModel.clear();
        String fileName;
        while (!(fileName = dataInputStream.readUTF()).equals("END")) {
            fileModel.addElement(fileName);
        }
    }

    private void downloadNotice(String fileName, DataInputStream dataInputStream) throws IOException {
        String response = dataInputStream.readUTF();
        if (response.equals("FOUND")) {
            long fileSize = dataInputStream.readLong();
            try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while (fileSize > 0 && (bytesRead = dataInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    fileSize -= bytesRead;
                }
            }
            textArea.append("File " + fileName + " downloaded successfully.\n");
        } else {
            textArea.append("File " + fileName + " not found on the server.\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new FTPClient();
            }
        });
    }
}