package com.prismtech.vortex.demos.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.gs.collections.api.map.ConcurrentMutableMap;
import com.gs.collections.api.map.primitive.MutableIntObjectMap;
import com.gs.collections.impl.map.mutable.ConcurrentHashMap;
import com.gs.collections.impl.map.mutable.primitive.IntObjectHashMap;
import com.prismtech.vortex.rx.RxVortex;
import org.omg.dds.core.policy.Durability;
import org.omg.dds.core.policy.History;
import org.omg.dds.core.policy.Reliability;
import org.omg.dds.domain.DomainParticipant;
import org.omg.dds.pub.DataWriter;
import org.omg.dds.pub.DataWriterQos;
import org.omg.dds.pub.Publisher;
import org.omg.dds.pub.PublisherQos;
import org.omg.dds.sub.DataReader;
import org.omg.dds.sub.DataReaderQos;
import org.omg.dds.sub.Subscriber;
import org.omg.dds.sub.SubscriberQos;
import org.omg.dds.topic.Topic;
import org.omg.dds.topic.TopicBuiltinTopicData;
import org.omg.dds.topic.TopicQos;
import org.omg.dds.type.TypeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import rx.Observable;
import vortex.commons.util.VConfig;

import java.util.Objects;

import static vortex.demo.util.VConfig.DefaultEntities.defaultPolicyFactory;

public class EntityManager {
    private static final Logger LOG = LoggerFactory.getLogger(EntityManager.class);
    private final MutableIntObjectMap<DomainParticipant> participants;
    private final ConcurrentMutableMap<String, Topic<?>> topics;
    private final ConcurrentMutableMap<PubSubKey, Subscriber> subscribers;
    private final ConcurrentMutableMap<PubSubKey, Publisher> publishers;
    private final ConcurrentMutableMap<EntityKey, DataReader<?>> readers;
    private final ConcurrentMutableMap<EntityKey, DataWriter<?>> writers;
    private final ConcurrentMutableMap<Integer, TopicDB> topicDB;
    private final ConcurrentMutableMap<Integer, Observable<TopicBuiltinTopicData>> topicDBObersever;

    public EntityManager() {
        participants =  IntObjectHashMap.<DomainParticipant>newMap().asSynchronized();
        topics = ConcurrentHashMap.newMap();
        subscribers = ConcurrentHashMap.newMap();
        publishers = ConcurrentHashMap.newMap();
        readers = ConcurrentHashMap.newMap();
        writers = ConcurrentHashMap.newMap();
        topicDB = ConcurrentHashMap.newMap();
        topicDBObersever = ConcurrentHashMap.newMap();
    }

    public DataReader<?> getDataReader(int domain, String partition, String topic) {
        EntityKey key = new EntityKey(domain, topic, partition);
        DataReader<?> dr;

        if(isBuiltinTopic(topic)) {
            dr = readers.getIfAbsentPut(key, () -> {
                DomainParticipant participant = participant(domain);
                if(participant != null) {
                    return participant.getBuiltinSubscriber().lookupDataReader(topic);
                } else {
                    return null;
                }
            });
        } else {
            dr = readers.getIfAbsentPut(key, () -> {
                Topic<?> topicDescription = getTopic(domain, topic);
                Subscriber subscriber = subscriber(domain, partition);
                DataReaderQos qos = subscriber.getDefaultDataReaderQos();

                qos = subscriber.copyFromTopicQos(qos, topicDescription.getQos());
                return subscriber.createDataReader(topicDescription, qos);
            });
        }

        return dr;
    }

    public DataWriter<?> getDataWriter(int domain, String partition, String topic) {
        EntityKey key = new EntityKey(domain, topic, partition);
        DataWriter<?> dw;


        dw = writers.getIfAbsentPut(key, () -> {
            Topic<?> topicDescription = getTopic(domain, topic);
            Publisher publisher = publisher(domain, partition);
            DataWriterQos qos = publisher.getDefaultDataWriterQos();

            qos = publisher.copyFromTopicQos(qos, topicDescription.getQos());
            return publisher.createDataWriter(topicDescription, qos);
        });


        return dw;
    }

    private DomainParticipant participant(int domain) {
        return participants.getIfAbsentPut(domain, () -> {
                    DomainParticipant participant = VConfig.ENV.getSPI().getParticipantFactory().createParticipant(domain);
                    assertDB(domain, participant);
                    return participant;
                }
        );
    }

    private Subscriber subscriber(int domain, String partition) {
        final PubSubKey key = new PubSubKey(domain, partition);
        return subscribers.getIfAbsentPut(key, () -> {
            DomainParticipant participant = participant(domain);
            SubscriberQos qos = participant.getDefaultSubscriberQos();
            if(partition != null && ! partition.isEmpty()) {
                qos = qos.withPolicy(defaultPolicyFactory().Partition().withName(partition));
            }
            return participant.createSubscriber(qos);
        });
    }

    private Publisher publisher(int domain, String partition) {
        final PubSubKey key = new PubSubKey(domain, partition);
        return publishers.getIfAbsentPut(key, () -> {
            DomainParticipant participant = participant(domain);
            PublisherQos qos = participant.getDefaultPublisherQos();
            if (partition != null && !partition.isEmpty()) {
                qos = qos.withPolicy(defaultPolicyFactory().Partition().withName(partition));
            }
            return participant.createPublisher(qos);
        });
    }

    private Topic<?> getTopic(int domain, String name) {
        return getTopic(domain, name, null, null, null, null);
    }

