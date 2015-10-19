package com.prismtech.vortex.demos.rest;

import com.prismtech.vortex.rx.RxVortex;
import org.omg.dds.sub.DataReader;
import org.omg.dds.topic.Topic;
import rx.Observable;
import vortex.JSONTopicType;

import static vortex.commons.util.VConfig.DefaultEntities.*;

public class RxApp {
    public static void main(String[] args) {
        Topic<JSONTopicType> myTopic = defaultDomainParticipant().createTopic("MyTopic", JSONTopicType.class);
        Topic<JSONTopicType> fooTopic = defaultDomainParticipant().createTopic("FooTopic", JSONTopicType.class);

        DataReader<JSONTopicType> dr = defaultSub().createDataReader(myTopic);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                defaultDomainParticipant().close();
            }
        });

        Observable<JSONTopicType> observable = RxVortex.fromDataReader(dr);
        observable.subscribe(s -> System.out.println(s.value));

    }
}
