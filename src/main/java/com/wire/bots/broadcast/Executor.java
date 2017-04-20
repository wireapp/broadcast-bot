//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.broadcast;

import com.wire.bots.broadcast.model.Broadcast;
import com.wire.bots.broadcast.model.Config;
import com.wire.bots.broadcast.storage.DbManager;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.assets.Picture;
import com.wire.bots.sdk.models.AssetKey;
import com.wire.bots.sdk.models.ImageMessage;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.User;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class Executor {
    private final ClientRepo repo;
    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(20);
    private final DbManager dbManager;
    private final Config config;

    public Executor(ClientRepo repo, Config config) {
        this.repo = repo;
        this.dbManager = new DbManager(config.getDatabase());
        this.config = config;
    }

    public void broadcast(Picture picture) throws Exception {
        File[] botDirs = getCryptoDirs();

        WireClient wireClient = repo.getWireClient(botDirs[0].getName());
        AssetKey assetKey = wireClient.uploadAsset(picture);
        picture.setAssetKey(assetKey.key);
        picture.setAssetToken(assetKey.token);

        broadcastPicture(picture);
    }

    public void broadcast(ImageMessage msg, byte[] imgData) throws Exception {
        Picture picture = new Picture(imgData, msg.getMimeType());
        picture.setSize((int) msg.getSize());
        picture.setWidth(msg.getWidth());
        picture.setHeight(msg.getHeight());
        picture.setAssetKey(msg.getAssetKey());
        picture.setAssetToken(msg.getAssetToken());
        picture.setOtrKey(msg.getOtrKey());
        picture.setSha256(msg.getSha256());
        picture.setMessageId(msg.getMessageId());

        broadcastPicture(picture);
    }

    public void broadcast(TextMessage msg) throws Exception {
        String text = msg.getText();
        String messageId = msg.getMessageId();

        if (text.startsWith("http")) {
            broadcastUrl(text);
        } else {
            broadcastText(messageId, text);
        }
    }

    public void broadcastUrl(final String url) throws Exception {
        File[] botDirs = getCryptoDirs();
        WireClient wireClient = repo.getWireClient(botDirs[0].getName());

        final String title = extractPageTitle(url);
        final Picture preview = uploadPreview(wireClient, extractPagePreview(url));

        saveBroadcast(url, title, preview);

        for (File botDir : botDirs) {
            final String botId = botDir.getName();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    sendLink(url, title, preview, botId);
                }
            });
        }
    }

    public void broadcastUrl(final String url, final String botId) throws Exception {
        WireClient wireClient = repo.getWireClient(botId);

        final String title = extractPageTitle(url);
        final Picture preview = uploadPreview(wireClient, extractPagePreview(url));

        saveBroadcast(url, title, preview);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                sendLink(url, title, preview, botId);
            }
        });
    }

    public void broadcastText(final String messageId, final String text) throws SQLException {
        File[] botDirs = getCryptoDirs();

        Broadcast b = new Broadcast();
        b.setMessageId(messageId);
        b.setText(text);

        dbManager.insertBroadcast(b);

        for (File botDir : botDirs) {
            final String botId = botDir.getName();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    sendText(messageId, text, botId);
                }
            });
        }
    }

    public void revokeBroadcast(final String messageId) throws SQLException {
        dbManager.deleteBroadcast(messageId);

        for (File botDir : getCryptoDirs()) {
            final String botId = botDir.getName();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    deleteMessage(messageId, botId);
                }
            });
        }
    }

    public void forwardFeedback(TextMessage msg) throws Exception {
        if (!hasAdminConv()) return;

        WireClient feedbackClient = repo.getWireClient(config.getAdmin());
        ArrayList<String> ids = new ArrayList<>();
        ids.add(msg.getUserId());
        for (User user : feedbackClient.getUsers(ids)) {
            String feedback = String.format("**%s** wrote: _%s_", user.name, msg.getText());
            feedbackClient.sendText(feedback);
        }
    }

    public void forwardFeedback(ImageMessage msg) throws Exception {
        if (!hasAdminConv()) return;

        WireClient feedbackClient = repo.getWireClient(config.getAdmin());
        ArrayList<String> ids = new ArrayList<>();
        ids.add(msg.getUserId());
        for (User user : feedbackClient.getUsers(ids)) {
            String feedback = String.format("**%s** sent:", user.name);
            feedbackClient.sendText(feedback);

            Picture picture = new Picture();
            picture.setMimeType(msg.getMimeType());
            picture.setSize((int) msg.getSize());
            picture.setWidth(msg.getWidth());
            picture.setHeight(msg.getHeight());
            picture.setAssetKey(msg.getAssetKey());
            picture.setAssetToken(msg.getAssetToken());
            picture.setOtrKey(msg.getOtrKey());
            picture.setSha256(msg.getSha256());

            feedbackClient.sendPicture(picture);
        }
    }

    public void sendOnMemberFeedback(String format, ArrayList<String> userIds) {
        if (!hasAdminConv()) return;

        try {
            WireClient feedbackClient = repo.getWireClient(config.getAdmin());
            for (User user : feedbackClient.getUsers(userIds)) {
                String feedback = String.format(format, user.name);
                feedbackClient.sendText(feedback);
            }
        } catch (Exception e) {
            Logger.error(e.getLocalizedMessage());
        }
    }

    public void newUserFeedback(String name) throws Exception {
        if (!hasAdminConv()) return;

        WireClient feedbackClient = repo.getWireClient(config.getAdmin());
        String feedback = String.format("**%s** just joined", name);
        feedbackClient.sendText(feedback);
    }

    private void broadcastPicture(final Picture picture) {
        saveBroadcast(null, null, picture);

        File[] botDirs = getCryptoDirs();
        for (File botDir : botDirs) {
            final String botId = botDir.getName();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    sendPicture(picture, botId);
                }
            });
        }
    }

    private void saveBroadcast(String url, String title, Picture picture) {
        try {
            // save to db
            Broadcast broadcast = new Broadcast();
            broadcast.setAssetData(picture.getImageData());
            broadcast.setAssetKey(picture.getAssetKey());
            broadcast.setToken(picture.getAssetToken());
            broadcast.setOtrKey(picture.getOtrKey());
            broadcast.setSha256(picture.getSha256());
            broadcast.setSize(picture.getSize());
            broadcast.setMimeType(picture.getMimeType());
            broadcast.setUrl(url);
            broadcast.setTitle(title);
            broadcast.setMessageId(picture.getMessageId());

            dbManager.insertBroadcast(broadcast);
        } catch (Exception e) {
            Logger.error(e.getLocalizedMessage());
        }
    }

    private void sendLink(String url, String title, Picture img, String botId) {
        try {
            WireClient client = repo.getWireClient(botId);
            client.sendLinkPreview(url, title, img);
        } catch (Exception e) {
            String msg = String.format("Bot: %s. Error: %s", botId, e.getMessage());
            Logger.error(msg);
        }
    }

    private void sendText(String messageId, String text, String botId) {
        try {
            WireClient client = repo.getWireClient(botId);
            client.sendText(text, 0, messageId);
        } catch (Exception e) {
            String msg = String.format("Bot: %s. Error: %s", botId, e.getMessage());
            Logger.error(msg);
        }
    }

    private void sendPicture(Picture picture, String botId) {
        try {
            WireClient client = repo.getWireClient(botId);
            client.sendPicture(picture);
        } catch (Exception e) {
            String msg = String.format("Bot: %s. Error: %s", botId, e.getMessage());
            Logger.error(msg);
        }
    }

    private Picture uploadPreview(WireClient client, String imgUrl) throws Exception {
        Picture preview = new Picture(imgUrl);
        preview.setPublic(true);

        AssetKey assetKey = client.uploadAsset(preview);
        preview.setAssetKey(assetKey.key);
        return preview;
    }

    private String extractPagePreview(String url) throws IOException {
        Connection con = Jsoup.connect(url);
        Document doc = con.get();

        Elements metaOgImage = doc.select("meta[property=og:image]");
        if (metaOgImage != null) {
            return metaOgImage.attr("content");
        }
        return null;
    }

    private String extractPageTitle(String url) throws IOException {
        Connection con = Jsoup.connect(url);
        Document doc = con.get();

        Elements title = doc.select("meta[property=og:title]");
        if (title != null) {
            return title.attr("content");
        }
        return doc.title();
    }

    private void deleteMessage(String messageId, String botId) {
        try {
            WireClient client = repo.getWireClient(botId);
            client.deleteMessage(messageId);
        } catch (Exception e) {
            String msg = String.format("Bot: %s. Error: %s", botId, e.getMessage());
            Logger.error(msg);
        }
    }

    private File[] getCryptoDirs() {
        File dir = new File(config.cryptoDir);
        return dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String botId = file.getName();

                // Don't broadcast to Feedback conv.
                if (config.getAdmin() != null && config.getAdmin().equals(botId))
                    return false;
                return botId.split("-").length == 5 && file.isDirectory();
            }
        });
    }

    private boolean hasAdminConv() {
        return config.getAdmin() != null && !config.getAdmin().isEmpty();
    }

    public DbManager getDbManager() {
        return dbManager;
    }
}
