# Spring Security による REST API の保護

Camel for Spring Boot のアーキタイプで生成した pom.xml [^1] をベースに、
Spring SecurityでRESTエンドポイントを保護する最小限の例を示す。

従来使用していたKeycloakプロジェクト提供の以下のコンポーネントはdeprecatedとなった。

```xml
    <dependency>
      <groupId>org.keycloak</groupId>
      <artifactId>keycloak-spring-boot-starter</artifactId>
    </dependency>
```

新バージョンのKeylocakでは各言語・フレームワークが提供するOAuthやOpenID Connect (OIDC)のライブラリの使用が推奨となり、
Sprinb Bootにおいては以下のコンポーネントを使用する（内部でSpring Securityを使っている）。

```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
```

保護対象は任意のREST APIを持つサーバーで、バックエンドで動くタイプのもの（いわゆるリソースサーバー）を想定している。
リクエストのヘッダに適切なアクセストークンが載っていなければならず、不適切なものであれば400番台のレスポンスを返す（ログイン画面にリダイレクト（300番台のレスポンス）したりはしない）。
KeycloakのアクセストークンはJWT形式なのでここでもそれを想定する。

## ファイルの説明

- [MySpringBootApplication.java](./src/main/java/com/redhat/MySpringBootApplication.java)
  - mainメソッド
- [ExampleResource.java](./src/main/java/com/redhat/ExampleResource.java)
  - 以下の三つのエンドポイントを持つREST API
    - /public (認証なしでアクセス可能)
    - /protected (認証が必要)
    - /protected/admin (認証が必要、かつ認証したユーザにadminロールも必要)
- [ExampleSecurityConfig.java](./src/main/java/com/redhat/ExampleSecurityConfig.java)
  - エンドポイントを上記の通りに保護する設定を行う
  - KeycloakのロールをSpring SecurityのAuthorityという概念にマップする追加設定を行う
- [application.properties](./src/main/resources/application.properties)
  - KeycloakサーバのURLを設定する

エンドポイントを保護する設定は、リソースサーバーの場合はapplication.propertiesには書けず
Javaのコードで以下の様に記述する必要がある。

```java
            .requestMatchers(new AntPathRequestMatcher("/protected/admin")).hasRole("admin")
            .requestMatchers(new AntPathRequestMatcher("/protected*")).authenticated()
            .anyRequest().permitAll())
```

公式ドキュメント:
- https://docs.spring.io/spring-security/reference/6.2/servlet/oauth2/resource-server/index.html

### ロールについて

ロールの使い方も示すために上記の通り「"/protected/admin" にアクセスするには "admin" ロールが必要」という
シナリオを用意している。
しかしロールをそもそも使用しない（認証のみでよい、またはフレームワークの流儀には従わずに自分で必要に応じて
トークンから情報を取得する）のであればこの部分は削除してよい。

認証だけでなく認可も行う場合は、一般にアクセストークン（以下単にトークンと言ったらアクセストークンのこととする）
内のスコープ (scope) やグループ、ロールといったクレイムを参照することになる。
Spring SecurityではそれらをAuthorityという概念で表すことになるが、デフォルトではスコープしか見ない。
一方Keycloakではロールという概念を多用しており、ロールも見るようにSpring Securityを設定するには、
自分でトークン内のロール情報を拾ってAuthorityという概念にマップする必要がある。

それを行っているのが `ExampleSecurity#realmRole2AuthorityConverter()`　メソッドである。
Keycloakはトークン内の realm_access というクレイムにログインしたユーザが持っているロールの配列を設定するので
それを `SimpleGrantedAuthority` というオブジェクトにマップしてフレームワーク内で利用可能にしている。

なお、Keycloakではロールにレルムレベルとクライアントレベルの二種類がある。
上記の realm_access クレイムはレルムレベルのロールであり、クライアントロールをマップするには別のクレイムをパースする必要がある。

また、デフォルトではログイン対象のクライアントのクライアントロールのみがトークン内に格納され、
ログインしたユーザが多くのロールを持っている場合でもトークンサイズが大きくなりすぎないようにしている。
一方で、ログイン対象に関係なくログインしたユーザが持つ全てのロールが欲しい場合もある。
そのためにはログイン対象クライアントの "Full scope allowed" という設定を ”ON" にする。

Spring Securityでロールを使う場合の参考リンク:
- https://kazuhira-r.hatenablog.com/entry/2022/09/08/015028
- https://stackoverflow.com/questions/56214991/enable-role-authentication-with-spring-boot-security-and-keycloak

## 使い方

### Keycoakの設定

Keycloak Legacy (もしくはRH-SSO)　新しいQuarkusベースのKeycloak (もしくはRHBK) をインストールし
ローカルで起動する。

