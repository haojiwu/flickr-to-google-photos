import requests
import json
import argparse

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

# main()
parser = argparse.ArgumentParser()
parser.add_argument("--host", help='Host URL, like "https://localhost:8443" or "https://my-flickr-to-google-photos.onrender.com"', required=True)
parser.add_argument("--flickr-token", help='Flickr token from https://[HOST]/auth/flickr/complete, like "12345678901234567-1234abc5d6e7890f"', required=True)
parser.add_argument("--flickr-secret", help='Flickr secret from https://[HOST]/auth/flickr/complete, like "1fa234b56c78de90"', required=True)
parser.add_argument("--flickr-user-id", help='Flickr user id from https://[HOST]/auth/flickr/complete, like "12345678@N00"', required=True)
parser.add_argument("--google-refresh-token", help='Google refreshToken from https://[HOST]/auth/google/complete, like ' +
                                                   '"1//23-4a5BCD6Ef7GhIJKLMNOPQRStU-V8Wx9y0zaBCd12Efg3HiJKlMnoPQ_rStU4vWx1YZabc5DefgH6iJk7LmNOPQr8stUvwxyza"', required=True)

args = parser.parse_args()

host = args.host
flickr_token = args.flickr_token
flickr_secret = args.flickr_secret
flickr_user_id = args.flickr_user_id
google_refresh_token = args.google_refresh_token

album_page = 1 # set to 1 to star from beginning, or the to other to resume from other page
success_google_album_count = 0
while True:
    response = get_flickr_albums(album_page)
    #print(json.dumps(response, indent=2))
    print('album_page: {}, hasNext: {}'.format(album_page, response['hasNext']))
    flickr_albums = response['flickrAlbums']
    for flickr_album in flickr_albums:
        print('google album num: {}'.format(success_google_album_count))
        print(json.dumps(flickr_album, indent=2))
        create_response = create_google_album(flickr_album)
        print(json.dumps(create_response, indent=2))
        success_google_album_count = success_google_album_count + 1

    if (response['hasNext'] == False): # should be False. Set to True if you only want to try first page
        break
    else:
        album_page = album_page + 1
print('success create {} google albums'.format(success_google_album_count))

