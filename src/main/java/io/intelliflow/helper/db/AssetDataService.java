package io.intelliflow.helper.db;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.paging.OffsetPager;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.intelliflow.repomanager.model.EventResponseModel;
import io.intelliflow.repomanager.model.FileInformation;
import io.intelliflow.repomanager.model.db.ApplicationDetail;
import io.intelliflow.repomanager.model.db.DataByUser;
import io.intelliflow.repomanager.model.db.FileDetail;
import net.minidev.json.JSONObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class AssetDataService {

    @Inject
    QuarkusCqlSession session;

    PreparedStatement ps;
    BoundStatement bs;

    public DataByUser getDataByUser(String userId){
        ps = session.prepare(CQLQuery.getDataByUser);
        bs = ps.bind(userId);
        DataByUser dataByUser = new DataByUser();
        List<Row> result = session.execute(bs).all();
        if(result.size() > 0){
            dataByUser.setUserId(userId);
            dataByUser.setWorkspaces(result.get(0).getSet("workspaces", String.class));
            dataByUser.setApps(result.get(0).getSet("apps", String.class));
            dataByUser.setFiles(result.get(0).getSet("files", String.class));
        }
        return dataByUser;
    }

    public EventResponseModel appDataInWorkspace(String workspaceName, String appName, String status, int pageNumber, int pageSize) {

        List<ApplicationDetail> applicationDetails = new ArrayList<>();
        EventResponseModel responseModel = new EventResponseModel();
        responseModel.setMessage("App information within " + workspaceName);
        int publishedAppCount = 0;
        int inDevAppCount = 0;

        ps = session.prepare(CQLQuery.getAppDataInWorkspace);
        bs = ps.bind(workspaceName);
        OffsetPager pager = new OffsetPager(pageSize);
        System.out.println("Apps for " + workspaceName + " loaded");
        double totalAppCount = 0;
        double totalPages = 0;
        double totalAppCountAppName = 0;
        double totalPagesAppName = 0;
        if (pageNumber == 1 && appName == null) {
            ResultSet countResultSet = session.execute(bs);
            totalAppCount = countResultSet.getAvailableWithoutFetching();
            totalPages = Math.ceil(totalAppCount / pageSize);
        }
        ResultSet resultSet = session.execute(bs);
        List<com.datastax.oss.driver.api.core.cql.Row> result;
        if(appName == null) {
            OffsetPager.Page<com.datastax.oss.driver.api.core.cql.Row> page = pager.getPage(resultSet, pageNumber);
            result = page.getElements();
        }else {
            result = resultSet.all();
        }
        for(Row appData : result) {
            boolean appNameCheck;
            String dataAppName = appData.getString("appname");
            if(appName != null) {
                Pattern regexPattern = Pattern.compile(appName);
                Matcher matcher = regexPattern.matcher(dataAppName);
                appNameCheck = matcher.find();
            }else {
                appNameCheck = true;
            }
            if(Objects.nonNull(status)){
                if (appData.getString("status").equalsIgnoreCase(status) && appNameCheck) {
                    applicationDetails.add(createAppDetail(appData));
                    totalAppCountAppName++;
                }
            } else {
                if (!appData.getString("status").equalsIgnoreCase("DELETED") && appNameCheck) {
                    applicationDetails.add(createAppDetail(appData));
                    totalAppCountAppName++;
                }
            }
            if(Objects.nonNull(appData.getString("status"))) {
                if(appData.getString("status").equalsIgnoreCase("Active")) {
                    inDevAppCount ++;
                } else if (appData.getString("status").equalsIgnoreCase("FINISHED")) {
                    publishedAppCount ++;
                }
            }
        }
        if(appName != null){
            totalPagesAppName = Math.ceil(totalAppCountAppName / pageSize);
        }
        JSONObject countObj = new JSONObject();
        countObj.put("published", publishedAppCount);
        countObj.put("development", inDevAppCount);

        if(pageNumber == 1 && appName == null) {
            countObj.put("totalApps", totalAppCount);
            countObj.put("totalPages", totalPages);
        }else {
            countObj.put("totalApps", totalAppCountAppName);
            countObj.put("totalPages", totalPagesAppName);
        }

        JSONObject dataObj =new JSONObject();
        dataObj.put("count", countObj);
        dataObj.put("apps", applicationDetails);

        responseModel.setData(dataObj);
        return responseModel;
    }

    public List<FileDetail> fileDataInApp(String workspaceName, String appName, String status) {
        ps = session.prepare(CQLQuery.getAppData);
        bs = ps.bind(workspaceName, appName);
        List<Row> appData = session.execute(bs).all();
        List<FileDetail> fileDetails = new ArrayList<>();
        if(appData.size() > 0) {
            Set<String> filesInApp = appData.get(0).getSet("files", String.class);
            for(String fileName : filesInApp) {
                ps = session.prepare(CQLQuery.getFileByApp);
                bs = ps.bind(workspaceName, appName, fileName);
                List<Row> fileData = session.execute(bs).all();
                if(Objects.nonNull(status)) {
                    if(fileData.get(0).getString("status").equalsIgnoreCase(status)) {
                        fileDetails.add(createFileDetail(fileData.get(0)));
                    }
                } else {
                    fileDetails.add(createFileDetail(fileData.get(0)));
                }
            }
        }
        return fileDetails;
    }

    static FileDetail createFileDetail(Row file) {
        FileDetail fileDetail = new FileDetail();
        fileDetail.setWorkspaceName(file.getString("workspacename"));
        fileDetail.setAppName(file.getString("appname"));
        fileDetail.setStatus(file.getString("status"));
        fileDetail.setUserId(file.getString("userid"));
        fileDetail.setCreationTime(file.getInstant("creationtime"));
        fileDetail.setFileName(file.getString("filename"));
        fileDetail.setLastUpdatedTime(file.getInstant("lastupdatedtime"));
        return fileDetail;
    }

    static ApplicationDetail createAppDetail(Row appData){
        ApplicationDetail appDetail = new ApplicationDetail();
        appDetail.setWorkspaceName(appData.getString("workspacename"));
        appDetail.setAppName(appData.getString("appname"));
        appDetail.setStatus(appData.getString("status"));
        appDetail.setUserId(appData.getString("userid"));
        appDetail.setCreationTime(appData.getInstant("creationtime"));
        appDetail.setLogoUrl(appData.getString("logourl"));
        appDetail.setDeviceSupport(appData.getString("devicesupport"));
        appDetail.setColorScheme(appData.getString("colorscheme"));
        appDetail.setLastUpdatedTime(appData.getInstant("lastupdatedtime"));
        appDetail.setAppDisplayName(appData.getString("appdisplayname"));
        appDetail.setDescription(appData.getString("description"));
        return appDetail;
    }

    public void updateTimeForData(FileInformation fileInformation) {
        Instant currentTime = Instant.now();
        if(Objects.nonNull(fileInformation.getWorkspaceName())) {
            ps = session.prepare(CQLQuery.updateTimeByWorkspace);
            bs = ps.bind(currentTime, fileInformation.getWorkspaceName());
            session.execute(bs);
            if(Objects.nonNull(fileInformation.getMiniApp())) {
                ps = session.prepare(CQLQuery.updateTimeByApp);
                bs = ps.bind(currentTime, fileInformation.getWorkspaceName(), fileInformation.getMiniApp());
                session.execute(bs);
                if(Objects.nonNull(fileInformation.getFileName())) {
                    ps = session.prepare(CQLQuery.updateTimeByFile);
                    bs = ps.bind(currentTime, fileInformation.getWorkspaceName(), fileInformation.getMiniApp(), fileInformation.getFileName());
                    session.execute(bs);
                }
            }
        }
    }
}
