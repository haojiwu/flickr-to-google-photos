# Flickr to Google Photos
Tool for photo and album migration from Flickr to Google Photos.

## Description
This project implements following features to achieve migration from Flickr to Google Photos.
1. Flickr and Google oauth request builder and callback endpoint.
2. RESTFul APIs to download all Flickr photos and upload to Google Photos, with following Flickr photo metadata:
   - Title
   - Description
   - Tags
   - (above 3 metadata will be used to compose description in Google Photos.)
   - Geotagging (add geotagging to photo's EXIF if user has manually added location in Flickr.)
3. RESTFul APIs to get all Flickr albums, including album-photo assocations and album metadata, and create corresponding album in Google Photos with following Flickr album metadata:
   - Title
   - Description (as text enrichment to the beginning of Google Photos album.)
   - Cover photo
4. Optinally force create unique photo in Google Photos by appending photo's EXIF.
5. Support vidoe migration with limintation.

## Build with
- Java 8
- [Spring Boot](https://spring.io/projects/spring-boot)
- Maven
- [H2 Database](https://www.h2database.com) (Optional)
- [Flickr4Java](https://github.com/boncey/Flickr4Java)
- [google-photos-library-client](https://github.com/google/java-photoslibrary) [guide](https://developers.google.com/photos/library/guides/get-started-java#get-library)
- [Apache Commons Imaging](https://github.com/apache/commons-imaging)

There are also python scripts to demo sending request to migrate all photos and albums.

## Prerequisites
- JDK 1.8+ and Maven 3.3+ installed
- Your own Flickr app credential
  1. Create app with any name in https://www.flickr.com/services/apps/create/noncommercial. After submitting, you will get **key** and **secret**.
  2. Click **Edit auth flow for this app** and update **Callback URL** with `https://localhost:8443/flickr/auth/complete`. 
- Your own Google App credential
  1. Create or config your Google project with Google Photos Library API. You can find more detailed instructions in https://developers.google.com/photos/library/guides/get-started.
  2. Update **Authorized redirect URI** with `https://localhost:8443/google/auth/complete`.
- Generate a self-signed SSL certificate (if you don't have real SSL Certificate).  
  Flickr oauth callback requires HTTPS URL. To enable HTTPS in Spring Boot we need SSL certificate. If you don't have one, you can generate self-signed SSL certificate. Of course it can only be used in local deployment.
  
  ```
  keytool -genkeypair -alias my_dev -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore my_dev.p12 -validity 3650
  ```
  
  This command will ask you input password (`my_password` as my example) and some other information (you can skip all of them except password) and generate `my_dev.p12`, which is your keystore file. This keystore file and password will be used when configuring project's application properties. You can find more informatin in https://www.baeldung.com/spring-boot-https-self-signed-certificate.
 

## Setup
1. Clone this project to local. Go to project folder.
```
git clone git@github.com:haojiwu/flickr-to-google-photos.git
cd flickr-to-google-photos
```
2. Create a folder (`/Users/dev/photos_from_flickr` as example) to temporarily store Flickr photo files in the local. Flickr photos will be downloaded to this folder and then upload to Google Photos. Although this is temporary storage, files in this folder will NOT be cleaned when migration completed or application shutdown. You can decide manually clean them or backup them to some other storage.
```
mkdir /Users/dev/photos_from_flickr
```
3. Copy keystore (`my_dev.p12` in previous example) to project folder.
4. Configure project's application properties in `src/main/resources/application-dev.properties`.
   ``` 
   # you can use any editor or IDE to edit this file
   vim src/main/resources/application-dev.properties
   ```   
   - Add SSL properties.
   ```
   server.ssl.key-store=my_dev.p12 # make sure you put it in the root of project folder
   server.ssl.key-store-password=my_password  # the value you entered when keytool asked 
   server.ssl.key-alias=my_dev
   ```
   - Add Flickr key and secret, which are from Flickr App you created.
   ```
   app.flickr.key=a1b234567b89c012d3e4f5ab67c8901d
   app.flickr.secret=12ab345c6d789e0f
   ```
   - Add Google client id and client secret, which are from Google project you created with Google Photos Library enabled. [Here](https://developers.google.com/photos/library/guides/get-started) has detailed steps to find them.
   ```
   app.google.clientId=123456789012-abcd3ef45g6h70ijkl89m0n1o2pqrstu.apps.googleusercontent.com
   app.google.clientSecret=12Ab3CDe4FGhIjklMnopQRST
   ```
   - Add photo folder. Put the path from step 2.
   ```
   app.photoFolder=/Users/dev/photos_from_flickr
   ```
5. In the project folder launch application.
```
./mvnw spring-boot:run
```

## Usage
### Get Flickr credential
1. Open browser to visit `https://localhost:8443/flickr/auth`.
2. Browser will be redirected to Flickr authorization page.
3. After you accept it, browser will be redirected back to `https://localhost:8443/flickr/auth/complete` and returns `FlickrCredential` which contains `userId`, `token` and `secret`.
```
{
    "userId": "12345678@N00",
    "token": "12345678901234567-1234abc5d6e7890f",
    "secret": "1fa234b56c78de90"
}
```
### Get Google credential
1. Open browser to visit `https://localhost:8443/google/auth`.
2. Browser will be redirected to Google authorization page. There may be warning like `This app isn't verified`. Ignore it by cliecking `Advanced` and `Go to` your app.
3. After you allow authorization with permissions, browser will be redirected back to `https://localhost:8443/google/auth/complete` and returns `GoogleCredential` which contains `accessToken` and `refreshToken`. We only need `refreshToken`.
```
{
    "accessToken": "ya12.a3AfH4SMCPonW5F6VHAH7L_oGsb0NwTgDCQQElPrG-8H90flJatx1RELxHPf12ydBKSwi-WH34mHh56jJFU7z89bayrvogNX-Z0PEdmM1gLMQWGfLW23yqbCStvsYp4gcJ5n6cox_nVc7rfGan8SfRiSwtqhg9Kik0Szo",
    "refreshToken": "1//23-4a5BCD6Ef7GhIJKLMNOPQRStU-V8Wx9y0zaBCd12Efg3HiJKlMnoPQ_rStU4vWx1YZabc5DefgH6iJk7LmNOPQr8stUvwxyza"
}
```
### Get Flickr photos metadata with downloadable URL
- Endpoint: `https://localhost:8443/flickr/photo`
- Parameters:
  - `token`: Flickr token from Flickr Credential.
  - `secret`: Flickr secret from Flickr Credential.
  - `page`: page number starting from **1** (it is not programmer friendly, but follows Flickr API's design). Each page has 500 photos.
```bash
# token: 12345678901234567-1234abc5d6e7890f
# secret: 1fa234b56c78de90
# page: 1
# add "-k" to ignore SSL verification 
curl -k "https://localhost:8443/flickr/photo?token=12345678901234567-1234abc5d6e7890f&secret=1fa234b56c78de90&page=1"
{
    "flickrPhotos": [ # Array of Flickr photos. If this is not last page, the array will have 500 elements.
        {
            "id": "12345678901", # Flickr photo id
            "url": "https://flickr.com/photos/12345678@N00/12345678901", # Flickr URL of this photo
            "downloadUrl": "https://farm66.staticflickr.com/65535/12345678901_af1ccf9d71_o.jpg", # URL to download this photo
            "title": "My photo title", # Flickr photo title
            "description": "My photo description", # Flickr photo description
            "latitude": 40.707294, # GPS latitude of Flickr photo geotagging 
            "longitude": -74.01037, # GPS longitude of Flickr photo geotagging 
            "tags": [ # Array of Flickr tag string 
                "tag1",
                "tag2"
            ],
            "media": "PHOTO" # PHOTO or VIDEO (both are uppercase)
        },
        ...
    ],
    "total": 13110, # Number of total photos in Flickr
    "page": 1, # Page number
    "pageSize": 500, # Page size
    "hasNext": true # Boolean Flag to tell if this is last page, or user need to send another request with next page number.
}        
```
### Create photos in Google Photos
### Get Flickr albums metadata with photo associations
### Create album in Google Photos
### Scripts to migration all photos and albums
