package io.intelliflow.helper;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import io.intelliflow.centralCustomExceptionHandler.CustomException;
import io.intelliflow.centralCustomExceptionHandler.Status;
import io.intelliflow.helper.db.CQLQuery;
import io.intelliflow.repomanager.model.AppupdateInformation;
import io.intelliflow.repomanager.model.EventResponseModel;
import io.intelliflow.repomanager.model.FileInformation;
import io.intelliflow.repomanager.model.TemplateInformation;
import io.intelliflow.services.utils.AppValidation;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;



@ApplicationScoped
public class RepositoryHelper {
    private static MongoClient mongoClient;

    private static MongoDatabase db;

    private static MongoCollection<Document> collection;

    private static String repositoryFolderPath = ConfigProvider.getConfig().getValue("repo.path", String.class);

    private static String username = ConfigProvider.getConfig().getValue("git.username", String.class);

    private static String password = ConfigProvider.getConfig().getValue("git.password", String.class);

    private static String kafkaServer = ConfigProvider.getConfig().getValue("if.kafka.bootstrap", String.class);

    private static String serverUrl = ConfigProvider.getConfig().getValue("if.server.url", String.class);

    // TODO: To be reviewed and removed in future
    private static String baseBranch = ConfigProvider.getConfig().getValue("if.baseapp.branch", String.class);

    private static final Logger logger = Logger.getLogger(RepositoryHelper.class);

    public static EventResponseModel createWorkspace(QuarkusCqlSession session, String workspaceName,
            FileInformation fileInformation) throws IOException, CustomException {
        File repositoryDirectory = new File(repositoryFolderPath + "/" + workspaceName);
        EventResponseModel response = new EventResponseModel();
        if (!repositoryDirectory.exists()) {
            FileUtils.mkdirs(repositoryDirectory);
            PreparedStatement ps;
            BoundStatement bs;
            response.setMessage("Workspace " + workspaceName + " created successfully");
            ps = session.prepare(CQLQuery.saveWorkspaceByName);
            bs = ps.bind(workspaceName, Instant.now(), Instant.now(), fileInformation.getUserId(), "Active"); // replace
                                                                                                              // userid
                                                                                                              // value
            session.execute(bs);

            ps = session.prepare(CQLQuery.getDataByUser);
            bs = ps.bind(fileInformation.getUserId()); // replace with userid
            List<Row> result = session.execute(bs).all();
            if (result.size() > 0) {
                ps = session.prepare(CQLQuery.updateWorkspaceByUser);
                bs = ps.bind(Collections.singleton(workspaceName), fileInformation.getUserId());
                session.execute(bs);
            } else {
                ps = session.prepare(CQLQuery.saveWorkspaceByUser);
                bs = ps.bind(fileInformation.getUserId(), Collections.singleton(workspaceName));
                session.execute(bs);
            }

        } else {
            throw new CustomException("Workspace " + workspaceName + " already exists", Status.CONFLICT);
        }
        return response;
    }

    private static boolean appCount(QuarkusCqlSession session, FileInformation fileInformation) {
        PreparedStatement ps;
        BoundStatement bs;
        double totalAppCount = 0;
        boolean allowCreate = false;
        fileInformation.setWorkspaceName(fileInformation.getWorkspaceName());
        EventResponseModel responseModel = new EventResponseModel();
        ps = session.prepare(CQLQuery.getAppDataInWorkspace);
        bs = ps.bind(fileInformation.getWorkspaceName());
        ResultSet countResultSet = session.execute(bs);
        totalAppCount = countResultSet.getAvailableWithoutFetching();
        int allowedApps = AppValidation.limitAllowed(session,fileInformation, "app");
        System.out.println("totalcount"+ totalAppCount + " allowedApps"+ allowedApps);
        if (totalAppCount <= allowedApps) {
            allowCreate = true;
        }
        return allowCreate;

    }



