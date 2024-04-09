package io.intelliflow.helper;

 /*
    @author rahul.malawadkar@intelliflow.ai
    @created on 06-07-2023
 */

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.intelliflow.helper.db.CQLQuery;
import io.intelliflow.repomanager.model.EventResponseModel;
import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ApplicationScoped
public class PlatformHelper {

    @Inject
    QuarkusCqlSession session;

    PreparedStatement ps;
    BoundStatement bs;

    public EventResponseModel getBuildHistory(String workSpace, String app,String timeZone,int pageSize,int pageNumber) {
        app = app.toLowerCase().replace(" ","-");
        ObjectMapper objectMapper = new ObjectMapper();
        EventResponseModel eventResponseModel = new EventResponseModel();
        ps = session.prepare(CQLQuery.getAppData);
        bs = ps.bind(workSpace, app);
        List<Row> resultDeployments = session.execute(bs).all();
        Object allDeployments = null;
        for (Row appData : resultDeployments) {
            if (!appData.isNull("alldeployment")) {
                allDeployments = appData.getObject("alldeployment");
            }
            Log.info("Deployment id's for app : " + app + " : " + allDeployments);
        }
        if (allDeployments != null) {
            Map<String, Object> response = new HashMap<>();
            List<String> listOfDeployments = objectMapper.convertValue(allDeployments,new TypeReference<>() {
            });
            double totalCount = listOfDeployments.size();
            double totalPages = Math.ceil(totalCount / pageSize);
            listOfDeployments.sort(Collections.reverseOrder());
            List<Map<String, String>> list = new ArrayList<>();
            int start = (pageNumber - 1) * pageSize;
            int end = pageNumber * pageSize;
            end = Math.min(end,listOfDeployments.size());
            for(int i = start ; i < end ; i++ ) {
                String buildNo = listOfDeployments.get(i);
                ps = session.prepare(CQLQuery.GET_ALL_DEPLOYMENTS_BY_BUILD_NO);
                bs = ps.bind(buildNo);
                ResultSet resultSet = session.execute(bs);
                int count = 0;
                String actionedBy;
                Map<String, String> map = new HashMap<>();
                String queuedStatus = null;
                for (Row deployment : resultSet) {
                    ZoneId zoneId = timeZone == null ? ZoneId.systemDefault() : ZoneId.of(timeZone);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                            .withZone(zoneId);
                    Instant actionTimeInstant = deployment.getInstant("action_time");
                    String formattedDate = formatter.format(actionTimeInstant);
                    String outCome = deployment.getString("outcome");
                    if(outCome.equalsIgnoreCase("QUEUED")){
                        queuedStatus = outCome;
                        actionedBy = deployment.getString("actioned_by");
                        map.put("deployedBy",actionedBy);
                        map.put("startTime", formattedDate);
                    }
                    if(outCome.equalsIgnoreCase("FINISHED")){
                        map.put("endTime", formattedDate);
                    }
                    count++;
                }
                list.add(map);
                if(count == 1){
                    map.put("Status",queuedStatus == null ? "FAILED" : queuedStatus.toUpperCase());
                }else if(count == 2){
                    map.put("status","RUNNING");
                }else {
                  map.put("status","COMPLETED");
                }
            }
            if(list.isEmpty()){
                eventResponseModel.setMessage("There is no more build history for the app: "+app);
            }else {
                response.put("totalCount",totalCount);
                response.put("totalPages",totalPages);
                response.put("records",list);
                eventResponseModel.setData(response);
                eventResponseModel.setMessage("The build history for app: " + app);
            }
            return eventResponseModel;
        }
        eventResponseModel.setMessage("There is no build history for the app: "+app);
        return eventResponseModel;
    }
}
