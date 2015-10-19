# vortex-rest-api
A RESTful API for sharing data with PrismTech's Vortex IIoT data-sharing platform.

Vortex Web does an excellent job of pushing data to and receiving data from a web client over web sockets. But similar to how we do not always need to receive data in real-time not all web applications need to receive data in real-time. Often it is sufficient for a web application to pull the latest data when it needs it and for this there is no better data-centric approach for the web than ReST. Wikipedia describes ReST APIs very succinctly.

Wikipedia definition of ReST
> RESTful systems typically, but not always, communicate over the Hypertext Transfer Protocol with the same HTTP verbs (GET, POST, PUT, DELETE, etc.) used by web browsers to retrieve web pages and send data to remote servers. REST interfaces usually involve collections of resources with identifiers, for example /people/paul, which can be operated upon using standard verbs, such as DELETE /people/paul.

As you will see here a ReST API for Vortex provides a very simple and easily consumed API for Vortex. Is useful for those use cases where it is not necessary for data to be pushed to the client in real-time. One of the benefits of this is a user can look at Vortex data with a simple Web Browser; it really can't much easier than that. It defines a ReSTful API that is only 3 different routes. In the original post to read and/or write a Topic, a user would have to create a reader/writer then use an id for that reader/writer to read/write. In this post the topic is the resource and the HTTP method (GET or PUT) determines whether reading or writing. The third route makes it possible to define a new topic.

The assumption in this approach is that when a Topic was created a suggested QoS was provided for readers and writers for the Topic. The server uses this QoS when creating a reader or writer for that Topic. How is this achieved? The server reads the DCPSTopic and keeps a database of Topics in the system. The database is used to determine both the topic type and the suggested QoS so that the server can automatically create the Topic locally and then readers and/or writers of the Topic. The one dependency is that the server is able to find the Topic's type in its class loader.
The server is built with two topic types that can be used to create topics:
  * ***vortex::JSONTopicType*** - used to pass around JSON Objects as strings; and
  * ***vortex::KeyValueTopicType*** - used to create key value pairs where the key field is also a key in DDS.

## Example of Reading
```
curl -X GET http://localhost:9000/vortex/MyTopic
```

## Example of Writing a Topic
```
curl -H 'Content-Type:application/json' -X PUT -d '{"value": "\"foo\": \"bar\""}' http://localhost:9000/vortex/MyTopic
```

## Example of Creating a Topic
```
curl  -X PUT  http://localhost:9000/vortex/topic/MyTopic\?type\=vortex.JSONTopicType\&durability\=TRANSIENT\&history\=\{\"k\"\=\"KEEP_ALL\"\}
```

***Now to the actual API; feedback as always is welcome.***

## A ReSTful API for Vortex
### /vortex/topic/{topic}
#### PUT
Create a new topic based on the provided information.
##### Parameters
| Name | Type | Located In | Required | Description |
|------|------|------------|----------|-------------|
| topic | String | path | yes | The name of the topic subscribed to by the reader. |
| type | String | query | yes | The qualified name of the topics type. It is necessary that the server is able to find the type for the discovered topic. |
| domain | int| query | no | The domain id to use. If not provided the default domain id will be used. |
| durability | String | query | no | The durability QoS setting VOLATILE, TRANSIENT, PERSISTENT |
| reliability | String | query | no | The reliability QoS setting RELIABLE or BEST_EFFORT |
| history | String | query | no | The history QoS setting in a JSON string. {"k": "[KEEP_ALL|KEEP_LAST]","v":[int value for history depth]} The "v" field is only required with KEEP_LAST and even then it is optional. |
##### Responses
| Code | Description |
|------|-------------|
| 200 | The topic was created successfully. |
| 404 | If the ***type*** for the topic could not be found. If the ***topic*** already exists but with a different ***type***. |

### /vortex/{topic}
#### PUT
Write the provided JSON encoded samples using a specific writer.
##### Parameters
| Name | Type | Located in | Required | Description |
|------|------|------------|----------|-------------|
| topic | String | path | yes | The name of the topic to write to. The body of is a JSON array of JSON encoded samples to be written to Vortex |
##### Responses
| Code | Description |
|------|-------------|
| 200 | The sample(s) were successfully written. |
| 404 | If the topic could not be found. If the JSON encoded samples could not be mapped into samples that can be written to Vortex |

### /vortex/{topic}
#### GET
Read the available samples from the {topic}.
##### Parameters
| Name | Type | Located in | Required | Description |
|------|------|------------|----------|-------------|
| topic | String | path | yes | The name of the topic to read from.|
##### Responses
| Code | Description |
|------|-------------|
| 200 | The sample(s) were successfully read and contained in the body. |
| 404 | If the topic could not be found. If the JSON encoded samples could not be mapped into JSON so that can be written to the body of the response. |
