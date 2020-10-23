import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.apache.commons.io.FileUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.URL;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class fileBot extends TelegramLongPollingBot {
    private String BotToken = "";/////////////////////////////////////////////////////////////////////
    private String BotUsername = "";/////////////////////////////////////////////////////////////////////

    private static File _createGoogleFile(String googleFolderIdParent, String contentType, //
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

    private  void sendMsg(Update msg, String text){
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(msg.getMessage().getChatId());
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void onUpdateReceived(Update update) {
        String uploadedFileId = update.getMessage().getDocument().getFileId();
//        String message_text = "File ID - " + uploadedFileId + "\n";
        String filePath = "";
        GetFile getFile = new GetFile().setFileId(uploadedFileId);
        try {
            filePath = execute(getFile).getFilePath();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
//        message_text =  message_text + "File Path - " + filePath;
//        sendMsg(update, message_text);

        String name = filePath.substring(10);
        String localpath = "C:\\Users\\panze\\Pictures\\" + name;
//        java.io.File localFile;
//        localFile = new java.io.File(localpath);
        
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

        String googFolderIdParent  = "";/////////////////////////////////////////////////////////////////////
        try {
            File googleFile = createGoogleFile(googFolderIdParent, "text/plain", name, uploadFile);
            System.out.println("done download to GD");
        } catch (IOException e) {
            e.printStackTrace();
        }
        uploadFile.delete();
        System.out.println("delete from PC");

    }

    public String getBotUsername() {
        return BotUsername;
    }

    public String getBotToken() {
        return BotToken;
    }

}