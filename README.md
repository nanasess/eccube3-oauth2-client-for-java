EC-CUBE3 OAuth2.0 Client for Java
===================================

設定方法
----------

```
authorization.properties を以下のように設定してください
## Client ID
CLIENT_ID=<Client ID>
## Client secret
CLIENT_SECRET=<Client secret>
## Port in the "Callback URL".
PORT=8080
## Domain name in the "Callback URL".
DOMAIN=127.0.0.1
## Authorization Endpoint
AUTHORIZATION_ENDPOINT=https://<ec-cube-host>/admin/OAuth2/v0/authorize
## Token Endpoint
TOKEN_ENDPOINT=https://<ec-cube-host>/OAuth2/v0/token
## Resource
RESOURCE=https://<ec-cube-host>/api/v0/productsauthsample/1
```

`redirect_uri` は **http://127.0.0.1:8080/Callback** を指定してください

実行方法
-----------

```
mvn compile exec:java
```