    public static EventResponseModel createMiniApp(QuarkusCqlSession session, FileInformation fileInformation)
            throws IOException, GitAPIException, CustomException {

        // TODO:Temp workaround

        EventResponseModel response = new EventResponseModel();
        if (appCount(session, fileInformation)) {
            // response = appCount(session, fileInformation, workspaceName);
            String miniAppPath = repositoryFolderPath +
                    fileInformation.getWorkspaceName() + "/" + fileInformation.getMiniApp();
            if (!new File(miniAppPath).exists()) {
                File gitRepositoryDir = new File(miniAppPath);
                FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
                repositoryBuilder.addCeilingDirectory(gitRepositoryDir);
                repositoryBuilder.findGitDir(gitRepositoryDir);

                PreparedStatement ps;
                BoundStatement bs;
                String logoUrl = "false";
                String deviceSupport = null;
                String colorScheme = null;
                try (Git gitClone = Git.cloneRepository().setURI(repositoryFolderPath + "/ifs-base-application")
                        .setBranch(baseBranch)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                        .setDirectory(new File(miniAppPath)).call()) {

                } catch (InvalidRemoteException e) {
                    e.printStackTrace();
                } catch (TransportException e) {
                    e.printStackTrace();
                } catch (GitAPIException e) {
                    System.out.println("Git Error Stack:::::");
                    e.printStackTrace();
                    throw new CustomException("Git Exception Occurred. Please contact Server Team",
                            Status.INTERNAL_SERVER_ERROR);
                }

                FileHelper.removeRecursively(new File(miniAppPath + "/.git"));
                try {
                    deviceSupport = JsonPath.parse(fileInformation.getContent()).read("$.deviceSupport");
                    logoUrl = JsonPath.parse(fileInformation.getContent()).read("$.logoURL");
                    colorScheme = JsonPath.parse(fileInformation.getContent()).read("$.colorScheme");
                } catch (PathNotFoundException | IllegalArgumentException e) {
                    logger.warn(e.getMessage());
                    System.out.println("Properties not specified");
                }
                updateProperties(miniAppPath, fileInformation.getMiniApp(), fileInformation.getAppDisplayName(),
                        fileInformation.getWorkspaceName(), deviceSupport);
                // Create the repository
                try (Git git = Git.init().setDirectory(gitRepositoryDir).call()) {
                    createSubfolder(fileInformation);
                    createWorkflowDescriptor(git, fileInformation);
                    System.out.println("Created mini app at " + git.getRepository().getDirectory());
                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Initial Commit - Base Application").call();
                    ps = session.prepare(CQLQuery.saveAppByName);
                    bs = ps.bind(fileInformation.getAppDisplayName(), fileInformation.getWorkspaceName(),
                            fileInformation.getMiniApp(), Instant.now(), Instant.now(), fileInformation.getUserId(),
                            "Active", logoUrl, colorScheme, deviceSupport, fileInformation.getDescription()); // replace
                                                                                                              // userid
                                                                                                              // value
                    session.execute(bs);

                    ps = session.prepare(CQLQuery.getDataByUser);
                    bs = ps.bind(fileInformation.getUserId()); // replace with userid
                    List<Row> result = session.execute(bs).all();
                    if (result.size() > 0) {
                        ps = session.prepare(CQLQuery.updateAppByUser);
                        bs = ps.bind(Collections.singleton(fileInformation.getMiniApp()), fileInformation.getUserId());
                        session.execute(bs);
                    } else {
                        ps = session.prepare(CQLQuery.saveAppByUser);
                        bs = ps.bind(fileInformation.getUserId(), Collections.singleton(fileInformation.getMiniApp()));
                        session.execute(bs);
                    }

                    response.setMessage("MiniApp " + fileInformation.getAppDisplayName() + " created successfully");
                    response.setData(listMiniApps(fileInformation.getWorkspaceName()));
                    Map<String, String> createdData = new HashMap<>();
                    createdData.put("miniappName", fileInformation.getMiniApp());
                    createdData.put("displayName", fileInformation.getAppDisplayName());
                    response.setCreated(createdData);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CustomException("Oops, Something went wrong!", Status.INTERNAL_SERVER_ERROR);
                }
            } else {
                throw new CustomException("MiniApp " + fileInformation.getMiniApp() + " already exists",
                        Status.CONFLICT);
            }
            return response;
            // return "Failed to create the repository";
        } else {
            throw new CustomException("Maximum number of apps limit reached",
                    Status.INTERNAL_SERVER_ERROR);
        }
    }