    public Topic<?> getTopic(int domain, String name, String type, String durability, String reliability, String history) {
        return topics.getIfAbsentPut(name, () -> {
            TypeSupport<?> ts = null;
            if (type == null || type.isEmpty()) {
                String topicDBType = topicDB.get(domain).getType(name);
                if (topicDBType != null) {
                    topicDBType = topicDBType.replace("::", ".");
                    ts = getTypeSupport(topicDBType, topicDBType);
                }
            } else {
                ts = getTypeSupport(type, type);
            }

            if (ts == null) {
                return null;
            }

            DomainParticipant participant = participant(domain);

            TopicQos qos = participant.getDefaultTopicQos();
            Durability durabilityQos = durabilityQos(durability, name);
            if (durabilityQos != null) {
                qos = qos.withPolicy(durabilityQos);
            }
            Reliability reliabilityQos = reliabilityQos(reliability, name);
            if (reliabilityQos != null) {
                qos = qos.withPolicy(reliabilityQos);
            }
            History historyQos = historyQos(history, name);
            if (historyQos != null) {
                qos = qos.withPolicy(historyQos);
            }

            return participant.createTopic(name, ts, qos, null);
        });
    }

    private static TypeSupport<?> getTypeSupport(String topicType, String topicRegType)  {
        Class<?> cls;
        try {
            cls = Class.forName(topicType);
        } catch (ClassNotFoundException e) {
            final String errMsg = "Could not load class " + topicType + " therefore unable to create the topic.";
            LOG.error(errMsg, e);
            throw new RuntimeException(errMsg, e);
        }
        return TypeSupport.newTypeSupport(cls, topicRegType, VConfig.ENV);
    }

    private DataReader<?> builtinReader(String topic) {
        return VConfig.DefaultEntities.defaultDomainParticipant().getBuiltinSubscriber().lookupDataReader(topic);
    }

    private void assertDB(int domain, DomainParticipant participant) {

        Observable<TopicBuiltinTopicData> dcpsTopic = topicDBObersever.getIfAbsentPut(domain, () -> {
            DataReader<TopicBuiltinTopicData> dcpsTopicDR = participant.getBuiltinSubscriber().lookupDataReader("DCPSTopic");
            return RxVortex.fromDataReader(dcpsTopicDR);
        });

        dcpsTopic.subscribe(sample -> topicDB.getIfAbsentPut(domain, new TopicDB()).add(sample));
    }

    private static Durability durabilityQos(String durability, String topic) {
        if("VOLATILE".equalsIgnoreCase(durability)) {
            LOG.info("Volatile qos for entity {}", topic);
            return defaultPolicyFactory().Durability().withVolatile();
        } else if("TRANSIENT".equalsIgnoreCase(durability)) {
            LOG.info("Transient qos for entity {}", topic);
            return defaultPolicyFactory().Durability().withTransient();
        } else  if("PERSISTENT".equalsIgnoreCase(durability)) {
            LOG.info("Persistent qos for entity {}", topic);
            return defaultPolicyFactory().Durability().withPersistent();
        }
        return null;
    }

    private static Reliability reliabilityQos(String reliability, String topic) {
        if("RELIABLE".equalsIgnoreCase(reliability)) {
            LOG.info("Reliable qos for entity {}", topic);
            return defaultPolicyFactory().Reliability().withReliable();
        } else if("BEST_EFFORT".equals(reliability)) {
            LOG.info("Best effort qos for entity {}", topic);
            return defaultPolicyFactory().Reliability().withBestEffort();
        }

        return  null;
    }

    private static History historyQos(String history, String topic) {
        if(history != null && !history.isEmpty()) {
            JsonNode json = Json.parse(history);
            JsonNode k = json.get("k");
            if(k != null && k.isTextual()) {
                String kind = k.asText();
                if("KEEP_ALL".equalsIgnoreCase(kind)) {
                    LOG.info("Keep all qos for entity {}", topic);
                    return defaultPolicyFactory().History().withKeepAll();
                } else if("KEEP_LAST".equalsIgnoreCase(kind)) {
                    JsonNode v = json.get("v");
                    int depth = 1;

                    if(v != null && v.isInt()) {
                        depth = v.asInt();
                    }
                    LOG.info("Keep last {} qos for entity {}", depth, topic);
                    return defaultPolicyFactory().History().withKeepLast(depth);
                }
            }
        }

        return null;
    }

    private static boolean isBuiltinTopic(String topic) {
        return "DCPSTopic".equals(topic) ||
                "DCPSParticipant".equals(topic) ||
                "DCPSPublication".equals(topic) ||
                "DCPSSubscription".equals(topic) ||
                "CMTopic".equals(topic) ||
                "CMParticipant".equals(topic) ||
                "CMDataReader".equals(topic) ||
                "CMDataWriter".equals(topic) ||
                "CMSubscriber".equals(topic) ||
                "CMPublisher".equals(topic);
    }

    private static class PubSubKey {
        private final int domain;
        private final String partition;

        public PubSubKey(int domain, String partition) {
            this.domain = domain;
            this.partition = partition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PubSubKey that = (PubSubKey) o;
            return Objects.equals(domain, that.domain) &&
                    Objects.equals(partition, that.partition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(domain, partition);
        }
    }

    public class EntityKey {
        final int domainId;
        final String topic;
        final String partition;

        public EntityKey(int domainId, String topic, String partition) {
            this.domainId = domainId;
            this.topic = topic;
            this.partition = partition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityKey entityKey = (EntityKey) o;
            return Objects.equals(domainId, entityKey.domainId) &&
                    Objects.equals(topic, entityKey.topic) &&
                    Objects.equals(partition, entityKey.partition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(domainId, topic, partition);
        }
    }
}
