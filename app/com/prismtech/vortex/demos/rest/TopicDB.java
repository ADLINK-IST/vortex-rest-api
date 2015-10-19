package com.prismtech.vortex.demos.rest;

import com.gs.collections.api.map.ConcurrentMutableMap;
import com.gs.collections.impl.map.mutable.ConcurrentHashMap;
import com.prismtech.cafe.ddsi.CMTopicData;
import org.omg.dds.topic.TopicBuiltinTopicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicDB {
    private static final Logger LOG = LoggerFactory.getLogger(TopicDB.class);

    private final ConcurrentMutableMap<String, TopicBuiltinTopicData> db;

    public TopicDB() {
        db = ConcurrentHashMap.newMap();
    }

    public void add(TopicBuiltinTopicData topic) {
        LOG.info("Adding {}", topic.getName());
        db.put(topic.getName(), topic);
    }

    public void add(CMTopicData topic) {

    }

    public void update(TopicBuiltinTopicData topic) {
        LOG.info("Updating {}", topic.getName());
        db.replace(topic.getName(), topic);
    }

    public void update(CMTopicData topic) {

    }

    public void delete(TopicBuiltinTopicData topic) {
        LOG.info("Deleting {}", topic.getName());
        db.remove(topic.getName());
    }

    public String getType(String topicName) {
        TopicBuiltinTopicData data = db.get(topicName);
        if(data != null) {
            return data.getTypeName();
        }

        return "";
    }
}
