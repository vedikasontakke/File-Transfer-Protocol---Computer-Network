import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class FTPServer {
    private static final int PORT = 12345;
    private static final String NOTICE_DIRECTORY = "notices";

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        JFrame frame = new FTPServerGUI(executor, NOTICE_DIRECTORY);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("FTP Server is running on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(new ClientHandler(clientSocket, (FTPServerGUI) frame));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private FTPServerGUI frame;

    public ClientHandler(Socket clientSocket, FTPServerGUI frame) {
        this.clientSocket = clientSocket;
        this.frame = frame;
    }

    @Override
    public void run() {
        try (
                DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());) {
            while (true) {
                try {
                    String request = dataInputStream.readUTF();
                    if (request.equals("LIST")) {
                        frame.sendFileList(dataOutputStream);
                    } else if (request.startsWith("GET ")) {
                        String fileName = request.substring(4);
                        frame.sendFile(fileName, dataOutputStream);
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class FTPServerGUI extends JFrame {
    private ExecutorService executor;
    private JTextArea logTextArea;
    private JButton startServerButton;
    private JButton addButton;
    private String NOTICE_DIRECTORY;

    public FTPServerGUI(ExecutorService executor, String noticeDirectory) {
        this.executor = executor;
        this.NOTICE_DIRECTORY = noticeDirectory;
        this.setTitle("FTP Server");
        this.setSize(400, 300);
        this.setLayout(new BorderLayout());

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        this.add(logScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        startServerButton = new JButton("Start Server");
        addButton = new JButton("Add File to Notices");

        startServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServerButton.setEnabled(false);
                logTextArea.append("Server started\n");
            }
        });

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int result = fileChooser.showOpenDialog(FTPServerGUI.this);

                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    addFileToNotices(selectedFile);
                }
            }
        });

        buttonPanel.add(startServerButton);
        buttonPanel.add(addButton);
        this.add(buttonPanel, BorderLayout.SOUTH);
    }

    public void sendFileList(DataOutputStream dataOutputStream) {
        File noticeDir = new File(NOTICE_DIRECTORY);
        File[] files = noticeDir.listFiles();
        if (files != null) {
            try {
                for (File file : files) {
                    dataOutputStream.writeUTF(file.getName());
                }
                dataOutputStream.writeUTF("END");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendFile(String fileName, DataOutputStream dataOutputStream) {
        File file = new File(NOTICE_DIRECTORY, fileName);
        if (file.exists() && file.isFile()) {
            try {
                dataOutputStream.writeUTF("FOUND");
                dataOutputStream.writeLong(file.length());

                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        dataOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                dataOutputStream.writeUTF("NOT_FOUND");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void addFileToNotices(File file) {
        if (file.exists() && file.isFile()) {
            File targetDirectory = new File(NOTICE_DIRECTORY);
            if (!targetDirectory.exists()) {
                targetDirectory.mkdir();
            }

            String targetFilePath = NOTICE_DIRECTORY + File.separator + file.getName();
            File targetFile = new File(targetFilePath);

            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
                fileOutputStream.close();
                logTextArea.append("File added to notices: " + file.getName() + "\n");
            } catch (IOException e) {
                logTextArea.append("Error adding file to notices: " + e.getMessage() + "\n");
            }
        } else {
            logTextArea.append("Selected file does not exist or is not a regular file.\n");
        }
    }
}
