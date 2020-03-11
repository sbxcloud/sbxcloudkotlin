# sbxcloudkotlin
kotlin library

## Implementattion:
    implementation 'com.github.sbxcloud:sbxcloudkotlin:v0.3.1'
    
## Usage

### Initialization:
```java
  sbxCoreService =SbxCore(applicationContext, "ibuyflowers").apply {
            initialize(domain,
                    url,
                     appKey)
        }
 ```
 ### Register:
   ```java
signUpRx(login: String, email: String, name: String, password: String)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    ....
   ```
  ### Login:
  ```java
 sbxCoreService.loginRx(login: String, password: String)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    ....
   ```
  ### Find:
  ```java
 sbxCoreService.find(model_name: String)
                    .andWhereIsEqual(field_name: String, value: Any)
                    .thenRx()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    ....
   ```
