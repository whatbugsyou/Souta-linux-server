# Souta-linux-server
> a web-server based on SpringBoot,provides interface for java background to monitor and operate Line,including create ,delete ,check ,self monitoring,etc
## Exposed Interfaces
### Create a Line
```json
{
    "URL":"/v1.0/line/notify",
    "port":"18080",
    "method":"post",
    "param":{
    },
    "return":{
    }
}
```

### Refresh a Line

```json
{
    "URL":"/v1.0/line/notify",
    "port":"18080",
    "method":"put",
    "param":{
     "lineId":"01" 
    },
    "return":{
      "status":"ok|not exist"
    }
}
```

### Check Line Status

```json
{
    "URL":"/v1.0/line/notify",
    "port":"18080",
    "method":"get",
    "param":{
        "lineId":"01" 
    },
    "return":{
        "status":"ok|not exist",
        "data":{
            "socket5":"on", 
            "shadowsocks":"off"
        }
    }
}
```

### Delete a Line

```json
{
    "URL":"/v1.0/line/notify",
    "method":"delete",
    "port":"18080",
    "param":{
        "lineId":"01"
    },
    "return":{
        "status":"ok" 
    }
}
```

### Operate Line Protocol

```json
{
    "URL":"/v1.0/line/notify/proto",
    "port":"18080",
    "method":"get",
    "param":{
        "protoId":"socket5|shadowsocks",
        "lineId":"01",  
        "action":"on|off"
    },
    "return":{
        "status":"ok|not exits"
    }
}
```
### change line adsl
```json
{
    "URL":"/v1.0/line/adsl",
    "port":"18080",
    "method":"put",
    "param": {
        "lineId": 1    
    },
  "body": {
    "adslUser": "xxx",
    "adslPassword": "xxx",
    "ethernetName": "xxx"
    //nullable and inherit the origin ethernetName
  },
  "return": {
    true
    |
    false
  }
}
```

### change host rate limit

```json
{
  "URL": "/v1.0/host/rateLimit/{rateLimitKB}",
  "port": "18080",
  "method": "put",
  "return": {
  }
}
```

## CallBack Function

### Return a Line

After calling `Create a Line` or `Refresh a Line` and the Line is OK,it will be sent it to java server with the
following data format :

```json
{
  "requestName": "/v1.0/line",
  "port": "8088",
  "method": "put",
  "data": {
    "hostId":"01",
        "lines":[{
            "id":"01",
            "socks5":{
                "ip":"123456",
                "port":"7777",
                "username":"testsocks",
                "password":"testsocks"
            },
            "ss":{
                "ip":"654321",
                "port":"6666",
                "password":"testss",
                "encryption":"rc4-md5" 
            } 
        }]
    }
}
```