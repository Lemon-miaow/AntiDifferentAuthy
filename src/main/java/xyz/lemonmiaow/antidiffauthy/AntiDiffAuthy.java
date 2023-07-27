package xyz.lemonmiaow.antidiffauthy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class AntiDiffAuthy extends Plugin implements Listener {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private ProxiedPlayer player;
    private ConfigData configData;
    private boolean saveInfo = false;

    @Override
    public void onEnable() {
        // 使用Gson解析config.json
        ConfigData configData = loadConfig();
        if (configData == null) {
            System.err.println("[AntiDiffAuthy] Failed to load config file. Using default values.");
            configData = new ConfigData("mongodb://localhost:27017", "minecraft", "player_info", "Mojang");
        }

        // 连接到MongoDB数据库
        String uri = configData.getUri();
        String databaseName = configData.getDatabaseName();
        String collectionName = configData.getCollectionName();

        ConnectionString connectionString = new ConnectionString(uri);
        this.mongoClient = MongoClients.create(connectionString);
        this.database = mongoClient.getDatabase(databaseName);
        this.collection = database.getCollection(collectionName);


        getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {
        // 关闭MongoDB连接
        mongoClient.close();
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        this.player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String loginMode = configData.getLoginMode();
        System.out.println(loginMode);
        String playerUUID = player.getUniqueId().toString();
        String playerUsername = player.getName();

        Document existingDocumentU = collection.find(new Document("uuid", new Document("$exists", true))).first();
        Document existingDocumentN = collection.find(new Document("username", new Document("$exists", true))).first();
        Document existingDocumentI = collection.find(new Document("login_mode", new Document("$exists", true))).first();

        String savedUUID = (existingDocumentU != null) ? existingDocumentU.getString("uuid") : null;
        String savedUsername = (existingDocumentN != null) ? existingDocumentN.getString("username") : null;
        String savedLoginMode = (existingDocumentI != null) ? existingDocumentI.getString("login_mode") : null;

        if (savedUUID == null && savedUsername == null) {
            // 数据库中没有该用户名且用户名与当前玩家的用户名都不存在，将玩家信息保存到数据库
            savePlayerInfo(uuid, loginMode, player.getAddress().getAddress().getHostAddress(), playerUsername);
            saveInfo = true;
        } else if (savedUUID.equals(playerUUID) &&
                !savedUsername.equals(playerUsername) &&
                savedLoginMode.equals(loginMode)) {
            // 如果数据库中没有该用户名但有相同UUID，且登录方式相同，则将玩家信息保存到数据库
            savePlayerInfo(uuid, loginMode, player.getAddress().getAddress().getHostAddress(), playerUsername);
            saveInfo = true;
        } else if (savedUsername.equals(playerUsername) &&
                savedUUID.equals(playerUUID) &&
                savedLoginMode.equals(loginMode)) {
            // 如果数据库中有该用户名且有相同UUID，且登录方式相同，则不进行任何操作
        } else {
            // 数据库中进行登录方式的比较检测，不符合则踢出
            if (!loginMode.equals(savedLoginMode)) {
                player.disconnect("您的登录方式不正确！请使用第一次所用的登录方式登录！");
            } else {
                savePlayerInfo(uuid, loginMode, player.getAddress().getAddress().getHostAddress(), playerUsername);
                saveInfo = true;
            }
        }
        //还有一种抽象的情况，就是数据库中有该用户名但没有相同UUID，Copilot说这种情况不会发生，因为UUID是唯一的
        //else if (savedUsername.equals(playerUsername) &&
        //                !savedUUID.equals(playerUUID) &&
        //                savedLoginMode.equals(loginMode)) {
        //            // 如果数据库中有该用户名但没有相同UUID，且登录方式相同，则将玩家信息保存到数据库
        //            savePlayerInfo(uuid, loginMode, player.getAddress().getAddress().getHostAddress(), player.getName());
        //            saveInfo = true;
        //        }

    }

    // 使用Gson解析config.json的子类
    private ConfigData loadConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.json");

            if (configFile.exists()) {
                FileReader reader = new FileReader(configFile);
                Gson gson = new Gson();
                ConfigData configData = gson.fromJson(reader, ConfigData.class);
                reader.close();
                return configData;
            } else {
                saveDefaultConfig();
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveDefaultConfig() {
        // 如果config.json不存在，则保存默认配置
        File configFile = new File(getDataFolder(), "config.json");
        File configDir = configFile.getParentFile();

        try {
            if (!configDir.exists()) {
                boolean created = configDir.mkdirs();
                if (!created) {
                    System.err.println("[AntiDiffAuthy] Failed to create config directory.");
                    return;
                }
            }

            FileWriter writer = new FileWriter(configFile);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            ConfigData defaultConfig = new ConfigData("mongodb://localhost:27017", "minecraft", "player_info", "Mojang");
            gson.toJson(defaultConfig, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setConfigData(ConfigData configData) {
        this.configData = configData;
    }

    private static class ConfigData {
        private String uri;
        private String databaseName;
        private String collectionName;
        private String loginMode;

        public ConfigData(String uri, String databaseName, String collectionName, String loginMode) {
            this.uri = uri;
            this.databaseName = databaseName;
            this.collectionName = collectionName;
            this.loginMode = loginMode;
        }

        public String getUri() {
            return uri;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public String getLoginMode() {
            return loginMode;
        }
    }

    // 根据玩家的UUID获取登录方式和其他信息
    private void savePlayerInfo(UUID uuid, String loginMode, String ipAddress, String username) {
        if (saveInfo) {
            // 如果saveInfo == true，则将玩家信息保存到数据库
            Document document = new Document()
                    .append("uuid", uuid.toString())
                    .append("login_mode", loginMode)
                    .append("ip_address", ipAddress)
                    .append("username", username);

            collection.replaceOne(new Document("uuid", uuid.toString()), document, new ReplaceOptions().upsert(true));

            System.out.println("[AntiDiffAuthy] Data replaced in the database.");
            saveInfo = false;
        }
    }
}
