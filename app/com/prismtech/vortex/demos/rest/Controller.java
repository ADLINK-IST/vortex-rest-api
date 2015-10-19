package com.prismtech.vortex.demos.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.Gson;
import org.omg.dds.core.Duration;
import org.omg.dds.pub.DataWriter;
import org.omg.dds.sub.DataReader;
import org.omg.dds.sub.Sample;
import org.omg.dds.topic.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import play.mvc.Result;
import vortex.commons.util.VConfig;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class Controller extends play.mvc.Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Controller.class);
    private static final EntityManager entities = new EntityManager();

    public static Result readFromTopic(String topic, String partition) {
        DataReader<?> dr = entities.getDataReader(0, partition, topic);
        if(dr != null) {
            try {
                dr.waitForHistoricalData(Duration.infiniteDuration(VConfig.ENV));
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
            Gson gson = new Gson();
            Sample.Iterator<?> iterator = dr.read();
            ArrayNode json = Json.newArray();
            iterator.forEachRemaining(sample -> {
                LOG.debug("Sample: {}", sample.toString());
                if(sample.getData() != null) {
                    String s = gson.toJson(sample.getData());
                    json.add(Json.parse(s));
                }
            });
            return ok(json);
        } else {
            return ok("No data reader for that topic!");
        }
    }

    public static Result writeToTopic(String topic, String partition) {
        DataWriter<?> dw = entities.getDataWriter(0, partition, topic);
        if(dw != null) {
            writeData(dw, request().body().asJson());
            return ok();
        } else {
            return notFound();
        }
    }

    public static Result topic(String topic, String type, String durability, String reliability, String history) {
        Topic<?> topicDesc = entities.getTopic(0, topic, type, durability, reliability, history);

        if(topicDesc != null) {
            return ok();
        } else {
            return notFound();
        }
    }

    private static String sessionId() {
        return session().computeIfAbsent("vsession", k -> UUID.randomUUID().toString());
    }

    private static void writeData(DataWriter<?> dw, JsonNode json) {
        Class<?> cls = dw.getTopic().getTypeSupport().getType();
        Object fromJson = Json.fromJson(json, cls);
        try {
            ((DataWriter<Object>)dw).write(fromJson);
            LOG.info("Data written to Vortex.");
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }
}
