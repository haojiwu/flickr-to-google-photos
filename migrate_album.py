import requests
import json

host = "https://localhost:8443"
flickr_token = "my flickr token from https://localhost:8443/auth/flickr/complete"
flickr_secret = "my flickr secret from https://localhost:8443/auth/flickr/complete"
flickr_user_id = "my flickr user id from https://localhost:8443/auth/flickr/complete"
google_refresh_token = "my google refreshToken from https://localhost:8443/auth/google/complete"

def get_flickr_albums(page):
    url = host + "/flickr/album"
    params = {'token': flickr_token, 'secret': flickr_secret, 'page': page, 'userId': flickr_user_id}
    r = requests.get(url=url, params=params, verify=False)
    return r.json()

def create_google_album(flickr_album):
    url = host + "/google/album"
    params = {'refreshToken': google_refresh_token}
    r = requests.post(url=url, json=flickr_album, params=params, verify=False)
    return r.json()

album_page = 1 # set to 1 to star from beginning, or the to other to resume from other page
google_album_num = 0
while True:
    response = get_flickr_albums(album_page)
    #print(json.dumps(response, indent=2))
    print('album_page: {}, hasNext: {}'.format(album_page, response['hasNext']))
    flickr_albums = response['flickrAlbums']
    for flickr_album in flickr_albums:
        print('google album num: {}'.format(google_album_num))
        print(json.dumps(flickr_album, indent=2))
        create_response = create_google_album(flickr_album)
        print(json.dumps(create_response, indent=2))
        google_album_num = google_album_num + 1

    if (response['hasNext'] == False): # should be False. Set to True if you only want to try first page
        break
    else:
        album_page = album_page + 1