    public static Repository openRepository(FileInformation fileInformation) throws IOException, CustomException {

        File repositoryDirectory = new File(repositoryFolderPath + "/" +
                fileInformation.getWorkspaceName() + "/" +
                fileInformation.getMiniApp());

        try (Git git = Git.open(repositoryDirectory)) {
            return git.getRepository();
        }
    }

    public static Git sourceRepository(TemplateInformation templateInformation) throws CustomException, IOException {

        File repositoryDirectory = new File(repositoryFolderPath + "/" +
                templateInformation.getSourceworkspaceName() + "/" +
                templateInformation.getSourceminiApp());

        if (repositoryDirectory.exists()) {
            try (Git git = Git.open(repositoryDirectory)) {
                return git;
            }
        } else {
            throw new CustomException("Application does not exists", Status.CONFLICT);
        }
    }

    public static Git destinationRepository(QuarkusCqlSession session, TemplateInformation templateInformation)
            throws IOException, CustomException, GitAPIException {

        String miniAppname = ((templateInformation.getDestminiApp().trim()).replace(' ', '-')).toLowerCase();
        File repositoryDirectory = new File(repositoryFolderPath + "/" +
                templateInformation.getDestworkspaceName() + "/" +
                miniAppname);

        EventResponseModel createResponse = new EventResponseModel();
        if (!repositoryDirectory.exists()) {
            FileInformation fileInformation = new FileInformation();
            fileInformation.setWorkspaceName(templateInformation.getDestworkspaceName());
            fileInformation.setMiniApp(miniAppname);
            fileInformation.setAppDisplayName(templateInformation.getDestminiApp());
            fileInformation.setDescription(templateInformation.getDescription());
            fileInformation.setUserId(templateInformation.getUserId());
            JSONObject widObject = new JSONObject();
            JSONObject map = new JSONObject();
            try {
                if (Objects.nonNull(templateInformation.getDeviceSupport())) {
                    widObject.put("deviceSupport", templateInformation.getDeviceSupport());
                }
                if (Objects.nonNull(templateInformation.getColorScheme())) {
                    widObject.put("colorScheme", templateInformation.getColorScheme());
                }
                if (Objects.nonNull(templateInformation.getLogoURL())) {
                    widObject.put("logoURL", templateInformation.getLogoURL());
                }

                map.put("mapping", new JSONArray());
                widObject.put("configuration", map);
                fileInformation.setContent(widObject.toString(2));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            createResponse = createMiniApp(session, fileInformation);

        }
        if (Objects.nonNull(createResponse.getData())) {
            try (Git git = Git.open(repositoryDirectory)) {
                return git;
            }
        }
        return null;
    }

    public static String[] listWorkspaces() {
        File repositoryDirectory = new File(repositoryFolderPath);

        return repositoryDirectory.list();
    }

    public static String[] listMiniApps(String workspaceName) {
        File repositoryDirectory = new File(repositoryFolderPath + "/" + workspaceName);
        return repositoryDirectory.list();
    }

    private static void createSubfolder(FileInformation fileInformation) throws IOException {
        String[] folders = { "bpmn", "datamodel", "form", "dmn", "workflow", "page" };

        for (String folder : folders) {
            File newFolder;
            if (folder.equalsIgnoreCase("datamodel")) {
                newFolder = new File(repositoryFolderPath + "/" +
                        fileInformation.getWorkspaceName() + "/" +
                        fileInformation.getMiniApp() + "/src/main/java/io/intelliflow/generated/models");
            } else {
                newFolder = new File(repositoryFolderPath + "/" +
                        fileInformation.getWorkspaceName() + "/" +
                        fileInformation.getMiniApp() + "/src/main/resources/" + folder);
            }
            FileUtils.mkdirs(newFolder);
        }
        // create meta data folders
        for (String folder : folders) {
            File newFolder = new File(repositoryFolderPath + "/" +
                    fileInformation.getWorkspaceName() + "/" +
                    fileInformation.getMiniApp() + "/src/main/resources/meta/" + folder);
            FileUtils.mkdirs(newFolder);
        }
    }

    public static EventResponseModel deleteWorkspace(QuarkusCqlSession session, String workspaceName,
            FileInformation fileInformation) throws CustomException {

        File repositoryDirectory = new File(repositoryFolderPath + "/" + workspaceName);
        EventResponseModel response = new EventResponseModel();
        if (repositoryDirectory.exists()) {
            PreparedStatement ps;
            BoundStatement bs;
            Git.shutdown();
            FileHelper.removeRecursively(repositoryDirectory);

            ps = session.prepare(CQLQuery.updateWorkspaceStatusByName);
            bs = ps.bind("DELETED", fileInformation.getUserId());
            session.execute(bs);

            ps = session.prepare(CQLQuery.removeWorkspaceByUser);
            bs = ps.bind(Collections.singleton(workspaceName), fileInformation.getUserId());
            session.execute(bs);

            response.setMessage("Workspace " + workspaceName + " and all the content have been deleted");
            response.setData(new File(repositoryFolderPath).list());
        } else {
            throw new CustomException("Workspace with name " + workspaceName + " not found", Status.CONFLICT);
        }

        return response;
    }

    public static EventResponseModel deleteMiniApp(QuarkusCqlSession session, FileInformation fileInformation)
            throws IOException, CustomException {

        PreparedStatement ps;
        BoundStatement bs;
        EventResponseModel response = new EventResponseModel();
        String miniAppName = (fileInformation.getMiniApp().replace(' ', '-')).toLowerCase();
        File gitRepositoryDir = new File(repositoryFolderPath +
                fileInformation.getWorkspaceName() + "/" + miniAppName);
        if (gitRepositoryDir.exists()) {
            try (Git git = Git.init().setDirectory(gitRepositoryDir).call()) {
                git.close();
                git.gc().call();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }
            FileHelper.removeRecursively(gitRepositoryDir);

            ps = session.prepare(CQLQuery.removeAppByUser);
            bs = ps.bind(Collections.singleton(miniAppName), fileInformation.getUserId());
            session.execute(bs);

            // ps = session.prepare(CQLQuery.updateAppStatusByName);
            // bs = ps.bind("DELETED", fileInformation.getWorkspaceName(),
            // fileInformation.getMiniApp());
            // session.execute(bs);

            // Workaround deleting the app entry in table
            ps = session.prepare(CQLQuery.deleteAppByName);
            bs = ps.bind(fileInformation.getWorkspaceName(), miniAppName);
            session.execute(bs);

            response.setMessage(
                    "Repository " + fileInformation.getMiniApp() + " and all the content have been deleted");
        } else {
            throw new CustomException("Mini app with name " + fileInformation.getMiniApp() + " not found",
                    Status.CONFLICT);
        }
        return response;
    }

    public static void updateProperties(String miniAppPath, String miniAppName, String appDisplayName,
            String workspaceName, String deviceSupport) {
        try {
            File properties = new File(miniAppPath + "/src/main/resources/application.properties");
            FileReader reader = new FileReader(properties);
            Properties p = new Properties();
            p.load(reader);
            p.setProperty("ifs.app.workspace", workspaceName);
            p.setProperty("ifs.app.miniappname", miniAppName);
            p.setProperty("ifs.app.displayname", appDisplayName);
            p.setProperty("ifs.app.devicesupport", deviceSupport != null ? deviceSupport : "B");
            p.setProperty("quarkus.kubernetes.name", (workspaceName + "-" + miniAppName).toLowerCase());
            p.setProperty("quarkus.container-image.name", (workspaceName + "-" + miniAppName).toLowerCase());
            p.setProperty("kafka.bootstrap.servers", kafkaServer);
            p.setProperty("ifs.server.url", serverUrl);
            p.setProperty("quarkus.container-image.group", workspaceName.toLowerCase());
            FileWriter writer = new FileWriter(properties);
            p.store(writer, miniAppName + " Properties");
            reader.close();
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static EventResponseModel updateApplication(AppupdateInformation appupdateInformation,
            QuarkusCqlSession session) throws IOException, CustomException, GitAPIException {
        // TODO:Need provicency for other variables like deviceSupport
        EventResponseModel response = new EventResponseModel();
        TemplateInformation templateInformation = new TemplateInformation();
        FileInformation fileInformation = new FileInformation();
        String updatedName = (appupdateInformation.getUpdateName().replace(' ', '-')).toLowerCase();

        templateInformation.setSourceworkspaceName(appupdateInformation.getWorkspaceName());
        templateInformation.setDestworkspaceName(appupdateInformation.getWorkspaceName());
        templateInformation.setSourceminiApp(appupdateInformation.getInitialName());
        templateInformation.setDestminiApp(appupdateInformation.getUpdateName());
        templateInformation.setColorScheme(appupdateInformation.getColorScheme());
        templateInformation.setDeviceSupport(appupdateInformation.getDeviceSupport());
        templateInformation.setDescription(appupdateInformation.getDescription());
        templateInformation.setUserId(appupdateInformation.getUserId());
        try (Git source = RepositoryHelper.sourceRepository(templateInformation)) {
            File applicationFile = new File(source.getRepository().getDirectory().getParent() +
                    "/src/main/resources/application.properties");
            FileReader reader = new FileReader(applicationFile);
            Properties p = new Properties();
            p.load(reader);
            String displayName = p.getProperty("ifs.app.displayname");

            if (updatedName.equalsIgnoreCase(appupdateInformation.getInitialName())) {
                // If appname equal check for other properties
                fileInformation.setWorkspaceName(appupdateInformation.getWorkspaceName());
                fileInformation.setMiniApp(appupdateInformation.getInitialName());
                if (Objects.nonNull(appupdateInformation.getDescription())) {
                    PreparedStatement ps;
                    BoundStatement bs;
                    ps = session.prepare(CQLQuery.updateDescriptionByName);
                    bs = ps.bind((appupdateInformation.getDescription()), fileInformation.getWorkspaceName(),
                            fileInformation.getMiniApp());
                    session.execute(bs);
                }

                if (Objects.nonNull(appupdateInformation.getDeviceSupport())) {
                    PreparedStatement ps;
                    BoundStatement bs;
                    ps = session.prepare(CQLQuery.updateDeviceSupportByName);
                    bs = ps.bind((appupdateInformation.getDeviceSupport()), fileInformation.getWorkspaceName(),
                            fileInformation.getMiniApp());
                    session.execute(bs);
                    EventResponseModel updateRes = updatePropertiesofdeviceSupport(appupdateInformation);
                    if (Objects.nonNull(updateRes)) {
                        source.add()
                                .addFilepattern("src/main/resources")
                                .call();
                        source.commit().setMessage("updated").call();
                    }
                    String widFileName = (fileInformation.getWorkspaceName()+"-"+(fileInformation.getMiniApp().replace(' ', '-'))).toLowerCase();
                    File WidFile = new File(source.getRepository().getDirectory().getParent() +
                            "/src/main/resources/workflow/"+ widFileName +".wid");
                    org.json.JSONObject widObject = new org.json.JSONObject(Files.readString(WidFile.toPath()));
                    widObject.remove("appName");
                    widObject.put("appName", appupdateInformation.getUpdateName());
                    widObject.remove("workspaceName");
                    widObject.put("workspaceName", appupdateInformation.getWorkspaceName());
                    widObject.put("deviceSupport",appupdateInformation.getDeviceSupport());
                    FileWriter file = new FileWriter(WidFile);
                    file.write(widObject.toString());
                    file.flush();
                    file.close();

                }
                if (Objects.nonNull(appupdateInformation.getColorScheme())) {
                    PreparedStatement ps;
                    BoundStatement bs;
                    ps = session.prepare(CQLQuery.updateColorSchemeByName);
                    bs = ps.bind((appupdateInformation.getColorScheme()), fileInformation.getWorkspaceName(),
                            fileInformation.getMiniApp());
                    session.execute(bs);
                }

                if (!displayName.equalsIgnoreCase(appupdateInformation.getUpdateName())) {

                    try (Git destinationGit = RepositoryHelper.destinationRepository(session, templateInformation)) {
                        File destinationFile = new File(destinationGit.getRepository().getDirectory().getParent() +
                                "/src/main/resources/application.properties");
                        p.setProperty("ifs.app.displayname", templateInformation.getDestminiApp());
                        FileWriter writer = new FileWriter(destinationFile);
                        p.store(writer, templateInformation.getDestminiApp() + " Properties");
                        reader.close();
                        writer.close();
                        if (Objects.nonNull(templateInformation.getDestminiApp())) {
                            PreparedStatement ps;
                            BoundStatement bs;
                            ps = session.prepare(CQLQuery.updateDisplayByName);
                            bs = ps.bind((templateInformation.getDestminiApp()), fileInformation.getWorkspaceName(),
                                    fileInformation.getMiniApp());
                            session.execute(bs);
                        }
                        source.add()
                                .addFilepattern("src/main/resources")
                                .call();
                        source.commit().setMessage("updated").call();

                    }

                }

                response.setMessage("Properties Updated");
            } else {
                // If appname is different , clone and rename application

                cloneApplication(session, templateInformation);

                // TODO:delete only if clone app succeeds
                try (Git sourceGit = RepositoryHelper.sourceRepository(templateInformation)) {
                    File sourceApp = new File(sourceGit.getRepository().getDirectory().getParent());
                    if (sourceApp.exists()) {
                        FileInformation deleteAppInfo = new FileInformation();
                        deleteAppInfo.setWorkspaceName(templateInformation.getSourceworkspaceName());
                        deleteAppInfo.setMiniApp(templateInformation.getSourceminiApp());
                        deleteAppInfo.setUserId(appupdateInformation.getUserId());
                        deleteMiniApp(session, deleteAppInfo);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CustomException("Error", Status.INTERNAL_SERVER_ERROR);
                }
                response.setMessage("Application Rename Completed");
            }
        }
        return response;
    }

    public static void createWorkflowDescriptor(Git git, FileInformation fileInformation) {
        String resourcePath = git.getRepository().getDirectory().getParent() + "/src/main/resources/workflow/"
                + (fileInformation.getWorkspaceName() + "-" + fileInformation.getMiniApp()).toLowerCase() + ".wid";
        File readmeFile = new File(resourcePath);
        try {
            readmeFile.createNewFile();

            if (fileInformation.getContent() != null) {
                FileWriter fileWriter = new FileWriter(readmeFile);
                fileWriter.write(fileInformation.getContent());
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateWidOnDeletion(FileInformation fileInformation) {
        FileInformation widFileInfo = new FileInformation();
        widFileInfo.setWorkspaceName(fileInformation.getWorkspaceName());
        widFileInfo.setMiniApp(fileInformation.getMiniApp());
        widFileInfo.setFileType("workflow");
        // Flag variable to see if any changes have occurred in the wid
        int statusFlag = -1;
        try (Repository repository = RepositoryHelper.openRepository(fileInformation)) {
            try (Git git = new Git(repository)) {
                File workFlowFile = new File(git.getRepository().getDirectory().getParent()
                        + "/src/main/resources/workflow/"
                        + (fileInformation.getWorkspaceName() + "-" + fileInformation.getMiniApp()).toLowerCase()
                        + ".wid");
                String getMappingExpression = "$.configuration.mapping";
                JSONArray mappingArray = new JSONArray();

                try {
                    net.minidev.json.JSONArray exisitingMapArray = JsonPath
                            .parse(Files.readString(workFlowFile.toPath())).read(getMappingExpression);
                    if (fileInformation.getFileType().equalsIgnoreCase("bpmn")) {
                        for (int i = 0; i < exisitingMapArray.size(); i++) {
                            if (exisitingMapArray.get(i).getClass().equals(LinkedHashMap.class)) {
                                if (((LinkedHashMap) exisitingMapArray.get(i)).get("bpmnname")
                                        .equals(fileInformation.getFileName())) {

                                    statusFlag++;
                                    continue;
                                } else {
                                    mappingArray.put(exisitingMapArray.get(i));
                                }

                            }
                        }

                    } else if (fileInformation.getFileType().equalsIgnoreCase("form")) {
                        for (int i = 0; i < exisitingMapArray.size(); i++) {
                            if (exisitingMapArray.get(i).getClass().equals(LinkedHashMap.class)) {
                                if (((LinkedHashMap) exisitingMapArray.get(i)).get("formname")
                                        .equals(fileInformation.getFileName())) {
                                    statusFlag++;
                                    continue;
                                } else {
                                    mappingArray.put(exisitingMapArray.get(i));
                                }

                            }
                        }

                    }

                    if (statusFlag != -1) {
                        JSONObject widObject = new JSONObject(Files.readString(workFlowFile.toPath()));
                        JSONObject newConfig = new JSONObject();
                        newConfig.put("mapping", mappingArray);
                        widObject.remove("configuration");
                        widObject.put("configuration", newConfig);
                        widFileInfo.setContent(widObject.toString());
                        GITFileHelper myobj = new GITFileHelper();
                        myobj.updateFileInRepository(widFileInfo);
                    }

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                } catch (GitAPIException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException | CustomException e) {
            e.printStackTrace();
        }

    }

    public static EventResponseModel cloneApplication(QuarkusCqlSession session,
            TemplateInformation templateInformation) throws IOException, CustomException, GitAPIException {

        EventResponseModel response = new EventResponseModel();
        AppupdateInformation appupdateInformation = new AppupdateInformation();
        appupdateInformation.setWorkspaceName(templateInformation.getDestworkspaceName());
        appupdateInformation
                .setInitialName(((templateInformation.getDestminiApp().trim()).replace(' ', '-')).toLowerCase());
        appupdateInformation
                .setUpdateName(((templateInformation.getDestminiApp().trim()).replace(' ', '-')).toLowerCase());
        try (Git sourceGit = RepositoryHelper.sourceRepository(templateInformation)) {
            try (Git destinationGit = RepositoryHelper.destinationRepository(session, templateInformation)) {

                File sourceFile;
                File destinationFile;
                File sourceDataFile;
                File destinationDataFile;

                // Updating mini app name after the create app is done
                String destinationMiniAppname = ((templateInformation.getDestminiApp().trim()).replace(' ', '-'))
                        .toLowerCase();

                sourceFile = new File(sourceGit.getRepository().getDirectory().getParent() +
                        "/src/main/resources");
                sourceDataFile = new File(sourceGit.getRepository().getDirectory().getParent() +
                        "/src/main/java/io/intelliflow/generated/models");
                destinationFile = new File(destinationGit.getRepository().getDirectory().getParent() +
                        "/src/main/resources");
                destinationDataFile = new File(destinationGit.getRepository().getDirectory().getParent() +
                        "/src/main/java/io/intelliflow/generated/models");
                File oldWid = new File(sourceGit.getRepository().getDirectory().getParent() +
                        "/src/main/resources/workflow/" + templateInformation.getSourceworkspaceName().toLowerCase()
                        + "-" + templateInformation.getSourceminiApp().toLowerCase() + ".wid");
                File newWid = new File(destinationGit.getRepository().getDirectory().getParent() +
                        "/src/main/resources/workflow/" + templateInformation.getDestworkspaceName().toLowerCase() + "-"
                        + destinationMiniAppname + ".wid");
                File sourceWid = new File(destinationGit.getRepository().getDirectory().getParent() +
                        "/src/main/resources/workflow/" + templateInformation.getSourceworkspaceName().toLowerCase()
                        + "-" + templateInformation.getSourceminiApp().toLowerCase() + ".wid");

                FileInputStream inputStream = new FileInputStream(oldWid);
                FileOutputStream outputStream = new FileOutputStream(newWid);
                int i;
                while (((i = inputStream.read()) != -1)) {
                    outputStream.write(i);
                }
                inputStream.close();
                outputStream.close();

                try {

                    if (newWid.length() > 0) {
                        org.json.JSONObject widObject = new org.json.JSONObject(Files.readString(newWid.toPath()));
                        widObject.remove("appName");
                        widObject.put("appName", destinationMiniAppname);
                        widObject.remove("workspaceName");
                        widObject.put("workspaceName", templateInformation.getDestworkspaceName());
                        FileWriter file = new FileWriter(newWid);
                        file.write(widObject.toString());
                        file.flush();
                        file.close();
                    }

                    org.apache.commons.io.FileUtils.copyDirectory(sourceFile, destinationFile);
                    if (sourceDataFile.exists()) {
                        org.apache.commons.io.FileUtils.copyDirectory(sourceDataFile, destinationDataFile);
                    }

                    File properties = new File(destinationFile + "/application.properties");
                    FileReader reader = new FileReader(properties);
                    Properties p = new Properties();
                    p.load(reader);
                    p.setProperty("ifs.app.workspace", templateInformation.getDestworkspaceName());
                    p.setProperty("ifs.app.miniappname", destinationMiniAppname);
                    p.setProperty("ifs.app.displayname", templateInformation.getDestminiApp());
                    p.setProperty("quarkus.container-image.name",
                            (templateInformation.getDestworkspaceName() + "-" + destinationMiniAppname).toLowerCase());
                    p.setProperty("quarkus.container-image.group",
                            (templateInformation.getDestworkspaceName()).toLowerCase());
                    p.setProperty("quarkus.kubernetes.name",
                            (templateInformation.getDestworkspaceName() + "-" + destinationMiniAppname).toLowerCase());
                    FileWriter writer = new FileWriter(properties);
                    p.store(writer, destinationMiniAppname + " Properties");
                    reader.close();
                    writer.close();
                    appupdateInformation.setDeviceSupport(templateInformation.getDeviceSupport());
                    if (Objects.nonNull(templateInformation.getDescription())) {
                        PreparedStatement ps;
                        BoundStatement bs;
                        ps = session.prepare(CQLQuery.updateDescriptionByName);
                        bs = ps.bind((templateInformation.getDescription()), templateInformation.getDestworkspaceName(),
                                destinationMiniAppname);
                        session.execute(bs);
                    }

                    if (Objects.nonNull(templateInformation.getDeviceSupport())) {
                        PreparedStatement ps;
                        BoundStatement bs;
                        ps = session.prepare(CQLQuery.updateDeviceSupportByName);
                        bs = ps.bind((templateInformation.getDeviceSupport()),
                                templateInformation.getDestworkspaceName(), destinationMiniAppname);
                        session.execute(bs);
                        updatePropertiesofdeviceSupport(appupdateInformation);
                    }

                    if (Objects.nonNull(templateInformation.getColorScheme())) {
                        PreparedStatement ps;
                        BoundStatement bs;
                        ps = session.prepare(CQLQuery.updateColorSchemeByName);
                        bs = ps.bind((templateInformation.getColorScheme()), templateInformation.getDestworkspaceName(),
                                destinationMiniAppname);
                        session.execute(bs);
                    }

                    if (Objects.nonNull(templateInformation.getLogoURL())) {
                        PreparedStatement ps;
                        BoundStatement bs;
                        ps = session.prepare(CQLQuery.updatelogoURLByName);
                        bs = ps.bind((templateInformation.getLogoURL()), templateInformation.getDestworkspaceName(),
                                destinationMiniAppname);
                        session.execute(bs);
                    }

                    if (sourceWid.exists()) {
                        sourceWid.delete();
                    }

                    destinationGit.add()
                            .addFilepattern("src/main/")
                            .call();
                    destinationGit.commit().setMessage("updated").call();

                } catch (IOException e) {
                    e.printStackTrace();
                    response.setMessage(String.valueOf(e));
                }
            }
            return response;
        }
    }

    public static EventResponseModel updatePropertiesofdeviceSupport(AppupdateInformation appupdateInformation) {

        EventResponseModel response = new EventResponseModel();
        try {
            File properties = new File(repositoryFolderPath + "/" + appupdateInformation.getWorkspaceName() + "/"
                    + appupdateInformation.getInitialName() + "/src/main/resources/application.properties");
            FileReader reader = new FileReader(properties);
            Properties p = new Properties();
            p.load(reader);
            p.setProperty("ifs.app.devicesupport", appupdateInformation.getDeviceSupport());
            p.store(new FileWriter(properties), appupdateInformation.getInitialName() + " Properties");
            response.setMessage("Success");
        } catch (IOException e) {
            e.printStackTrace();
            return null;

        }
        return response;
    }
}