```shell
# すでにインストールしたKeycloakのディレクトリ直下にcd済みとする。
# Legacy版の場合
$ bin/standalone.sh
# Quarkus版の場合 (URLをLegacy版と合わせるためにオプションを指定している)
$ bin/kc.sh start-dev --http-relative-path=/auth
```

1. レルムを作成する

   名前を "test-realm" とする。

2. 作成したレルム内に（以下同様）ユーザーを作成、パスワードも設定する

   ここでは "testuser01/Testuser01!", "testuser02/Testuser02!" の二つを作成する。
   Quarkus版Keycloakの場合はEmail, First name, Last nameも設定しておかないと初回ログイン時のRequired Actionで止まってしまうのでこれらも設定しておく。

3. レルムロール "admin" を作成し、一人のユーザに付与する

   ここでは testuser01 にのみ admin ロールを付与する。

4. admin-cli に "Full scope allowed: ON" を設定する

   管理コンソールの設定箇所がバージョンにより異なるので注意する。 \
　　Quraks版: "Clients > admin-cli > Client scopes > admin-cli-dedicated > Scope" \
　　Legacy版: "Clients > admin-cli > Scope"

JWTでは署名の検証で充分なので、このSpring Booアプリのクライアント登録は必要ない。

フロントエンドアプリの代替としてデフォルトで存在する admin-cli を用いる。
このクライアントを対象にログインした際に全てのロールが付くように "Full scope allowed: ON" が必要になる。

### Spring Bootの設定

1. application.properties にKeycloakのURLを設定する
   ```
   spring.security.oauth2.resourceserver.jwt.issuer-uri = http://localhost:8080/auth/realms/test-realm
   ```
   ここで設定したURLに ".well-known/openid-configuration" を付けた所にSpring Securityがアクセスし必要な情報を取得・自動設定している: http://localhost:8080/auth/realms/test-realm/.well-known/openid-configuration
   
2. ビルドおよび起動する
   ```shell
   mvn clean spring-boot:run -Dspring-boot.run.arguments="--server.port=8180"
   ```
   Keycloakが8080番のポートを使っているので8180番に変えている。

### curlコマンドによるテスト

1. /public にアクセスしてみる
   ```shell
   # このパスは保護されていないのでトークンなしでアクセスできる。
   $ curl -sv http://localhost:8180/public | jq .
   ...
   < HTTP/1.1 200 OK
   ...
   {
    "message": "public area"
   }
   ```
2. /protected にアクセスしてみる
   ```shell
   # トークンなしでは401エラーになる。
   $ curl -sv http://localhost:8180/protected | jq .
   ...
   < HTTP/1.1 401 Unauthorized
   ...
   # testuser01 でトークンを取得しそれを使ってアクセスする。
   $ TOKEN=$( curl -s -d "client_id=admin-cli" -d "username=testuser01" -d "password=Testuser01!" -d "grant_type=password" "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/token" | jq -r .access_token )
   $ curl -sv -H "Authorization: bearer ${TOKEN}" http://localhost:8180/protected | jq .
   ...
   < HTTP/1.1 200 OK
   ...
   {
     "name": "60592faf-98ca-4534-bd18-aed03adab62d",
     "preferred_username": "testuser01",
     "message": "protected area",
     "email": "testuser01@example.com"
   }
   ```
3. /protected/admin にアクセスしてみる
   ```shell
   # testuser01 は admin ロールを持っているのでアクセスできる。
   $ curl -sv -H "Authorization: bearer ${TOKEN}" http://localhost:8180/protected/admin | jq .
   ...
   < HTTP/1.1 200 OK
   ...
   {
     "name": "60592faf-98ca-4534-bd18-aed03adab62d",
     "preferred_username": "testuser01",
     "message": "protected area with admin role",
     "email": "testuser01@example.com"
   }
   ```
4. adminロールなしで /protected/admin にアクセスしてみる
   ```shell
   # testuser02 でログインしなおす。
   $ TOKEN=$( curl -s -d "client_id=admin-cli" -d "username=testuser02" -d "password=Testuser02!" -d "grant_type=password" "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/token" | jq -r .access_token )
   # testuser02 は admin ロールを持っていないのでアクセスできない（/protected ならアクセスできる）。
   $ curl -sv -H "Authorization: bearer ${TOKEN}" http://localhost:8180/protected/admin | jq 
   ...
   < HTTP/1.1 403 Forbidden
   ...
   ```



[^1]: https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.4/html/getting_started_with_red_hat_build_of_apache_camel_for_spring_boot/getting-started-with-camel-spring-boot_csb#generating-a-csb-application-using-maven
