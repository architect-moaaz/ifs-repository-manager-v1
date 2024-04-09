package io.intelliflow.helper.db;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.intelliflow.repomanager.model.AppTemplate;
import io.intelliflow.repomanager.model.EventResponseModel;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TemplateService {

    @Inject
    QuarkusCqlSession session;
    PreparedStatement ps;
    BoundStatement bs;
}
