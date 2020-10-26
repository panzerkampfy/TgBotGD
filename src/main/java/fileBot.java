import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

//изначально бот не загружает файлы вообще
//чтобы он начал загружать необходимо добавить usernames в whitelist
//добавление в вайтлиста делается с помощью команды /add

public class fileBot extends TelegramLongPollingBot {
    private String BotToken = "";/////////////////////////////////////////////////////////////////////
    private String BotUsername = "";/////////////////////////////////////////////////////////////////////
    private String whitelistPath = "C:\\Users\\panze\\Pictures\\whitelist.json";
    private static File createGoogleFolder(String folderIdParent, String folderName) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        if (folderIdParent != null) {
            List<String> parents = Arrays.asList(folderIdParent);
            fileMetadata.setParents(parents);
        }
        Drive driveService = GoogleDriveUtils.getDriveService();
        File file = driveService.files().create(fileMetadata).setFields("id, name").execute();
        return file;
    }
    private static File _createGoogleFile(String googleFolderIdParent, String contentType,
                                          String customFileName, AbstractInputStreamContent uploadStreamContent) throws IOException {

        File fileMetadata = new File();
        fileMetadata.setName(customFileName);
        List<String> parents = Arrays.asList(googleFolderIdParent);
        fileMetadata.setParents(parents);
        Drive driveService = GoogleDriveUtils.getDriveService();
        File file = driveService.files().create(fileMetadata, uploadStreamContent)
                .setFields("id, webContentLink, webViewLink, parents").execute();
        return file;
    }
    public static File createGoogleFile(String googleFolderIdParent, String contentType, //
                                        String customFileName, java.io.File uploadFile) throws IOException {
        AbstractInputStreamContent uploadStreamContent = new FileContent(contentType, uploadFile);
        return _createGoogleFile(googleFolderIdParent, contentType, customFileName, uploadStreamContent);
    }

    private void sendMsg(Long chatId, String text){
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void onUpdateReceived(Update update) {
//        System.out.println("chat ID - " + update.getMessage().getChatId());
//        System.out.println("username - " + update.getMessage().getFrom().getUserName());
        Long chatId = update.getMessage().getChatId();
        if (chatId < 0){
            //check commands
            if (update.getMessage().hasText()){
                String message_text = update.getMessage().getText().trim();
                System.out.println(message_text);
                if (message_text.equals("/add") || message_text.equals("/delete")){
                    String start_message = "example: /add @gosha @viktor";
                    sendMsg(chatId, start_message);
                }
                else{
                    //список юзернеймов с ид папками хранится в формате JSON
                    //add to whitelist
                    if(message_text.substring(0,4).equals("/add")){
                        message_text = message_text.substring(6);
                        String[] usernames = message_text.split(" @");
                        //хз почему именно json, Для java лучше хмл поди
                        //структура листа для понимая
//                    {"list" : [{"folder":"24h2h4h24h23jk42j3","name":"Wiz_Stale"},
//                        {"folder":"24h2h4h24h23jk42j3","name":"grisha"},
//                        {"folder":"24h2h4h24h23jk42j3","name":"fifa"}]}
                        //че я дальше делаю
                        //прочитал файл в стринг, засунул в JSON, прочитал имена для поиска

                        String text = null;
                        try {
                            text = new String(Files.readAllBytes(Paths.get(whitelistPath)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        org.json.JSONObject obj = new org.json.JSONObject(text);
                        org.json.JSONArray list = obj.getJSONArray("list");
                        String folderID = null;
                        for (String username : usernames){
                            System.out.println(username);
                            String text_msg = "";
                            Integer j = 0;
                            for (int i=0; i<list.length(); i++){
                                String name = list.getJSONObject(i).getString("name");
                                if (username.equals(name)){
                                    //есть совпадеие, вторую папку под этого юзера делать не бдуем
                                    // надо найти этого юзера в листе
                                    //а точнее он найден, возьмем ид его его папки
                                    folderID = list.getJSONObject(i).getString("folder");
                                    j = 1;
                                    System.out.println("найдена папка with id= "+ folderID);
                                    text_msg = text_msg + "@" + username + " already in whitelist" + System.lineSeparator();
                                }
//
                            }
                            if (j == 0){
                                //совпадений нет, надо создать папку на ГД и записать новобранца в файл
                                File newfolder = null;
                                try {
                                    newfolder = createGoogleFolder("1vFw6Td6LnlLYF2fAebJe5fOxnA_RXTvR", username);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
//                                folderID = newfolder.getId();
//                                System.out.println("Created folder with id= "+ folderID + "  name= "+ newfolder.getName());

                                //запись в файл нового пользователя
                                JSONObject newuser = new JSONObject();
                                newuser.put("name", newfolder.getName());
                                newuser.put("folder", newfolder.getId());
                                String str_newuser = newuser.toJSONString();
                                text = text.substring(0, text.length()-2) + "," + str_newuser + "]}";
                                try (FileWriter file = new FileWriter(whitelistPath)) {
                                    file.write(text);
                                    file.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                sendMsg(chatId, "@" + username + " added to whitelist");
                            }

                        }
                    }

                    //удаление написал через простые стринги, вроде проще, но выглядит невнятно
                    //и мб медленне чем через библиотеку
                    if(message_text.substring(0,7).equals("/delete")){
                        message_text = message_text.substring(9);
                        String[] usernames = message_text.split(" @");
                        String text = null;
                        try {
                            text = new String(Files.readAllBytes(Paths.get(whitelistPath)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        for (String username : usernames){
                            Integer k = text.indexOf(username);
                            System.out.println(k);
                            System.out.println(text.charAt(116));
                            text = text.substring(0, k-54) + text.substring(k+username.length()+3,text.length());
                            System.out.println(text);
                            try (FileWriter file = new FileWriter(whitelistPath)) {
                                file.write(text);
                                file.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            sendMsg(chatId, "@" + username + " deleted from whitelist");
                        }
                    }
                    if(message_text.substring(0,10).equals("/whitelist")){

                        String text = null;
                        try {
                            text = new String(Files.readAllBytes(Paths.get(whitelistPath)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        org.json.JSONObject obj = new org.json.JSONObject(text);
                        org.json.JSONArray list = obj.getJSONArray("list");
                        String reply = "Whitelist: ";
                        for (int i=0; i<list.length(); i++){
                            reply = reply + "@" + list.getJSONObject(i).getString("name") + " ";
//
                        }
                        sendMsg(chatId, reply);
                    }
                }
            }
            //
            if (update.getMessage().hasDocument()){
                String text = null;
                try {
                    text = new String(Files.readAllBytes(Paths.get(whitelistPath)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                org.json.JSONObject obj = new org.json.JSONObject(text);
                org.json.JSONArray list = obj.getJSONArray("list");
                String username = update.getMessage().getFrom().getUserName();
                for (int i=0; i<list.length(); i++){
                    String name = list.getJSONObject(i).getString("name");
//                    System.out.println("сравнение " + username + " " + name);
                    if (username.equals(name)){
                        //есть совпадеие, тогда скачиваем файл в его папку
                        String folderID = list.getJSONObject(i).getString("folder");
                        String uploadedFileId = update.getMessage().getDocument().getFileId();
                        String filePath = "";
                        GetFile getFile = new GetFile().setFileId(uploadedFileId);
                        try {
                            filePath = execute(getFile).getFilePath();
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                        String localpath = "C:\\Users\\panze\\Pictures\\" + filePath.substring(10);;
                        InputStream is = null;
                        try {
                            is = new URL("https://api.telegram.org/file/bot" + getBotToken()+ "/" + filePath).openStream();
                            System.out.println("URL - " + "https://api.telegram.org/file/bot" + getBotToken()+ "/" + filePath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            FileUtils.copyInputStreamToFile(is, new java.io.File(localpath));
                            System.out.println("done download to pc");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        java.io.File uploadFile = new java.io.File(localpath);
                        try {
                            File googleFile = createGoogleFile(folderID, "text/plain", filePath.substring(10), uploadFile);
                            System.out.println("done download to GD");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        uploadFile.delete();
                        System.out.println("delete from PC");
                    }
                }

            }

        }

    }

    public String getBotUsername() {
        return BotUsername;
    }

    public String getBotToken() {
        return BotToken;
    }

}