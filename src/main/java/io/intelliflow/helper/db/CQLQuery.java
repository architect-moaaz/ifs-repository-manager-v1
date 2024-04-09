package io.intelliflow.helper.db;

public class CQLQuery {

    public static final String saveWorkspaceByName = "INSERT INTO workspace_by_name (workspacename, creationtime, lastupdatedtime, userid, status) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS";

    public static final String saveAppByName = "INSERT INTO apps_by_name (appdisplayname, workspacename, appname, creationtime, lastupdatedtime, userid, status, logourl, colorscheme, devicesupport, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";

    public static final String saveFileByName = "INSERT INTO files_by_app (workspacename, appname, creationtime, lastupdatedtime, filename, userid, status) VALUES (?, ?, ?, ?, ?, ?, ?) IF NOT EXISTS";

    public static final String saveWorkspaceByUser = "INSERT INTO data_by_user (userid, workspaces) VALUES (?, ?)";

    public static final String saveAppByUser = "INSERT INTO data_by_user (userid, apps) VALUES (?, ?)";

    public static final String saveFileByUser = "INSERT INTO data_by_user (userid, files) VALUES (?, ?)";


    //UPDATE QUERIES


    public static final String updateWorkspaceByUser = "UPDATE data_by_user SET workspaces = workspaces + ? WHERE userid=?";

    public static final String updateAppByUser = "UPDATE data_by_user SET apps = apps + ? WHERE userid=?";

    public static final String updateFileByUser = "UPDATE data_by_user SET files = files + ? WHERE userid=?";


    public static final String updateFilesInApp = "UPDATE apps_by_name SET files = files + ? WHERE workspacename=? AND appname=?";

    public static final String updateWorkspaceStatusByName = "UPDATE workspace_by_name SET status = ? WHERE workspacename=?";

    public static final String updateFileStatusByName = "UPDATE files_by_app SET status = ? WHERE workspacename=? AND appname=? AND filename=?";

    public static final String updateAppStatusByName = "UPDATE apps_by_name SET status = ? WHERE workspacename=? AND appname=?";

    public static final String updateTimeByWorkspace = "UPDATE workspace_by_name SET lastupdatedtime = ? where workspacename = ?";

    public static final String updateTimeByApp = "UPDATE apps_by_name SET lastupdatedtime = ? where workspacename = ? AND appname=?";

    public static final String updateTimeByFile = "UPDATE files_by_app SET lastupdatedtime = ? where workspacename = ? AND appname=? AND filename=?";

    public static final String updateDescriptionByName = "UPDATE apps_by_name SET description = ? WHERE workspacename=? AND appname=?";

    public static final String updateColorSchemeByName = "UPDATE apps_by_name SET colorscheme = ? WHERE workspacename=? AND appname=?";

    public static final String updateDeviceSupportByName = "UPDATE apps_by_name SET devicesupport = ? WHERE workspacename=? AND appname=?";
    
    public static final String updatelogoURLByName = "UPDATE apps_by_name SET logourl = ? WHERE workspacename=? AND appname=?";

    public static final String updateDisplayByName = "UPDATE apps_by_name SET appdisplayname = ? WHERE workspacename=? AND appname=?";
    
    public static final String removeFilesInApp = "UPDATE apps_by_name SET files = files - ? WHERE workspacename=? AND appname=?";

    public static final String removeFileByUser = "UPDATE data_by_user SET files = files - ? WHERE userid=?";

    public static final String removeAppByUser = "UPDATE data_by_user SET apps = apps - ? WHERE userid=?";

    public static final String removeWorkspaceByUser = "UPDATE data_by_user SET workspaces = workspaces - ? WHERE userid=?";

    //SELECT QUERIES

    public static final String getDataByUser = "SELECT * FROM data_by_user WHERE userid=?";

    public static final String getAppData = "SELECT * FROM apps_by_name WHERE workspacename=? AND appname=?";

    public static final String GET_ALL_DEPLOYMENTS_BY_BUILD_NO = "SELECT * FROM deployment WHERE build_no=?";

    public static final String getFileByApp = "SELECT * from files_by_app WHERE workspacename=? AND appname=? AND filename=?";

    public static final String getAppDataInWorkspace = "SELECT * FROM apps_by_name WHERE workspacename=?";


    //DELETE QUERIES
    public static final String deleteFileByName = "DELETE FROM files_by_app WHERE workspacename = ? AND appname=? AND filename=? IF EXISTS";

    public static final String deleteAppByName = "DELETE FROM apps_by_name WHERE workspacename = ? AND appname=?";

}
