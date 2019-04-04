package ru.jchat.core.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nick;
    private int id;

    public String getNick() {
        return nick;
    }

    public ClientHandler(Server server, Socket socket) {
        ExecutorService service = Executors.newCachedThreadPool();
        try{
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());

            service.submit(new Thread(() -> {
                try{
                    while (true){
                        String msg = in.readUTF();
                        if (msg.startsWith("/auth ")){
                            String[] data = msg.split("\\s");
                            if (data.length == 3){
                                String newNick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);
                                if (newNick != null){
                                    if (!server.isNickBusy(newNick)){
                                        id = server.getAuthService().getIdByLoginAndPass(data[1], data[2]);
                                        nick = newNick;
                                        sendMsg("/authok " + newNick);
                                        server.subscribe(this);
                                        break;
                                    }
                                    else {
                                        sendMsg("Учетная запись уже занята");
                                    }
                                }
                                else {
                                    sendMsg("Неверный логин/пароль");
                                }
                            }
                        }
                    }
                    while (true){
                        String msg = in.readUTF();
                        System.out.println(nick + ": " + msg);
                        if (msg.startsWith("/")){
                            if (msg.equals("/end")) break;
                            if (msg.startsWith("/w ")){ // /w nick1 hello java
                                String[] data = msg.split("\\s", 3);
                                server.sendPrivateMsg(this, data[1], data[2]);
                            }
                            if (msg.startsWith("/newnick")){
                                String newNick = msg.split("\\s", 2)[1];
                                if (newNick != null && !newNick.trim().isEmpty()){
                                    if (server.getAuthService().isNickFree(newNick)){
                                        if (server.getAuthService().changeNick(id, newNick)){
                                            nick = newNick;
                                            server.unsubscribe(this);
                                            server.subscribe(this);
                                        }
                                        else {
                                            sendMsg("Ошибка смены ника. Попробуйте ещё раз.");
                                        }
                                    }
                                    else {
                                        sendMsg("Ник занят, введите другой.");
                                    }
                                }
                            }
                        }
                        else {
                            server.broadcastMsg(nick + ": " + msg);
                        }
                    }
                } catch (IOException | SQLException e){
                    e.printStackTrace();
                } finally {
                    nick = null;
                    server.unsubscribe(this);
                    try{
                        socket.close();
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }

            }));
            //            }).start();

        } catch (IOException e){
            e.printStackTrace();
        }
        finally {
            service.shutdown();
        }
    }

    public void sendMsg(String msg) {
        try{
            out.writeUTF(msg);
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
