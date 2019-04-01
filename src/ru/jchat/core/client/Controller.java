package ru.jchat.core.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    TextArea textArea;
    @FXML
    TextField msgField;
    @FXML
    HBox authPanel;
    @FXML
    HBox msgPanel;
    @FXML
    TextField loginField;
    @FXML
    PasswordField passField;
    @FXML
    ListView<String> clientsListView;

    private static final int MSG_LOG_SHOW_SIZE = 100;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    final String SERVER_IP = "localhost";
    final int SERVER_PORT = 8189;

    private boolean authorized;
    private String myNick;

    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private List<String> log;

    private ObservableList<String> clientsList;

    private void initLog(String nick) {
        try{
            String logName = "msg_log_" + nick + ".txt";
            File msgLog = new File(logName);
            msgLog.createNewFile();
            bufferedWriter = new BufferedWriter(new FileWriter(logName, true));
            bufferedReader = new BufferedReader(new FileReader(logName));
            log = new ArrayList<>();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    private void showLog() throws IOException {
        String s;
        while ((s = bufferedReader.readLine()) != null){
            log.add(s);
        }
        for (int i = (log.size() <= MSG_LOG_SHOW_SIZE) ? 0 : log.size() - MSG_LOG_SHOW_SIZE; i < log.size(); i++){
            textArea.appendText(log.get(i) + "\n");
        }
        bufferedReader.close();
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        if (authorized){
            msgPanel.setVisible(true);
            msgPanel.setManaged(true);
            authPanel.setVisible(false);
            authPanel.setManaged(false);
            clientsListView.setVisible(true);
            clientsListView.setManaged(true);
        }
        else {
            msgPanel.setVisible(false);
            msgPanel.setManaged(false);
            authPanel.setVisible(true);
            authPanel.setManaged(true);
            clientsListView.setVisible(false);
            clientsListView.setManaged(false);
            myNick = "";
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);
    }

    public void connect() {
        try{
            socket = new Socket(SERVER_IP, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            clientsList = FXCollections.observableArrayList();
            clientsListView.setItems(clientsList);

            clientsListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<String>() {
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (!empty){
                                setText(item);
                                if (item.equals(myNick)){
                                    setStyle("-fx-font-weight: bold; -fx-background-color: #00ff00;");
                                }
                            }
                            else {
                                setGraphic(null);
                                setText(null);
                            }
                        }
                    };
                }
            });
            Thread t = new Thread(() -> {
                try{
                    while (true){
                        String s = in.readUTF();
                        if (s.startsWith("/authok ")){
                            setAuthorized(true);
                            myNick = s.split("\\s")[1];
                            initLog(myNick);
                            showLog();
                            break;
                        }
                        textArea.appendText(s + "\n");
                    }
                    while (true){
                        String s = in.readUTF();
                        if (s.startsWith("/")){
                            if (s.startsWith("/clientslist ")){
                                String[] data = s.split("\\s");
                                Platform.runLater(() -> {
                                    clientsList.clear();
                                    for (int i = 1; i < data.length; i++){
                                        clientsList.addAll(data[i]);
                                    }
                                });
                            }
                        }
                        else {
                            textArea.appendText(s + "\n");
                            bufferedWriter.write(s + "\n");
                            bufferedWriter.flush();
                        }
                    }
                }
                catch (IOException e){
                    showAlert("Сервер перестал отвечать");
                }
                finally {
                    setAuthorized(false);
                    try{
                        socket.close();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }
        catch (IOException e){
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void sendAuthMsg() {
        if (loginField.getText().isEmpty() || passField.getText().isEmpty()){
            showAlert("Указаны неполные данные авторизации");
            return;
        }
        if (socket == null || socket.isClosed()){
            connect();
        }
        try{ // "/auth login pass"
            out.writeUTF("/auth " + loginField.getText() + " " + passField.getText());
            loginField.clear();
            passField.clear();
        }
        catch (IOException e){
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void sendMsg() {
        try{
            out.writeUTF(msgField.getText());
            msgField.clear();
            msgField.requestFocus();
        }
        catch (IOException e){
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Возникли проблемы");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    public void clientsListClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2){
            msgField.setText("/w " + clientsListView.getSelectionModel().getSelectedItem() + " ");
            msgField.requestFocus();
            msgField.selectEnd();
        }
    }
}
